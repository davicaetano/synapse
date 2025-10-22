package com.synapse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.source.firestore.FirestoreConversationDataSource
import com.synapse.data.source.firestore.FirestoreMessageDataSource
import com.synapse.data.source.firestore.FirestoreUserDataSource
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for conversation operations.
 * 
 * RESPONSIBILITY: Business logic - combining multiple DataSources.
 * - Combines: Conversation + Message + User + Presence
 * - Transforms entities to domain models
 * - Coordinates multi-step operations (send message + update metadata)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDataSource: FirestoreConversationDataSource,
    private val messageDataSource: FirestoreMessageDataSource,
    private val userDataSource: FirestoreUserDataSource,
    private val presenceDataSource: RealtimePresenceDataSource,
    private val auth: FirebaseAuth
) {
    
    // ============================================================
    // READ OPERATIONS (with business logic)
    // ============================================================
    
    /**
     * Observe all conversations for a user with full data (members + presence).
     */
    fun observeConversationsWithUsers(userId: String): Flow<List<ConversationSummary>> {
        return conversationDataSource.listenConversations(userId)
            .flatMapLatest { conversations ->
                if (conversations.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // Collect all unique member IDs from all conversations
                    val allMemberIds = conversations
                        .flatMap { it.memberIds }
                        .distinct()
                    
                    // Combine conversations + users + presence
                    combine(
                        flowOf(conversations),
                        userDataSource.listenUsersByIds(allMemberIds),
                        presenceDataSource.listenMultiplePresence(allMemberIds)
                    ) { convEntities, userEntities, presenceMap ->
                        // Build user map
                        val usersMap = userEntities.associateBy { it.id }
                        
                        // Transform entities to domain models
                        convEntities.map { convEntity ->
                            val members = convEntity.memberIds.mapNotNull { memberId ->
                                val userEntity = usersMap[memberId]
                                val presence = presenceMap[memberId]
                                userEntity?.toDomain(
                                    presence = presence,
                                    isMyself = (memberId == userId)
                                )
                            }
                            
                            convEntity.toDomain(members = members)
                        }
                    }
                }
            }
    }
    
    /**
     * Observe conversations filtered by type with full data.
     */
    fun observeConversationsByTypeWithUsers(
        userId: String,
        convType: ConversationType
    ): Flow<List<ConversationSummary>> {
        return conversationDataSource.listenConversationsByType(userId, convType)
            .flatMapLatest { conversations ->
                if (conversations.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val allMemberIds = conversations
                        .flatMap { it.memberIds }
                        .distinct()
                    
                    combine(
                        flowOf(conversations),
                        userDataSource.listenUsersByIds(allMemberIds),
                        presenceDataSource.listenMultiplePresence(allMemberIds)
                    ) { convEntities, userEntities, presenceMap ->
                        val usersMap = userEntities.associateBy { it.id }
                        
                        convEntities.map { convEntity ->
                            val members = convEntity.memberIds.mapNotNull { memberId ->
                                val userEntity = usersMap[memberId]
                                val presence = presenceMap[memberId]
                                userEntity?.toDomain(
                                    presence = presence,
                                    isMyself = (memberId == userId)
                                )
                            }
                            
                            convEntity.toDomain(members = members)
                        }
                    }
                }
            }
    }
    
    /**
     * Observe a single conversation with all messages.
     */
    fun observeConversationWithMessages(conversationId: String): Flow<Conversation> {
        val userId = auth.currentUser?.uid
        
        return combine(
            conversationDataSource.listenConversations(userId ?: ""),
            messageDataSource.listenMessages(conversationId)
        ) { conversations, messageEntities ->
            val convEntity = conversations.find { it.id == conversationId }
            
            if (convEntity == null) {
                // Return empty conversation
                Conversation(
                    summary = ConversationSummary(
                        id = conversationId,
                        lastMessageText = null,
                        updatedAtMs = 0L,
                        members = emptyList(),
                        convType = ConversationType.DIRECT
                    ),
                    messages = emptyList()
                )
            } else {
                // We need users and presence for the conversation
                convEntity
            }
        }.flatMapLatest { convEntity ->
            if (convEntity is Conversation) {
                flowOf(convEntity) // Already a Conversation (empty case)
            } else {
                // Cast back to entity
                val conv = convEntity as com.synapse.data.source.firestore.entity.ConversationEntity
                val allMemberIds = conv.memberIds
                
                combine(
                    flowOf(conv),
                    messageDataSource.listenMessages(conversationId),
                    userDataSource.listenUsersByIds(allMemberIds),
                    presenceDataSource.listenMultiplePresence(allMemberIds)
                ) { convEnt, msgEntities, userEntities, presenceMap ->
                    val usersMap = userEntities.associateBy { it.id }
                    
                    val members = convEnt.memberIds.mapNotNull { memberId ->
                        val userEntity = usersMap[memberId]
                        val presence = presenceMap[memberId]
                        userEntity?.toDomain(
                            presence = presence,
                            isMyself = (memberId == userId)
                        )
                    }
                    
                    val messages = msgEntities.map { msgEntity ->
                        msgEntity.toDomain(
                            currentUserId = userId,
                            memberCount = members.size
                        )
                    }
                    
                    Conversation(
                        summary = convEnt.toDomain(members = members),
                        messages = messages
                    )
                }
            }
        }
    }
    
    // ============================================================
    // WRITE OPERATIONS (coordinating multiple DataSources)
    // ============================================================
    
    /**
     * Send a message to a conversation.
     * Coordinates: create message + update conversation metadata.
     */
    suspend fun sendMessage(conversationId: String, text: String) {
        val messageId = messageDataSource.sendMessage(conversationId, text)
        
        if (messageId != null) {
            // Update conversation's lastMessage and timestamp
            conversationDataSource.updateConversationMetadata(
                conversationId = conversationId,
                lastMessageText = text,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Get or create a direct conversation with another user.
     * Returns conversation ID.
     */
    suspend fun getOrCreateDirectConversation(otherUserId: String): String? {
        val myId = auth.currentUser?.uid ?: return null
        val userIds = listOf(myId, otherUserId).sorted()
        
        return conversationDataSource.createDirectConversation(userIds)
    }
    
    /**
     * Create a self conversation (user talking to themselves / AI).
     */
    suspend fun createSelfConversation(): String? {
        val userId = auth.currentUser?.uid ?: return null
        return conversationDataSource.createSelfConversation(userId)
    }
    
    /**
     * Create a group conversation.
     * Current user is automatically set as the admin/creator.
     * 
     * @param memberIds List of user IDs to add to group (can be empty to create group with just yourself)
     * @param groupName Optional group name
     */
    suspend fun createGroupConversation(memberIds: List<String>, groupName: String? = null): String? {
        val currentUserId = auth.currentUser?.uid ?: return null
        
        // Always include current user in the group
        val allMemberIds = (memberIds + currentUserId).distinct()
        
        return conversationDataSource.createGroupConversation(
            memberIds = allMemberIds,
            groupName = groupName,
            createdBy = currentUserId  // Current user is the admin
        )
    }
    
    /**
     * Add a user to a group conversation.
     */
    suspend fun addUserToGroup(conversationId: String, userId: String) {
        conversationDataSource.addMemberToGroup(conversationId, userId)
    }
    
    /**
     * Remove a user from a group conversation.
     */
    suspend fun removeUserFromGroup(conversationId: String, userId: String) {
        conversationDataSource.removeMemberFromGroup(conversationId, userId)
    }
    
    /**
     * Mark specific messages as read.
     */
    suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>) {
        messageDataSource.markMessagesAsRead(conversationId, messageIds)
    }
    
    /**
     * Mark all messages in a conversation as read.
     */
    suspend fun markConversationAsRead(conversationId: String) {
        messageDataSource.markAllMessagesAsRead(conversationId)
    }
    
    /**
     * Get unread message count for a conversation.
     */
    suspend fun getUnreadMessageCount(conversationId: String): Int {
        return messageDataSource.getUnreadMessageCount(conversationId)
    }
    
    /**
     * Mark the last message in a conversation as received.
     * Optimization: if last message is received, all previous ones are too.
     */
    suspend fun markLastMessageAsReceived(conversationId: String) {
        messageDataSource.markLastMessageAsReceived(conversationId)
    }
}

