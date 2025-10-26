package com.synapse.data.source.room

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.synapse.data.source.firestore.entity.MessageEntity
import com.synapse.data.source.room.dao.MessageDao
import com.synapse.data.source.room.entity.MessageRoomEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message DataSource for Room (local cache).
 * 
 * PURE ROOM - NO Firestore dependencies:
 * - All READS come from Room (instant, ~50ms for 2500 messages)
 * - Provides Paging3 support for efficient lazy loading
 * - ViewModel orchestrates Firebase → Room sync when needed
 * 
 * This provides instant UI updates. Sync is managed externally.
 */
@Singleton
class RoomMessageDataSource @Inject constructor(
    private val messageDao: MessageDao
) {
    
    /**
     * Observe messages with Paging3 support (efficient for large lists).
     * 
     * BENEFITS:
     * - Loads messages in chunks (50 at a time)
     * - Scroll up → automatically loads more
     * - UI always responsive (never loads all 2500 at once!)
     * 
     * PURE ROOM - reads from local cache only.
     */
    fun observeMessagesPaged(conversationId: String): Flow<PagingData<MessageEntity>> {
        Log.d(TAG, "📄 [ROOM] observeMessagesPaged START: $conversationId")
        
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                prefetchDistance = 15,
                enablePlaceholders = false,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { messageDao.observeMessagesPaged(conversationId) }
        ).flow.map { pagingData ->
            pagingData.map { roomEntity -> roomEntity.toEntity() }
        }
    }
    
    /**
     * Upsert messages into Room cache.
     * Called by ViewModel when syncing from Firestore.
     * 
     * NO FILTERING: Always upsert all messages to ensure updates (e.g. serverTimestamp) propagate.
     * Room's REPLACE strategy is efficient - only writes if data changed.
     */
    suspend fun upsertMessages(messages: List<MessageEntity>, conversationId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        
        val roomEntities = messages.map { MessageRoomEntity.fromEntity(it, conversationId) }
        messageDao.upsertMessages(roomEntities)
        Log.d(TAG, "✅ [ROOM] Upserted ${roomEntities.size} messages for $conversationId")
    }
    
    /**
     * Mark a message as deleted (soft delete) in Room cache.
     * Called when user deletes a message.
     */
    suspend fun markMessageAsDeleted(messageId: String, deletedBy: String, timestamp: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        messageDao.markMessageAsDeleted(messageId, deletedBy, timestamp)
        Log.d(TAG, "✅ [ROOM] Marked message as deleted: $messageId")
    }
    
    /**
     * Get the timestamp of the last message in Room cache for incremental sync.
     * Returns null if no messages exist yet (first sync).
     * Used by incremental sync to query Firestore for only NEW messages.
     */
    suspend fun getLastMessageTimestamp(conversationId: String): Long? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val timestamp = messageDao.getLastMessageTimestamp(conversationId)
        Log.d(TAG, "📅 [ROOM] Last message timestamp for $conversationId: $timestamp")
        return@withContext timestamp
    }
    
    /**
     * Get the timestamp of the oldest message in Room cache.
     * Used for manual lazy loading to fetch older messages from Firebase.
     * Returns null if no messages exist yet.
     */
    suspend fun getOldestMessageTimestamp(conversationId: String): Long? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val timestamp = messageDao.getOldestMessageTimestamp(conversationId)
        Log.d(TAG, "📅 [ROOM] Oldest message timestamp for $conversationId: $timestamp")
        return@withContext timestamp
    }
    
    /**
     * Calculate unread counts for conversations based on memberStatus timestamps.
     * 
     * NEW APPROACH - Uses conversation-level lastSeenAt tracking:
     * - Takes map of conversationId → lastSeenAtMs from conversation.memberStatus
     * - Counts messages where serverTimestamp > lastSeenAtMs
     * - Caps at 10 per conversation (shows "10+" in UI)
     * 
     * This is called FROM InboxViewModel which has access to memberStatus.
     * 
     * Performance: ~10ms for 100 conversations
     */
    suspend fun calculateUnreadCounts(
        conversationLastSeenMap: Map<String, Long?>
    ): Map<String, Int> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ [ROOM] calculateUnreadCounts START for ${conversationLastSeenMap.size} conversations")
        Log.d(TAG, "   Input map: $conversationLastSeenMap")
        
        val conversationIds = conversationLastSeenMap.keys.toList()
        if (conversationIds.isEmpty()) {
            Log.d(TAG, "   Empty conversationIds - returning empty map")
            return emptyMap()
        }
        
        Log.d(TAG, "   Querying Room for ${conversationIds.size} conversations")
        
        val messages = messageDao.getMessagesForConversations(conversationIds)
        Log.d(TAG, "   Room returned ${messages.size} total messages")
        
        val countsByConv = mutableMapOf<String, Int>()
        
        // For each conversation, count messages newer than lastSeenAt
        conversationLastSeenMap.forEach { (convId, lastSeenAtMs) ->
            val conversationMessages = messages.filter { it.conversationId == convId }
            Log.d(TAG, "   Conv ${convId.takeLast(6)}: ${conversationMessages.size} messages in Room, lastSeenAt=$lastSeenAtMs")
            
            val unreadCount = if (lastSeenAtMs == null) {
                // Never seen - all messages are unread
                conversationMessages.size
            } else {
                // Count messages with serverTimestamp > lastSeenAt
                conversationMessages.count { msg ->
                    val serverTimestamp = msg.serverTimestamp
                    val isUnread = serverTimestamp != null && serverTimestamp > lastSeenAtMs
                    if (isUnread) {
                        Log.d(TAG, "      Unread msg: serverTimestamp=$serverTimestamp > lastSeenAt=$lastSeenAtMs")
                    }
                    isUnread
                }
            }
            
            Log.d(TAG, "   Conv ${convId.takeLast(6)}: $unreadCount unread messages (before cap)")
            
            // Cap at 10 (UI shows "10+")
            if (unreadCount > 0) {
                countsByConv[convId] = minOf(unreadCount, 10)
            }
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        val total = countsByConv.values.sum()
        Log.d(TAG, "✅ [ROOM] calculateUnreadCounts: ${countsByConv.size} convs with unread, $total total (capped at 10+) in ${elapsed}ms")
        Log.d(TAG, "   Final result: $countsByConv")
        
        return countsByConv
    }
    
    companion object {
        private const val TAG = "RoomMessageDS"
    }
}

