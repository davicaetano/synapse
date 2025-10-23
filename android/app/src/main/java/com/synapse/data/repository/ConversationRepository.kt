package com.synapse.data.repository

import androidx.paging.PagingData
import com.google.firebase.auth.FirebaseAuth
import com.synapse.BuildConfig
import com.synapse.data.source.IMessageDataSource
import com.synapse.data.source.firestore.FirestoreConversationDataSource
import com.synapse.data.source.firestore.FirestoreMessageDataSource
import com.synapse.data.source.firestore.FirestoreUserDataSource
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.MessageEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import com.synapse.data.source.realtime.entity.PresenceEntity
import com.synapse.data.source.room.RoomMessageDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for conversation operations.
 *
 * RESPONSIBILITY: Simple data access - expose flows from DataSources.
 * - NO complex transformations or nested flatMapLatest
 * - NO combining multiple flows (ViewModel does that)
 * - Only coordinates write operations (send message + update metadata)
 *
 * BRANCH BY ABSTRACTION:
 * - Uses RoomMessageDataSource when BuildConfig.USE_ROOM_MESSAGES = true
 * - Falls back to FirestoreMessageDataSource when false
 * - Allows safe testing of Room implementation without breaking existing code
 *
 * Philosophy: Keep it simple. Repository exposes data, ViewModel processes it.
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDataSource: FirestoreConversationDataSource,
    private val firestoreMessageDataSource: FirestoreMessageDataSource,
    private val roomMessageDataSource: RoomMessageDataSource,
    private val userDataSource: FirestoreUserDataSource,
    private val presenceDataSource: RealtimePresenceDataSource,
    private val auth: FirebaseAuth
) {
    
    // Branch by Abstraction: Choose message data source based on feature flag
//    private val messageDataSource: IMessageDataSource = if (BuildConfig.USE_ROOM_MESSAGES) {
    private val messageDataSource: IMessageDataSource = if (true) {
        roomMessageDataSource
    } else {
        firestoreMessageDataSource
    }

    // ============================================================
    // READ OPERATIONS (simple flows, NO complex transformations)
    // ============================================================

    /**
     * Observe raw conversations for a user.
     * Returns ConversationEntity without users or presence data.
     * ViewModel should combine this with users and presence.
     */
    fun observeConversations(userId: String): Flow<List<ConversationEntity>> {
        return conversationDataSource.listenConversations(userId)
    }

    /**
     * Observe messages in a conversation.
     * Returns raw MessageEntity list.
     */
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDataSource.listenMessages(conversationId)
    }

    /**
     * Observe messages with pagination support (only available with Room).
     * Returns PagingData for efficient lazy loading.
     * 
     * Falls back to null if Room is not enabled.
     */
    fun observeMessagesPaged(conversationId: String): Flow<PagingData<MessageEntity>>? {
        return if (true && messageDataSource is RoomMessageDataSource) {
            (messageDataSource as RoomMessageDataSource).observeMessagesPaged(conversationId)
        } else {
            null  // Paging only available with Room
        }
    }

    /**
     * Observe users by IDs.
     * Simple passthrough - ViewModel manages when to call this.
     */
    fun observeUsers(userIds: List<String>): Flow<List<UserEntity>> {
        return if (userIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            userDataSource.listenUsersByIds(userIds)
        }
    }

    /**
     * Observe presence for multiple users.
     * Simple passthrough - ViewModel manages when to call this.
     */
    fun observePresence(userIds: List<String>): Flow<Map<String, PresenceEntity>> {
        return if (userIds.isEmpty()) {
            flowOf(emptyMap())
        } else {
            presenceDataSource.listenMultiplePresence(userIds)
        }
    }

    /**
     * Get a single conversation entity by ID.
     * Observes all user's conversations and filters by ID.
     * Returns null if not found.
     */
    fun observeConversation(userId: String, conversationId: String): Flow<ConversationEntity?> {
        return conversationDataSource.listenConversations(userId)
            .map { conversations -> conversations.find { it.id == conversationId } }
    }

    // ============================================================
    // WRITE OPERATIONS (coordinating multiple DataSources)
    // ============================================================

    /**
     * Send a message to a conversation.
     * Coordinates: create message + update conversation metadata.
     * 
     * @param conversationId The conversation ID
     * @param text The message text
     * @param memberIds List of all member IDs in the conversation (from ViewModel state)
     */
    suspend fun sendMessage(conversationId: String, text: String, memberIds: List<String>) {

        // Send message (may return null if offline, but Firestore caches it)
        messageDataSource.sendMessage(conversationId, text, memberIds)

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
     * Observe unread message counts across ALL conversations for a user.
     * Returns a Map of conversationId → unread count.
     * 
     * Uses a single Firestore collectionGroup query - MUCH more efficient!
     */
    fun observeAllUnreadCounts(userId: String): Flow<Map<String, Int>> {
        return messageDataSource.observeAllUnreadCounts(userId)
    }

    /**
     * Observe ALL unreceived messages across ALL conversations for the current user.
     * Returns a Map of conversationId → list of unreceived messageIds.
     * 
     * Uses a single Firestore collectionGroup query - MUCH more efficient!
     */
    fun observeAllUnreceivedMessages(userId: String): Flow<Map<String, List<String>>> {
        return messageDataSource.observeAllUnreceivedMessages(userId)
    }
    
    /**
     * Mark specific messages as received in a conversation.
     * 
     * @param conversationId The conversation ID
     * @param messageIds List of message IDs to mark as received
     */
    suspend fun markMessagesAsReceived(conversationId: String, messageIds: List<String>) {
        messageDataSource.markMessagesAsReceived(conversationId, messageIds)
    }
    
    /**
     * Observe unread messages for a specific conversation.
     * Returns a Flow of message IDs that haven't been read yet.
     */
    fun observeUnreadMessages(conversationId: String): Flow<List<String>> {
        return messageDataSource.observeUnreadMessages(conversationId)
    }
    
    /**
     * Mark specific messages as read in a conversation.
     * 
     * @param conversationId The conversation ID
     * @param messageIds List of message IDs to mark as read
     */
    suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>) {
        messageDataSource.markMessagesAsRead(conversationId, messageIds)
    }
    
    /**
     * Send multiple messages using Firestore batch write (for performance testing).
     * All messages are sent in a single transaction.
     * 
     * @param conversationId The conversation ID
     * @param messages List of message texts to send
     * @param memberIds List of all member IDs in the conversation (from ViewModel state)
     */
    suspend fun sendMessagesBatch(conversationId: String, messages: List<String>, memberIds: List<String>) {
        messageDataSource.sendMessagesBatch(conversationId, messages, memberIds)
        // Update conversation metadata with the last message
        if (messages.isNotEmpty()) {
            conversationDataSource.updateConversationMetadata(
                conversationId = conversationId,
                lastMessageText = messages.last(),
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

