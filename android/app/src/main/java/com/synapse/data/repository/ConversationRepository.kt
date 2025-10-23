package com.synapse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.source.firestore.FirestoreConversationDataSource
import com.synapse.data.source.firestore.FirestoreMessageDataSource
import com.synapse.data.source.firestore.FirestoreUserDataSource
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.MessageEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import com.synapse.data.source.realtime.entity.PresenceEntity
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
 * Philosophy: Keep it simple. Repository exposes data, ViewModel processes it.
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDataSource: FirestoreConversationDataSource,
    private val messageDataSource: FirestoreMessageDataSource,
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
     * Observe messages in a conversation.
     * Returns raw MessageEntity list.
     */
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDataSource.listenMessages(conversationId)
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
     */
    suspend fun sendMessage(conversationId: String, text: String) {

        // Send message (may return null if offline, but Firestore caches it)
        messageDataSource.sendMessage(conversationId, text)

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
    fun observeUnreadMessageCount(conversationId: String): Flow<Int> {
        return messageDataSource.observeUnreadMessageCount(conversationId)
    }

    /**
     * Mark the last message in a conversation as received.
     * Optimization: if last message is received, all previous ones are too.
     */
    suspend fun markLastMessageAsReceived(conversationId: String) {
        messageDataSource.markLastMessageAsReceived(conversationId)
    }
}

