package com.synapse.data.source.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.synapse.data.source.IMessageDataSource
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
) : IMessageDataSource {
    
    // ============================================================
    // READ OPERATIONS
    // ============================================================
    
    /**
     * Listen to all messages in a conversation, ordered by creation time.
     * Returns raw Firestore data.
     */
    override fun listenMessages(conversationId: String): Flow<List<MessageEntity>> = callbackFlow {
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
                        receivedBy = (doc.get("receivedBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        readBy = (doc.get("readBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        notReceivedBy = (doc.get("notReceivedBy") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        notReadBy = (doc.get("notReadBy") as? List<*>)
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
    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        memberIds: List<String>
    ): String? {
        val startTime = System.currentTimeMillis()
        val userId = auth.currentUser?.uid
        if (userId == null) {
            return null
        }
        
        // notReceivedBy = all members except sender
        val notReceivedBy = memberIds.filter { it != userId }
        
        // notReadBy = all members except sender (same as notReceivedBy initially)
        val notReadBy = memberIds.filter { it != userId }
        
        // memberIdsAtCreation = snapshot of all members at this moment
        val memberIdsAtCreation = memberIds
        
        val messageData = hashMapOf(
            "text" to text,
            "senderId" to userId,
            "createdAtMs" to System.currentTimeMillis(),
            "receivedBy" to listOf(userId),  // Sender has received their own message
            "readBy" to listOf(userId),      // Sender has read their own message
            "notReceivedBy" to notReceivedBy,  // All other members haven't received yet
            "notReadBy" to notReadBy,  // All other members haven't read yet
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
     * Observe unread message counts across ALL conversations for a user.
     * 
     * Uses collectionGroup to query across all conversations in a single query!
     * Returns a Map of conversationId → unread count.
     * 
     * This is MUCH more efficient than creating separate listeners per conversation.
     */
    override fun observeAllUnreadCounts(userId: String): Flow<Map<String, Int>> = callbackFlow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ observeAllUnreadCounts START")
        
        if (userId.isEmpty()) {
            send(emptyMap())
            close()
            return@callbackFlow
        }
        
        // Emit empty map immediately so UI doesn't wait for Firestore
        send(emptyMap())
        
        var firstEmission = true
        // Single listener for ALL unread messages across ALL conversations!
        val listener = firestore.collectionGroup("messages")
            .whereArrayContains("notReadBy", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error observing unread counts", error)
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                
                // Group messages by conversationId and COUNT them
                val countsByConv = snapshot?.documents
                    ?.groupBy { doc ->
                        // Extract conversationId from path: conversations/{convId}/messages/{msgId}
                        doc.reference.parent.parent?.id ?: ""
                    }
                    ?.filterKeys { it.isNotEmpty() }
                    ?.mapValues { (_, docs) -> docs.size }  // Just count the docs!
                    ?: emptyMap()
                
                if (firstEmission) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⏱️ observeAllUnreadCounts FIRST EMIT: ${countsByConv.size} convs, ${countsByConv.values.sum()} total unread in ${elapsed}ms")
                    firstEmission = false
                }
                
                trySend(countsByConv)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Observe ALL unreceived messages across ALL conversations for a user.
     * 
     * Uses collectionGroup to query across all conversations in a single query!
     * Returns a Map of conversationId → list of unreceived messageIds.
     * 
     * This is MUCH more efficient than creating separate listeners per conversation.
     */
    override fun observeAllUnreceivedMessages(userId: String): Flow<Map<String, List<String>>> = callbackFlow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ observeAllUnreceivedMessages START")
        
        if (userId.isEmpty()) {
            send(emptyMap())
            close()
            return@callbackFlow
        }
        
        // Emit empty map immediately so UI doesn't wait for Firestore
        send(emptyMap())
        
        var firstEmission = true
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
                
                if (firstEmission) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⏱️ observeAllUnreceivedMessages FIRST EMIT: ${messagesByConv.size} convs, ${messagesByConv.values.sumOf { it.size }} msgs in ${elapsed}ms")
                    firstEmission = false
                }
                
                trySend(messagesByConv)
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
    override suspend fun markMessagesAsReceived(conversationId: String, messageIds: List<String>) {
        val startTime = System.currentTimeMillis()
        val userId = auth.currentUser?.uid ?: return
        
        if (messageIds.isEmpty()) {
            // Nothing to mark!
            return
        }
        
        try {
            // Firestore batch limit: 500 operations
            // We do 2 updates per message, so max 250 messages per batch
            val BATCH_SIZE = 250
            
            messageIds.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                
                chunk.forEach { messageId ->
                    val docRef = firestore.collection("conversations")
                        .document(conversationId)
                        .collection("messages")
                        .document(messageId)
                    
                    // Remove from notReceivedBy + Add to receivedBy
                    batch.update(docRef, "notReceivedBy", FieldValue.arrayRemove(userId))
                    batch.update(docRef, "receivedBy", FieldValue.arrayUnion(userId))
                }
                
                batch.commit().await()
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ markMessagesAsReceived: ${messageIds.size} msgs in ${elapsed}ms")
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "⏱️ markMessagesAsReceived FAILED: ${elapsed}ms", e)
        }
    }
    
    /**
     * Observe unread messages for a specific conversation.
     * 
     * Uses the NEW notReadBy field for efficient querying.
     * Only fetches messages where notReadBy contains the current user.
     * 
     * @return Flow of message IDs that haven't been read yet
     */
    override fun observeUnreadMessages(conversationId: String): Flow<List<String>> = callbackFlow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ observeUnreadMessages START: $conversationId")
        
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            send(emptyList())
            close()
            return@callbackFlow
        }
        
        var firstEmission = true
        // Listen to messages where notReadBy contains my userId
        val listener = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereArrayContains("notReadBy", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Error observing unread messages in $conversationId", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val messageIds = snapshot?.documents?.map { it.id } ?: emptyList()
                
                if (firstEmission) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⏱️ observeUnreadMessages FIRST EMIT: $conversationId - ${messageIds.size} msgs in ${elapsed}ms")
                    firstEmission = false
                }
                
                trySend(messageIds)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Mark specific messages as read by the current user.
     * 
     * Updates:
     * - Removes userId from notReadBy array
     * - Adds userId to readBy array
     * - Also marks as received (can't read without receiving)
     * 
     * @param conversationId The conversation ID
     * @param messageIds List of message IDs to mark as read
     */
    override suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>) {
        val startTime = System.currentTimeMillis()
        val userId = auth.currentUser?.uid ?: return
        
        if (messageIds.isEmpty()) {
            // Nothing to mark!
            return
        }
        
        try {
            // Firestore batch limit: 500 operations
            // We do 4 updates per message, so max 125 messages per batch
            val BATCH_SIZE = 125
            
            messageIds.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                
                chunk.forEach { messageId ->
                    val docRef = firestore.collection("conversations")
                        .document(conversationId)
                        .collection("messages")
                        .document(messageId)
                    
                    // Remove from notReadBy + Add to readBy
                    batch.update(docRef, "notReadBy", FieldValue.arrayRemove(userId))
                    batch.update(docRef, "readBy", FieldValue.arrayUnion(userId))
                    // Also mark as received (can't read without receiving first)
                    batch.update(docRef, "notReceivedBy", FieldValue.arrayRemove(userId))
                    batch.update(docRef, "receivedBy", FieldValue.arrayUnion(userId))
                }
                
                batch.commit().await()
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ markMessagesAsRead: ${messageIds.size} msgs in ${elapsed}ms")
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "⏱️ markMessagesAsRead FAILED: ${elapsed}ms", e)
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
    override suspend fun sendMessagesBatch(conversationId: String, messages: List<String>, memberIds: List<String>) {
        val startTime = System.currentTimeMillis()
        val userId = auth.currentUser?.uid ?: return
        
        if (messages.isEmpty()) return
        
        Log.d(TAG, "⏱️ sendMessagesBatch START: ${messages.size} messages")
        
        try {
            // notReceivedBy = all members except sender
            val notReceivedBy = memberIds.filter { it != userId }
            
            // notReadBy = all members except sender (same as notReceivedBy initially)
            val notReadBy = memberIds.filter { it != userId }
            
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
                    "notReadBy" to notReadBy,  // All other members haven't read yet
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

