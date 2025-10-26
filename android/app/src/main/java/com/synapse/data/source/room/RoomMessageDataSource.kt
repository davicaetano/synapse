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
                initialLoadSize = 20
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
    
    companion object {
        private const val TAG = "RoomMessageDS"
    }
}

