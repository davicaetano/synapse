package com.synapse.data.source.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.synapse.data.source.room.entity.MessageRoomEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for message cache operations.
 * Provides instant local reads compared to Firestore.
 */
@Dao
interface MessageDao {

    /**
     * Observe messages for a conversation with pagination support.
     * Returns PagingSource for efficient loading of large message lists.
     * 
     * Sorted DESC so newest messages appear first (for reverseLayout in LazyColumn).
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtMs DESC")
    fun observeMessagesPaged(conversationId: String): PagingSource<Int, MessageRoomEntity>

    /**
     * Observe all messages for a conversation (sorted by timestamp).
     * Returns a Flow that updates automatically when Room data changes.
     * Used for non-paged scenarios.
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtMs ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageRoomEntity>>

    /**
     * Get unread message count per conversation for a specific user.
     * Returns all messages grouped by conversation where notReadBy contains the userId.
     */
    @Query("SELECT * FROM messages WHERE notReadBy LIKE '%' || :userId || '%'")
    fun getUnreadMessagesByUser(userId: String): Flow<List<MessageRoomEntity>>

    /**
     * Get unreceived messages for a specific user.
     * Returns all messages where notReceivedBy contains the userId.
     */
    @Query("SELECT * FROM messages WHERE notReceivedBy LIKE '%' || :userId || '%'")
    fun getUnreceivedMessagesByUser(userId: String): Flow<List<MessageRoomEntity>>

    /**
     * Insert or update multiple messages.
     * Used to sync Firebase data into Room cache.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageRoomEntity>)

    /**
     * Delete all messages for a conversation.
     * Used when leaving a conversation or cleaning up.
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    /**
     * Delete all messages (for logout/cleanup).
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}

