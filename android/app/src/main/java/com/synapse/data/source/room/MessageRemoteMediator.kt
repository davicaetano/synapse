package com.synapse.data.source.room

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.synapse.data.source.firestore.entity.MessageEntity
import com.synapse.data.source.room.dao.MessageDao
import com.synapse.data.source.room.entity.MessageRoomEntity
import kotlinx.coroutines.tasks.await

/**
 * RemoteMediator for lazy loading message history from Firebase.
 * 
 * HOW IT WORKS:
 * 1. User scrolls to top (oldest messages in Room)
 * 2. Paging3 detects end of local data
 * 3. RemoteMediator.load() is triggered
 * 4. Fetches 200 older messages from Firebase (BEFORE oldest in Room)
 * 5. Inserts into Room
 * 6. Paging3 automatically renders new data
 * 
 * BENEFITS:
 * - Lazy loading (fetch only when needed)
 * - Infinite scroll (keeps fetching as user scrolls)
 * - Room as cache (instant local reads)
 * - Offline support (Room persists data)
 */
@OptIn(ExperimentalPagingApi::class)
class MessageRemoteMediator(
    private val conversationId: String,
    private val firestore: FirebaseFirestore,
    private val messageDao: MessageDao
) : RemoteMediator<Int, MessageRoomEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MessageRoomEntity>
    ): MediatorResult {
        return try {
            // With reverseLayout=true, Paging3 directions are inverted:
            // - APPEND = scroll visually UP (to older messages)
            // - PREPEND = scroll visually DOWN (to newer messages)
            // - REFRESH = initial load
            when (loadType) {
                LoadType.REFRESH -> {
                    // Don't fetch on refresh - incremental sync handles this
                    Log.d(TAG, "📄 RemoteMediator: REFRESH - skipping (handled by incremental sync)")
                    return MediatorResult.Success(endOfPaginationReached = false)
                }
                LoadType.PREPEND -> {
                    // With reverseLayout, PREPEND = scroll visually DOWN to newer msgs
                    // Incremental sync handles new messages, so skip
                    Log.d(TAG, "📄 RemoteMediator: PREPEND (newer msgs) - skipping (handled by incremental sync)")
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    // With reverseLayout, APPEND = scroll visually UP to older messages
                    // This is what we want! Fetch older messages from Firebase
                    Log.d(TAG, "📄 RemoteMediator: APPEND (older msgs) - fetching from Firebase")
                    
                    // Get oldest message timestamp in Room
                    val oldestTimestamp = messageDao.getOldestMessageTimestamp(conversationId)
                    
                    // Build query based on whether Room has messages
                    val snapshot = if (oldestTimestamp == null) {
                        // Room is empty (first load) - fetch most recent 200 messages
                        Log.d(TAG, "   📥 Room empty, fetching initial 200 messages from Firebase...")
                        firestore.collection("conversations")
                            .document(conversationId)
                            .collection("messages")
                            .orderBy("createdAtMs", Query.Direction.DESCENDING)
                            .limit(200)
                            .get()
                            .await()
                    } else {
                        // Room has messages - fetch 200 older messages (BEFORE oldestTimestamp)
                        Log.d(TAG, "   📅 Oldest message in Room: $oldestTimestamp")
                        Log.d(TAG, "   🔍 Fetching 200 messages BEFORE $oldestTimestamp from Firebase...")
                        firestore.collection("conversations")
                            .document(conversationId)
                            .collection("messages")
                            .orderBy("createdAtMs", Query.Direction.DESCENDING)
                            .whereLessThan("createdAtMs", oldestTimestamp)
                            .limit(200)
                            .get()
                            .await()
                    }
                    
                    val olderMessages = snapshot.documents.mapNotNull { doc ->
                        try {
                            // Skip soft-deleted messages
                            val isDeleted = doc.getBoolean("isDeleted") ?: false
                            if (isDeleted) return@mapNotNull null
                            
                            MessageEntity(
                                id = doc.id,
                                text = doc.getString("text") ?: "",
                                senderId = doc.getString("senderId") ?: "",
                                createdAtMs = doc.getLong("createdAtMs") ?: 0L,
                                memberIdsAtCreation = (doc.get("memberIdsAtCreation") as? List<*>)
                                    ?.mapNotNull { it as? String } 
                                    ?: emptyList(),
                                serverTimestamp = doc.getTimestamp("serverTimestamp")?.toDate()?.time,
                                type = doc.getString("type") ?: "text",
                                isDeleted = false,
                                deletedBy = null,
                                deletedAtMs = null
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "   ❌ Error parsing message: ${e.message}")
                            null
                        }
                    }
                    
                    Log.d(TAG, "   📥 Fetched ${olderMessages.size} older messages from Firebase")
                    
                    // Insert into Room
                    if (olderMessages.isNotEmpty()) {
                        val roomEntities = olderMessages.map { MessageRoomEntity.fromEntity(it, conversationId) }
                        messageDao.upsertMessages(roomEntities)
                        Log.d(TAG, "   ✅ Inserted ${roomEntities.size} older messages into Room")
                    }
                    
                    // End of pagination reached if we got less than requested
                    val endOfPaginationReached = olderMessages.size < 200
                    Log.d(TAG, "   🏁 End of pagination: $endOfPaginationReached")
                    
                    MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ RemoteMediator error: ${e.message}", e)
            MediatorResult.Error(e)
        }
    }
    
    companion object {
        private const val TAG = "MessageRemoteMediator"
    }
}

