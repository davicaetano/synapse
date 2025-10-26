package com.synapse.data.source.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.synapse.data.source.firestore.entity.MessageEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource for accessing the 'messages' Firestore subcollection.
 * Path: conversations/{conversationId}/messages/{messageId}
 * 
 * RESPONSIBILITY: Raw CRUD operations on messages only.
 * - No business logic
 * - No combining with other data sources
 * - Returns raw entities (not domain models)
 */
@Singleton
class FirestoreMessageDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    // ============================================================
    // READ OPERATIONS
    // ============================================================
    
    /**
     * Listen to all messages in a conversation, ordered by creation time.
     * Returns raw Firestore data.
     * 
     * @param conversationId The conversation ID to listen to
     * @param afterTimestamp Optional timestamp to only fetch messages created after this time (incremental sync)
     */
    fun listenMessages(conversationId: String, afterTimestamp: Long? = null): Flow<List<MessageEntity>> = callbackFlow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ listenMessages START: $conversationId (afterTimestamp=$afterTimestamp)")
        
        var query = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAtMs", Query.Direction.DESCENDING)
        
        // If afterTimestamp provided, only fetch new messages (incremental sync)
        if (afterTimestamp != null) {
            query = query.whereGreaterThan("createdAtMs", afterTimestamp)
            Log.d(TAG, "   🔄 Incremental sync: fetching messages after $afterTimestamp")
        }
        
        val ref = query
        
        var firstEmission = true
        val registration = ref.addSnapshotListener { snapshot, error ->
            val isFromCache = snapshot?.metadata?.isFromCache ?: false
            
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            val messages = snapshot?.documents?.mapNotNull { doc ->
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
                        isDeleted = false,  // Already filtered above
                        deletedBy = null,
                        deletedAtMs = null
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            
            if (messages.isNotEmpty()) {
                val last = messages.last()
            }
            
            if (firstEmission) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "⏱️ listenMessages FIRST EMIT: $conversationId - ${messages.size} msgs in ${elapsed}ms (fromCache=$isFromCache)")
                firstEmission = false
            }
            
            trySend(messages)
        }
        
        awaitClose {
            registration.remove()
        }
    }
    
    /**
     * Listen to a single message by ID.
     * Used for AI summary refinement to display the previous summary.
     * 
     * @param conversationId The conversation ID
     * @param messageId The specific message ID to observe
     * @return Flow of MessageEntity (null if not found or deleted)
     */
    fun listenMessage(conversationId: String, messageId: String): Flow<MessageEntity?> = callbackFlow {
        val ref = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(messageId)
        
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "❌ Error listening to message $messageId", error)
                trySend(null)
                return@addSnapshotListener
            }
            
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            
            try {
                // Skip soft-deleted messages
                val isDeleted = snapshot.getBoolean("isDeleted") ?: false
                if (isDeleted) {
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val message = MessageEntity(
                    id = snapshot.id,
                    text = snapshot.getString("text") ?: "",
                    senderId = snapshot.getString("senderId") ?: "",
                    createdAtMs = snapshot.getLong("createdAtMs") ?: 0L,
                    memberIdsAtCreation = (snapshot.get("memberIdsAtCreation") as? List<*>)
                        ?.mapNotNull { it as? String } 
                        ?: emptyList(),
                    serverTimestamp = snapshot.getTimestamp("serverTimestamp")?.toDate()?.time,
                    type = snapshot.getString("type") ?: "text",
                    isDeleted = false,
                    deletedBy = null,
                    deletedAtMs = null
                )
                
                trySend(message)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error parsing message $messageId", e)
                trySend(null)
            }
        }
        
        awaitClose {
            registration.remove()
        }
    }
    
    /**
     * Fetch older messages from Firestore (one-time query, not a listener).
     * Used for manual lazy loading when user scrolls to top.
     * 
     * @param conversationId The conversation ID
     * @param beforeTimestamp Only fetch messages created before this timestamp
     * @param limit Maximum number of messages to fetch (default: 200)
     * @return List of older messages
     */
    suspend fun fetchOlderMessages(
        conversationId: String,
        beforeTimestamp: Long,
        limit: Int = 200
    ): List<MessageEntity> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "📥 Fetching $limit older messages before timestamp: $beforeTimestamp")
        
        return try {
            val snapshot = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .whereLessThan("createdAtMs", beforeTimestamp)
                .limit(limit.toLong())
                .get()
                .await()
            
            val messages = snapshot.documents.mapNotNull { doc ->
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
                    Log.e(TAG, "❌ Error parsing message: ${e.message}")
                    null
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Fetched ${messages.size} older messages in ${elapsed}ms")
            
            messages
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Error fetching older messages: ${e.message} (${elapsed}ms)", e)
            emptyList()
        }
    }
    
    /**
     * Fetch the most recent N messages from a conversation.
     * Used for initial load when opening a conversation.
     * 
     * @param conversationId The conversation ID
     * @param limit Number of recent messages to fetch (default: 200)
     * @return List of the most recent messages, ordered by creation time descending
     */
    suspend fun fetchRecentMessages(
        conversationId: String,
        limit: Int = 200
    ): List<MessageEntity> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "📥 Fetching $limit most recent messages")
        
        return try {
            val snapshot = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val messages = snapshot.documents.mapNotNull { doc ->
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
                    Log.e(TAG, "❌ Error parsing message: ${e.message}")
                    null
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Fetched ${messages.size} recent messages in ${elapsed}ms")
            
            messages
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Error fetching recent messages: ${e.message} (${elapsed}ms)", e)
            emptyList()
        }
    }
    
    /**
     * Count unread messages in a conversation for a specific user.
     * Counts messages sent by OTHER users (not the current user) that are:
     * - Not deleted (isDeleted = false)
     * - Created after the user's lastSeenAt timestamp
     * 
     * @param conversationId The conversation ID
     * @param userId The current user ID (to exclude their own messages)
     * @param lastSeenAtMs The user's last seen timestamp (from memberStatus)
     * @return Number of unread messages
     */
    suspend fun getUnreadCount(
        conversationId: String,
        userId: String,
        lastSeenAtMs: Long
    ): Int {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "🔢 Counting unread for conv=${conversationId.takeLast(6)}, userId=${userId.takeLast(6)}, lastSeenAtMs=$lastSeenAtMs")
        
        return try {
            val snapshot = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .whereEqualTo("isDeleted", false)     // Only non-deleted
                .whereGreaterThan("createdAtMs", lastSeenAtMs)  // After last seen
                .whereNotEqualTo("senderId", userId)  // Exclude own messages (last to match enabled index)
                .limit(100)  // Only need to count up to 100 (UI shows "99+")
                .get()
                .await()
            
            val count = snapshot.size()
            val elapsed = System.currentTimeMillis() - startTime
            
            // Debug: Show first few messages
            if (snapshot.documents.isNotEmpty()) {
                Log.d(TAG, "   📄 Sample messages:")
                snapshot.documents.take(3).forEach { doc ->
                    Log.d(TAG, "      - senderId=${doc.getString("senderId")?.takeLast(6)}, createdAt=${doc.getLong("createdAtMs")}, deleted=${doc.getBoolean("isDeleted")}")
                }
            }
            
            Log.d(TAG, "✅ Unread count: $count (${elapsed}ms)")
            
            count
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Error counting unread messages: ${e.message} (${elapsed}ms)", e)
            0
        }
    }
    
    // ============================================================
    // WRITE OPERATIONS
    // ============================================================
    
    /**
     * Send a message as a specific user (e.g. bot).
     * Used for system messages like welcome messages.
     * 
     * @param conversationId The conversation ID
     * @param text The message text
     * @param memberIds List of all member IDs in the conversation
     * @param senderId The ID of the user sending the message (e.g. bot ID)
     * @param createdAtMs Optional custom timestamp (default: current time, use 0 for welcome messages to appear first)
     * @param sendNotification Whether to send push notifications (default: true, set false for welcome messages to avoid spam)
     */
    suspend fun sendMessageAs(
        conversationId: String,
        text: String,
        memberIds: List<String>,
        senderId: String,
        createdAtMs: Long = System.currentTimeMillis(),
        sendNotification: Boolean = true  // Default: send notifications
    ): String? {
        val startTime = System.currentTimeMillis()
        
        val messageData = hashMapOf(
            "text" to text,
            "senderId" to senderId,  // Use provided sender ID
            "createdAtMs" to createdAtMs,  // Use provided timestamp (0 for welcome messages)
            "memberIdsAtCreation" to memberIds,
            "serverTimestamp" to FieldValue.serverTimestamp(),
            "type" to "bot",  // Message type (bot welcome message)
            "sendNotification" to sendNotification,  // Control notification sending
            "isDeleted" to false  // Default: not deleted
        )
        
        return try {
            val docRef = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(messageData)
                .await()
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ sendMessageAs($senderId): ${elapsed}ms")
            
            docRef.id
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "⏱️ sendMessageAs FAILED: ${elapsed}ms", e)
            null
        }
    }
    
    /**
     * Send a message to a conversation.
     * Returns the new message ID if successful.
     * 
     * Note: The sender is automatically added to receivedBy and readBy.
     * 
     * @param conversationId The conversation ID
     * @param text The message text
     * @param memberIds List of all member IDs in the conversation (from ViewModel state)
     */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        memberIds: List<String>
    ): String? {
        val startTime = System.currentTimeMillis()
        val userId = auth.currentUser?.uid
        if (userId == null) {
            return null
        }
        
        // memberIdsAtCreation = snapshot of all members at this moment
        val memberIdsAtCreation = memberIds
        
        val messageData = hashMapOf(
            "text" to text,
            "senderId" to userId,
            "createdAtMs" to System.currentTimeMillis(),
            "memberIdsAtCreation" to memberIdsAtCreation,  // Snapshot of group members
            "serverTimestamp" to FieldValue.serverTimestamp(),  // Server assigns actual timestamp
            "type" to "text",  // Message type (normal user message)
            "isDeleted" to false  // Default: not deleted
        )
        
        return try {
            val docRef = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(messageData)
                .await()
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ sendMessage: ${elapsed}ms")
            
            docRef.id
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "⏱️ sendMessage FAILED: ${elapsed}ms", e)
            // Even if there's an error (e.g. offline), Firestore should have cached it
            // The listener will pick it up and show it as PENDING
            null
        }
    }

    /**
     * Send multiple messages using Firestore batch write.
     * More efficient than sending messages one by one.
     * Used for performance testing and bulk operations.
     * 
     * @param conversationId The conversation ID
     * @param messages List of message texts to send
     * @param memberIds List of all member IDs in the conversation (from ViewModel state)
     */
    suspend fun sendMessagesBatch(conversationId: String, messages: List<String>, memberIds: List<String>) {
        val startTime = System.currentTimeMillis()
        val userId = auth.currentUser?.uid ?: return
        
        if (messages.isEmpty()) return
        
        Log.d(TAG, "⏱️ sendMessagesBatch START: ${messages.size} messages")
        
        try {
            // memberIdsAtCreation = snapshot of all members at this moment
            val memberIdsAtCreation = memberIds
            
            val batch = firestore.batch()
            val timestamp = System.currentTimeMillis()
            
            // Create a message document for each message text
            messages.forEachIndexed { index, text ->
                val messageRef = firestore.collection("conversations")
                    .document(conversationId)
                    .collection("messages")
                    .document()  // Auto-generate ID
                
                val messageData = hashMapOf(
                    "id" to messageRef.id,
                    "text" to text,
                    "senderId" to userId,
                    "createdAtMs" to (timestamp + index), // Slightly offset to maintain order
                    "memberIdsAtCreation" to memberIdsAtCreation,  // Snapshot of group members
                    "serverTimestamp" to FieldValue.serverTimestamp(), // Server assigns actual timestamp
                    "type" to "text",  // Message type (normal user message)
                    "isDeleted" to false  // Default: not deleted
                )
                
                batch.set(messageRef, messageData)
            }
            
            // Commit all writes in a single transaction
            batch.commit().await()
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ sendMessagesBatch: ${messages.size} msgs in ${elapsed}ms")
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "⏱️ sendMessagesBatch FAILED: ${elapsed}ms", e)
        }
    }
    
    /**
     * Delete a message (soft delete).
     * Marks the message as deleted by updating isDeleted flag in Firestore.
     */
    suspend fun deleteMessage(conversationId: String, messageId: String, deletedBy: String, timestamp: Long) {
        try {
            val messageRef = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
            
            val updates = hashMapOf<String, Any>(
                "isDeleted" to true,
                "deletedBy" to deletedBy,
                "deletedAtMs" to timestamp
            )
            
            messageRef.update(updates).await()
            Log.d(TAG, "✅ Message deleted: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete message: $messageId", e)
            throw e
        }
    }
    
    companion object {
        private const val TAG = "FirestoreMessageDS"
    }
}

