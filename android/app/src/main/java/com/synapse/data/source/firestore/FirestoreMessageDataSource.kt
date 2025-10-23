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
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ listenMessages START: $conversationId")
        
        val ref = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAtMs")
        
        var firstEmission = true
        val registration = ref.addSnapshotListener { snapshot, error ->
            val isFromCache = snapshot?.metadata?.isFromCache ?: false
            
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
                        memberIdsAtCreation = (doc.get("memberIdsAtCreation") as? List<*>)
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
    
    // ============================================================
    // WRITE OPERATIONS
    // ============================================================
    
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
            "serverTimestamp" to FieldValue.serverTimestamp()  // Server assigns actual timestamp
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
                    "serverTimestamp" to FieldValue.serverTimestamp() // Server assigns actual timestamp
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
    
    companion object {
        private const val TAG = "FirestoreMessageDS"
    }
}

