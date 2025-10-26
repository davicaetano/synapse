package com.synapse.data.repository

import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    
    companion object {
        /**
         * System bot that sends welcome messages to new conversations.
         * Created via firebase/scripts/create-synapse-bot.js
         */
        private const val SYNAPSE_BOT_ID = "synapse-bot-system"
        
        // Cannot use trimIndent() in const, so using regular val
        private val WELCOME_MESSAGE = """👋 Welcome to Synapse!

This is the beginning of your conversation. All messages are end-to-end encrypted and synced across all your devices.

Feel free to start chatting!"""
    }

    // ============================================================
    // READ OPERATIONS (simple flows, NO complex transformations)
    // ============================================================

    /**
     * Observe raw conversations for a user.
     * Returns ConversationEntity without users or presence data.
     * ViewModel should combine this with users and presence.
     */
    fun observeConversations(
        userId: String,
        includesCacheChanges: Boolean = true,
    ): Flow<List<ConversationEntity>> {
        return conversationDataSource.listenConversations(userId, includesCacheChanges)
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
     * Observe a single message by ID from Firestore.
     * Used for AI summary refinement to display the previous summary.
     */
    fun observeMessage(conversationId: String, messageId: String): Flow<MessageEntity?> {
        return firestoreMessageDataSource.listenMessage(conversationId, messageId)
    }
    
    /**
     * Upsert messages into Room cache.
     * Called by ViewModel when syncing from Firestore.
     */
    suspend fun upsertMessagesToRoom(messages: List<MessageEntity>, conversationId: String) {
        roomMessageDataSource.upsertMessages(messages, conversationId)
    }
    
    /**
     * Start incremental message sync from Firestore to Room.
     * 
     * HOW IT WORKS:
     * 1. Gets last message timestamp from Room (or null if empty)
     * 2. Listens to Firestore for messages AFTER that timestamp
     * 3. Upserts new messages to Room
     * 4. Updates internal timestamp to last received message
     * 5. Next listener emission only fetches messages after new timestamp
     * 
     * BENEFITS:
     * - Always syncs ONLY new messages (1-5 msgs, not 100)
     * - No artificial limits (syncs everything incrementally)
     * - Fast upserts (5ms, not 500ms)
     * - Maintains Room cache for offline-first
     * 
     * @param scope CoroutineScope to launch the sync job in (usually viewModelScope)
     * @param conversationId The conversation ID to sync
     * @return Job that can be cancelled when sync should stop
     */
    fun startIncrementalSync(
        scope: kotlinx.coroutines.CoroutineScope,
        conversationId: String
    ): Job {
        return scope.launch {
            // Get initial last timestamp from Room
            var lastTimestamp = roomMessageDataSource.getLastMessageTimestamp(conversationId)
            Log.d("ConversationRepo", "🔄 Starting incremental sync for $conversationId (lastTimestamp=$lastTimestamp)")
            
            // If Room is empty, fetch initial messages first (only last 200, not all history)
            if (lastTimestamp == null) {
                Log.d("ConversationRepo", "   📦 Room is empty, fetching initial 200 messages...")
                val initialMessages = firestoreMessageDataSource.fetchRecentMessages(
                    conversationId = conversationId,
                    limit = 200
                )
                
                if (initialMessages.isNotEmpty()) {
                    Log.d("ConversationRepo", "   💾 Upserting ${initialMessages.size} initial messages to Room...")
                    roomMessageDataSource.upsertMessages(initialMessages, conversationId)
                    
                    // Update lastTimestamp to the most recent message
                    lastTimestamp = initialMessages.maxByOrNull { it.createdAtMs }?.createdAtMs
                    Log.d("ConversationRepo", "   ✅ Initial messages loaded, lastTimestamp=$lastTimestamp")
                }
            }
            
            // Listen to Firestore for messages AFTER lastTimestamp (only new messages from now on)
            firestoreMessageDataSource.listenMessages(conversationId, lastTimestamp)
                .collect { newMessages ->
                    if (newMessages.isNotEmpty()) {
                        Log.d("ConversationRepo", "   📥 Received ${newMessages.size} new messages, upserting to Room...")
                        
                        // Upsert to Room
                        roomMessageDataSource.upsertMessages(newMessages, conversationId)
                        
                        // Update lastTimestamp to the most recent message
                        val newestMessage = newMessages.maxByOrNull { it.createdAtMs }
                        if (newestMessage != null && newestMessage.createdAtMs > (lastTimestamp ?: 0)) {
                            lastTimestamp = newestMessage.createdAtMs
                            Log.d("ConversationRepo", "   ✅ Updated lastTimestamp to $lastTimestamp")
                        }
                    } else {
                        Log.d("ConversationRepo", "   ℹ️ No new messages")
                    }
                }
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
     * Uses direct document listener (much faster than filtering all conversations).
     */
    fun observeConversation(
        conversationId: String,
        includesCacheChanges: Boolean = true
    ): Flow<ConversationEntity?> {
        return conversationDataSource.listenConversation(conversationId, includesCacheChanges)
    }

    // ============================================================
    // WRITE OPERATIONS (coordinating multiple DataSources)
    // ============================================================

    /**
     * Send a welcome message from Synapse Bot to a new conversation.
     * Called automatically when creating any conversation.
     * 
     * @param conversationId The conversation ID
     * @param memberIds List of all member IDs in the conversation
     */
    private suspend fun sendWelcomeMessage(conversationId: String, memberIds: List<String>) {
        // Send welcome message as the Synapse Bot with timestamp 0 to ensure it appears first
        // sendNotification = false to avoid spamming users with welcome message notifications
        firestoreMessageDataSource.sendMessageAs(
            conversationId = conversationId,
            text = WELCOME_MESSAGE,
            memberIds = memberIds,
            senderId = SYNAPSE_BOT_ID,
            createdAtMs = 0L,  // Timestamp 0 ensures this message always appears first
            sendNotification = false  // Don't send push notification for welcome messages
        )
        
        // Update conversation metadata (use actual timestamp for conversation ordering)
        val timestamp = System.currentTimeMillis()
        conversationDataSource.updateConversationMetadata(
            conversationId = conversationId,
            lastMessageText = WELCOME_MESSAGE,
            timestamp = timestamp
        )
    }
    
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

        val conversationId = conversationDataSource.createDirectConversation(userIds)
        
        // Send welcome message to new conversation
        if (conversationId != null) {
            sendWelcomeMessage(conversationId, userIds + SYNAPSE_BOT_ID)
        }
        
        return conversationId
    }

    /**
     * Create a self conversation (user talking to themselves / AI).
     */
    suspend fun createSelfConversation(): String? {
        val userId = auth.currentUser?.uid ?: return null
        val conversationId = conversationDataSource.createSelfConversation(userId)
        
        // Send welcome message to new conversation
        if (conversationId != null) {
            sendWelcomeMessage(conversationId, listOf(userId, SYNAPSE_BOT_ID))
        }
        
        return conversationId
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

        val conversationId = conversationDataSource.createGroupConversation(
            memberIds = allMemberIds,
            groupName = groupName,
            createdBy = currentUserId  // Current user is the admin
        )
        
        // Send welcome message to new conversation
        if (conversationId != null) {
            sendWelcomeMessage(conversationId, allMemberIds + SYNAPSE_BOT_ID)
        }
        
        return conversationId
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
    
    /**
     * Delete a message (soft delete).
     * Marks the message as deleted in both Firestore and Room cache.
     * The message will no longer appear in the conversation.
     * 
     * @param conversationId The conversation ID
     * @param messageId The message ID to delete
     */
    suspend fun deleteMessage(conversationId: String, messageId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val timestamp = System.currentTimeMillis()
        
        // Update Firestore (soft delete)
        firestoreMessageDataSource.deleteMessage(conversationId, messageId, currentUserId, timestamp)
        
        // Update Room cache (soft delete)
        roomMessageDataSource.markMessageAsDeleted(messageId, currentUserId, timestamp)
    }
    
    /**
     * Fetch older messages from Firestore and insert into Room cache.
     * Used for manual lazy loading when user scrolls to top.
     * 
     * This method:
     * 1. Gets the oldest message timestamp from Room
     * 2. Fetches 200 messages older than that from Firestore
     * 3. Inserts them into Room
     * 4. Paging3 automatically detects and displays them
     * 
     * @param conversationId The conversation ID
     * @return Number of messages fetched (0 if reached end)
     */
    suspend fun fetchOlderMessages(conversationId: String): Int {
        // Get oldest message timestamp from Room
        val oldestTimestamp = roomMessageDataSource.getOldestMessageTimestamp(conversationId)
        
        if (oldestTimestamp == null) {
            Log.d("ConversationRepo", "📭 No messages in Room yet, skipping older messages fetch")
            return 0
        }
        
        Log.d("ConversationRepo", "📥 Fetching older messages before timestamp: $oldestTimestamp")
        
        // Fetch older messages from Firestore
        val olderMessages = firestoreMessageDataSource.fetchOlderMessages(
            conversationId = conversationId,
            beforeTimestamp = oldestTimestamp,
            limit = 200
        )
        
        if (olderMessages.isEmpty()) {
            Log.d("ConversationRepo", "🏁 No more older messages to fetch (reached end)")
            return 0
        }
        
        // Insert into Room
        roomMessageDataSource.upsertMessages(olderMessages, conversationId)
        
        Log.d("ConversationRepo", "✅ Fetched and inserted ${olderMessages.size} older messages")
        return olderMessages.size
    }
}

