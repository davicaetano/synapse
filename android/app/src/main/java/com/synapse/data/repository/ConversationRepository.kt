package com.synapse.data.repository

import android.util.Log
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        
        // Listen to conversations (only once!)
        val conversationsFlow = conversationDataSource.listenConversations(userId)
        
        // Get member IDs flow from conversations
        val memberIdsFlow = conversationsFlow
            .map { conversations ->
                val allMemberIds = conversations
                    .flatMap { it.memberIds }
                    .distinct()
                    .sorted() // Sort to make comparison stable
                
                
                allMemberIds
            }
            .distinctUntilChanged() // Only trigger flatMapLatest when member IDs actually change!
        
        // Listen to users ONLY when member IDs change (not on every presence update!)
        val usersFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                if (memberIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    userDataSource.listenUsersByIds(memberIds)
                }
            }
        
        // Listen to presence ONLY when member IDs change (not on every presence update!)
        val presenceFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                if (memberIds.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    presenceDataSource.listenMultiplePresence(memberIds)
                }
            }
        
        // Combine conversations + users + presence
        return combine(
            conversationsFlow,
            usersFlow,
            presenceFlow
        ) { conversations, userEntities, presenceMap ->
            
            if (conversations.isEmpty()) {
                emptyList()
            } else {
                // Build user map
                val usersMap = userEntities.associateBy { it.id }
                
                // Transform entities to domain models
                val summaries = conversations.map { convEntity ->
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
                
                summaries
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
        
        // Send message (may return null if offline, but Firestore caches it)
        val messageId = messageDataSource.sendMessage(conversationId, text)
        
        // ALWAYS update conversation metadata, even if messageId is null
        // This ensures the inbox shows the latest message immediately,
        // even when offline (Firestore will sync when back online)
        val timestamp = System.currentTimeMillis()
        conversationDataSource.updateConversationMetadata(
            conversationId = conversationId,
            lastMessageText = text,
            timestamp = timestamp
        )
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

