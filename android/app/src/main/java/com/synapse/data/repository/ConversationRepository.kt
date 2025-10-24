package com.synapse.data.repository

import androidx.paging.PagingData
import com.google.firebase.auth.FirebaseAuth
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
 * Uses RoomMessageDataSource for reads (local cache + Paging3).
 * Uses FirestoreMessageDataSource for writes (remote).
 *
 * Philosophy: Keep it simple. Repository exposes data, ViewModel orchestrates sync.
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDataSource: FirestoreConversationDataSource,
    private val roomMessageDataSource: RoomMessageDataSource,
    private val firestoreMessageDataSource: FirestoreMessageDataSource,
    private val userDataSource: FirestoreUserDataSource,
    private val presenceDataSource: RealtimePresenceDataSource,
    private val auth: FirebaseAuth
) {

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
     * Observe messages with pagination support using Room + Paging3.
     * Returns PagingData for efficient lazy loading.
     * 
     * PURE ROOM - reads from local cache only.
     * ViewModel orchestrates Firebase → Room sync separately.
     */
    fun observeMessagesPaged(conversationId: String): Flow<PagingData<MessageEntity>> {
        return roomMessageDataSource.observeMessagesPaged(conversationId)
    }
    
    /**
     * Listen to messages from Firestore.
     * Returns Flow of message updates from remote.
     * 
     * This is used by ViewModel to sync Firestore → Room.
     */
    fun listenMessagesFromFirestore(conversationId: String): Flow<List<MessageEntity>> {
        return firestoreMessageDataSource.listenMessages(conversationId)
    }
    
    /**
     * Upsert messages into Room cache.
     * Called by ViewModel when syncing from Firestore.
     */
    suspend fun upsertMessagesToRoom(messages: List<MessageEntity>, conversationId: String) {
        roomMessageDataSource.upsertMessages(messages, conversationId)
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
     * Uses direct document listener (much faster than filtering all conversations).
     */
    fun observeConversation(userId: String, conversationId: String): Flow<ConversationEntity?> {
        return conversationDataSource.listenConversation(conversationId)
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
        // Send message to Firestore (may return null if offline, but Firestore caches it)
        firestoreMessageDataSource.sendMessage(conversationId, text, memberIds)

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
     * Update group conversation name.
     */
    suspend fun updateGroupName(conversationId: String, groupName: String) {
        conversationDataSource.updateGroupName(conversationId, groupName)
    }
    
    /**
     * Update member's lastReceivedAt timestamp (when user receives messages).
     * NEW APPROACH: Single write per conversation instead of per-message.
     * 
     * @param conversationId Conversation ID
     * @param serverTimestamp Server timestamp from the most recent message
     */
    suspend fun updateMemberLastReceivedAt(conversationId: String, serverTimestamp: com.google.firebase.Timestamp) {
        conversationDataSource.updateMemberLastReceivedAt(conversationId, serverTimestamp)
    }
    
    /**
     * Update member's lastSeenAt to NOW (when user opens conversation).
     * Badge disappears instantly.
     */
    suspend fun updateMemberLastSeenAtNow(conversationId: String) {
        conversationDataSource.updateMemberLastSeenAtNow(conversationId)
    }
    
    /**
     * Update member's lastMessageSentAt to NOW (when user sends a message).
     * Used to determine if badge should show for other users.
     */
    suspend fun updateMemberLastMessageSentAtNow(conversationId: String) {
        conversationDataSource.updateMemberLastMessageSentAtNow(conversationId)
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
        firestoreMessageDataSource.sendMessagesBatch(conversationId, messages, memberIds)
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

