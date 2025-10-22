package com.synapse.data.source.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
     */
    fun listenMessages(conversationId: String): Flow<List<MessageEntity>> = callbackFlow {
        val ref = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAtMs")
        
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to messages in $conversationId, sending empty list", error)
                trySend(emptyList())  // Keep flow alive, will update when error resolves
                return@addSnapshotListener
            }
            
            val messages = snapshot?.documents?.mapNotNull { doc ->
                try {
                    MessageEntity(
                        id = doc.id,
                        text = doc.getString("text") ?: "",
                        senderId = doc.getString("senderId") ?: "",
                        createdAtMs = doc.getLong("createdAtMs") ?: 0L,
                        receivedBy = (doc.get("receivedBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        readBy = (doc.get("readBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        serverTimestamp = doc.getTimestamp("serverTimestamp")?.toDate()?.time
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message ${doc.id}", e)
                    null
                }
            } ?: emptyList()
            
            trySend(messages)
        }
        
        awaitClose { registration.remove() }
    }
    
    /**
     * Get a single message by ID (one-time read, not a listener).
     */
    suspend fun getMessage(conversationId: String, messageId: String): MessageEntity? {
        return try {
            val doc = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .get()
                .await()
            
            if (!doc.exists()) return null
            
            MessageEntity(
                id = doc.id,
                text = doc.getString("text") ?: "",
                senderId = doc.getString("senderId") ?: "",
                createdAtMs = doc.getLong("createdAtMs") ?: 0L,
                receivedBy = (doc.get("receivedBy") as? List<*>)
                    ?.mapNotNull { it as? String } 
                    ?: emptyList(),
                readBy = (doc.get("readBy") as? List<*>)
                    ?.mapNotNull { it as? String } 
                    ?: emptyList(),
                serverTimestamp = doc.getTimestamp("serverTimestamp")?.toDate()?.time
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting message $messageId", e)
            null
        }
    }
    
    /**
     * Get all messages in a conversation (one-time read).
     * Useful for batch operations like marking all as read.
     */
    suspend fun getAllMessages(conversationId: String): List<MessageEntity> {
        return try {
            val snapshot = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAtMs")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    MessageEntity(
                        id = doc.id,
                        text = doc.getString("text") ?: "",
                        senderId = doc.getString("senderId") ?: "",
                        createdAtMs = doc.getLong("createdAtMs") ?: 0L,
                        receivedBy = (doc.get("receivedBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        readBy = (doc.get("readBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        serverTimestamp = doc.getTimestamp("serverTimestamp")?.toDate()?.time
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all messages from $conversationId", e)
            emptyList()
        }
    }
    
    // ============================================================
    // WRITE OPERATIONS
    // ============================================================
    
    /**
     * Send a message to a conversation.
     * Returns the new message ID if successful.
     * 
     * Note: The sender is automatically added to receivedBy and readBy.
     */
    suspend fun sendMessage(
        conversationId: String,
        text: String
    ): String? {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot send message: user not authenticated")
            return null
        }
        
        val messageData = hashMapOf(
            "text" to text,
            "senderId" to userId,
            "createdAtMs" to System.currentTimeMillis(),
            "receivedBy" to listOf(userId),  // Sender has received their own message
            "readBy" to listOf(userId),      // Sender has read their own message
            "serverTimestamp" to FieldValue.serverTimestamp()  // Server assigns actual timestamp
        )
        
        return try {
            val docRef = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .add(messageData)
                .await()
            
            Log.d(TAG, "Message sent successfully: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            // Even if there's an error (e.g. offline), Firestore should have cached it
            // The listener will pick it up and show it as PENDING
            Log.w(TAG, "Error sending message to $conversationId (may be offline): ${e.message}")
            null
        }
    }
    
    /**
     * Mark a message as received by the current user.
     * Adds current user ID to the receivedBy array.
     */
    suspend fun markMessageReceived(conversationId: String, messageId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("receivedBy", FieldValue.arrayUnion(userId))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking message $messageId as received", e)
        }
    }
    
    /**
     * Mark a message as read by the current user.
     * Also marks as received (you can't read without receiving first).
     * Adds current user ID to both readBy and receivedBy arrays.
     */
    suspend fun markMessageAsRead(conversationId: String, messageId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            val docRef = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
            
            // Batch update both fields atomically
            firestore.runTransaction { transaction ->
                transaction.update(docRef, "readBy", FieldValue.arrayUnion(userId))
                transaction.update(docRef, "receivedBy", FieldValue.arrayUnion(userId))
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking message $messageId as read", e)
        }
    }
    
    /**
     * Mark multiple messages as read by the current user.
     * Also marks them as received (you can't read without receiving first).
     * Batch operation for efficiency.
     */
    suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>) {
        val userId = auth.currentUser?.uid ?: return
        if (messageIds.isEmpty()) return
        
        try {
            // Use batch write for efficiency
            val batch = firestore.batch()
            
            messageIds.forEach { messageId ->
                val docRef = firestore.collection("conversations")
                    .document(conversationId)
                    .collection("messages")
                    .document(messageId)
                
                // Update BOTH readBy and receivedBy
                batch.update(docRef, "readBy", FieldValue.arrayUnion(userId))
                batch.update(docRef, "receivedBy", FieldValue.arrayUnion(userId))
            }
            
            batch.commit().await()
            Log.d(TAG, "Marked ${messageIds.size} messages as read and received")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking messages as read", e)
        }
    }
    
    /**
     * Mark ALL messages in a conversation as read by the current user.
     * This is less efficient than markMessagesAsRead for specific IDs,
     * but useful when opening a conversation for the first time.
     */
    suspend fun markAllMessagesAsRead(conversationId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            // Get all messages first
            val messages = getAllMessages(conversationId)
            
            // Filter messages where current user is not in readBy
            val unreadMessageIds = messages
                .filter { userId !in it.readBy }
                .map { it.id }
            
            if (unreadMessageIds.isEmpty()) {
                Log.d(TAG, "No unread messages to mark")
                return
            }
            
            // Mark them as read using batch operation
            markMessagesAsRead(conversationId, unreadMessageIds)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all messages as read in $conversationId", e)
        }
    }
    
    /**
     * Delete a message (soft delete - could add a 'deleted' flag in the future).
     * For now, this is a hard delete.
     * 
     * Note: Consider adding soft delete functionality later for "Message deleted" placeholders.
     */
    suspend fun deleteMessage(conversationId: String, messageId: String) {
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            
            Log.d(TAG, "Message $messageId deleted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message $messageId", e)
        }
    }
    
    /**
     * Count unread messages in a conversation for the current user.
     * A message is unread if:
     * - senderId != currentUserId (not my message)
     * - readBy does NOT contain currentUserId (I haven't read it)
     */
    suspend fun getUnreadMessageCount(conversationId: String): Int {
        val userId = auth.currentUser?.uid ?: return 0
        
        return try {
            val snapshot = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .whereNotEqualTo("senderId", userId)  // Not my messages
                .get()
                .await()
            
            // Count messages where readBy doesn't contain current user
            snapshot.documents.count { doc ->
                val readBy = (doc.get("readBy") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
                userId !in readBy
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting unread messages in $conversationId", e)
            0
        }
    }
    
    /**
     * Mark the last message in a conversation as received by the current user.
     * Uses the optimization: if the last message is received, all previous ones are too.
     */
    suspend fun markLastMessageAsReceived(conversationId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            // Get the last message (ordered by createdAtMs descending, limit 1)
            val lastMessageSnapshot = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAtMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            val lastMessage = lastMessageSnapshot.documents.firstOrNull()
            if (lastMessage != null) {
                val receivedBy = (lastMessage.get("receivedBy") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
                
                // Only update if current user is not already in receivedBy
                if (userId !in receivedBy) {
                    lastMessage.reference.update("receivedBy", FieldValue.arrayUnion(userId)).await()
                    Log.d(TAG, "Marked last message in $conversationId as received")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking last message as received in $conversationId", e)
        }
    }
    
    companion object {
        private const val TAG = "FirestoreMessageDS"
    }
}

