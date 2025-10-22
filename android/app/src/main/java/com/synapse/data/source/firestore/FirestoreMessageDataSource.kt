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
            val isFromCache = snapshot?.metadata?.isFromCache ?: false
            val hasPendingWrites = snapshot?.metadata?.hasPendingWrites() ?: false
            
            if (error != null) {
                trySend(emptyList())
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
                    null
                }
            } ?: emptyList()
            
            if (messages.isNotEmpty()) {
                val last = messages.last()
            }
            
            trySend(messages)
        }
        
        awaitClose {
            registration.remove()
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
            
            docRef.id
        } catch (e: Exception) {
            // Even if there's an error (e.g. offline), Firestore should have cached it
            // The listener will pick it up and show it as PENDING
            null
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
     * Uses efficient Firestore query to only fetch unread messages.
     */
    suspend fun markAllMessagesAsRead(conversationId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            // Query only messages not sent by me (more efficient than downloading everything)
            val snapshot = firestore.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .whereNotEqualTo("senderId", userId)  // Only messages from others
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                Log.d(TAG, "No messages from others to mark as read")
                return
            }
            
            // Use batch to update all unread messages
            val batch = firestore.batch()
            var updatedCount = 0
            
            snapshot.documents.forEach { doc ->
                val readBy = (doc.get("readBy") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
                
                // Only update if not already read
                if (userId !in readBy) {
                    batch.update(doc.reference, "readBy", FieldValue.arrayUnion(userId))
                    batch.update(doc.reference, "receivedBy", FieldValue.arrayUnion(userId))
                    updatedCount++
                }
            }
            
            if (updatedCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Marked $updatedCount messages as read and received in $conversationId")
            } else {
                Log.d(TAG, "No unread messages to mark in $conversationId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all messages as read in $conversationId", e)
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

