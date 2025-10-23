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
                        notReceivedBy = (doc.get("notReceivedBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
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
        val userId = auth.currentUser?.uid
        if (userId == null) {
            return null
        }
        
        // notReceivedBy = all members except sender
        val notReceivedBy = memberIds.filter { it != userId }
        
        // memberIdsAtCreation = snapshot of all members at this moment
        val memberIdsAtCreation = memberIds
        
        val messageData = hashMapOf(
            "text" to text,
            "senderId" to userId,
            "createdAtMs" to System.currentTimeMillis(),
            "receivedBy" to listOf(userId),  // Sender has received their own message
            "readBy" to listOf(userId),      // Sender has read their own message
            "notReceivedBy" to notReceivedBy,  // All other members haven't received yet
            "memberIdsAtCreation" to memberIdsAtCreation,  // Snapshot of group members
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
     * Observe unread message count in a conversation for the current user.
     * 
     * FLOW STRATEGY:
     * 1. Emits 0 immediately (instant UI render)
     * 2. Listens to Firestore in real-time (snapshot listener)
     * 3. Emits updated count whenever messages change
     * 
     * A message is unread if:
     * - senderId != currentUserId (not my message)
     * - readBy does NOT contain currentUserId (I haven't read it)
     */
    fun observeUnreadMessageCount(conversationId: String): Flow<Int> = callbackFlow {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            send(0)
            close()
            return@callbackFlow
        }

        // 1️⃣ Send 0 immediately (instant UI)
        send(0)
        
        // 2️⃣ Listen to Firestore in real-time
        val listenerRegistration = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereNotEqualTo("senderId", userId)  // Not my messages
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to unread messages in $conversationId", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    // Count messages where readBy doesn't contain current user
                    val unreadCount = snapshot.documents.count { doc ->
                        val readBy = (doc.get("readBy") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?: emptyList()
                        userId !in readBy
                    }
                    
                    // 3️⃣ Send updated count
                    trySend(unreadCount)
                }
            }
        
        awaitClose {
            listenerRegistration.remove()
        }
    }
    
    /**
     * Observe ALL unreceived messages across ALL conversations for a user.
     * 
     * Uses collectionGroup to query across all conversations in a single query!
     * Returns a Map of conversationId → list of unreceived messageIds.
     * 
     * This is MUCH more efficient than creating separate listeners per conversation.
     */
    fun observeAllUnreceivedMessages(userId: String): Flow<Map<String, List<String>>> = callbackFlow {
        if (userId.isEmpty()) {
            send(emptyMap())
            close()
            return@callbackFlow
        }
        
        // Single listener for ALL unreceived messages across ALL conversations!
        val listener = firestore.collectionGroup("messages")
            .whereArrayContains("notReceivedBy", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error observing unreceived messages", error)
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                
                // Group messages by conversationId
                val messagesByConv = snapshot?.documents
                    ?.groupBy { doc ->
                        // Extract conversationId from path: conversations/{convId}/messages/{msgId}
                        doc.reference.parent.parent?.id ?: ""
                    }
                    ?.filterKeys { it.isNotEmpty() }
                    ?.mapValues { (_, docs) -> docs.map { it.id } }
                    ?: emptyMap()
                
                trySend(messagesByConv)
                Log.d(TAG, "✅ Unreceived messages: ${messagesByConv.size} conversations, ${messagesByConv.values.sumOf { it.size }} total messages")
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Mark specific messages as received by the current user.
     * 
     * Updates:
     * - Removes userId from notReceivedBy array
     * - Adds userId to receivedBy array
     * 
     * @param conversationId The conversation ID
     * @param messageIds List of message IDs to mark as received
     */
    suspend fun markMessagesAsReceived(conversationId: String, messageIds: List<String>) {
        val userId = auth.currentUser?.uid ?: return
        
        if (messageIds.isEmpty()) {
            // Nothing to mark!
            return
        }
        
        try {
            // Use batch to update all unreceived messages
            val batch = firestore.batch()
            
            messageIds.forEach { messageId ->
                val docRef = firestore.collection("conversations")
                    .document(conversationId)
                    .collection("messages")
                    .document(messageId)
                
                // Remove from notReceivedBy + Add to receivedBy
                batch.update(docRef, "notReceivedBy", FieldValue.arrayRemove(userId))
                batch.update(docRef, "receivedBy", FieldValue.arrayUnion(userId))
            }
            
            batch.commit().await()
            Log.d(TAG, "✅ Marked ${messageIds.size} messages as received in $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking messages as received in $conversationId", e)
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
        val userId = auth.currentUser?.uid ?: return
        
        if (messages.isEmpty()) return
        
        try {
            // notReceivedBy = all members except sender
            val notReceivedBy = memberIds.filter { it != userId }
            
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
                    "receivedBy" to listOf(userId), // Sender has received their own message
                    "readBy" to listOf(userId),     // Sender has read their own message
                    "notReceivedBy" to notReceivedBy,  // All other members haven't received yet
                    "memberIdsAtCreation" to memberIdsAtCreation,  // Snapshot of group members
                    "serverTimestamp" to FieldValue.serverTimestamp() // Server assigns actual timestamp
                )
                
                batch.set(messageRef, messageData)
            }
            
            // Commit all writes in a single transaction
            batch.commit().await()
            Log.d(TAG, "✅ Batch sent ${messages.size} messages to $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending batch messages to $conversationId", e)
        }
    }
    
    companion object {
        private const val TAG = "FirestoreMessageDS"
    }
}

