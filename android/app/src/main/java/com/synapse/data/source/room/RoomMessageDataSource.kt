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
     * OPTIMIZATION: Only inserts NEW messages (timestamp > last cached message).
     * This avoids re-writing 3000+ messages when only 20 are new.
     */
    suspend fun upsertMessages(messages: List<MessageEntity>, conversationId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        
        // Get the most recent message timestamp we already have
        val lastCachedTimestamp = messageDao.getLastMessageTimestamp(conversationId) ?: 0L
        
        // Filter only NEW messages (timestamp > what we have)
        val newMessages = messages.filter { it.createdAtMs > lastCachedTimestamp }
        
        if (newMessages.isEmpty()) {
            Log.d(TAG, "✅ [ROOM] No new messages to upsert for $conversationId (already cached)")
            return@withContext
        }
        
        val roomEntities = newMessages.map { MessageRoomEntity.fromEntity(it, conversationId) }
        messageDao.upsertMessages(roomEntities)
        Log.d(TAG, "✅ [ROOM] Upserted ${roomEntities.size} NEW messages for $conversationId (filtered from ${messages.size} total)")
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

