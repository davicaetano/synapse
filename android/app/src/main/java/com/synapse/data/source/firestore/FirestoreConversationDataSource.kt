package com.synapse.data.source.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.domain.conversation.ConversationType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource for accessing the 'conversations' Firestore collection.
 * 
 * RESPONSIBILITY: Raw CRUD operations on conversations only.
 * - No business logic
 * - No combining with other data sources
 * - Returns raw entities (not domain models)
 */
@Singleton
class FirestoreConversationDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    // ============================================================
    // READ OPERATIONS
    // ============================================================
    
    /**
     * Listen to all conversations where the user is a member.
     * Returns raw Firestore data without user details or presence.
     */
    fun listenConversations(userId: String): Flow<List<ConversationEntity>> = callbackFlow {
        val ref = firestore.collection("conversations")
            .whereArrayContains("memberIds", userId)
        
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to conversations, sending empty list", error)
                trySend(emptyList())  // Keep flow alive, will update when error resolves
                return@addSnapshotListener
            }
            
            val conversations = snapshot?.documents?.mapNotNull { doc ->
                try {
                    ConversationEntity(
                        id = doc.id,
                        memberIds = (doc.get("memberIds") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        convType = doc.getString("convType") ?: ConversationType.DIRECT.name,
                        lastMessageText = doc.getString("lastMessageText"),
                        updatedAtMs = doc.getLong("updatedAtMs") ?: 0L,
                        createdAtMs = doc.getLong("createdAtMs") ?: 0L,
                        groupName = doc.getString("groupName"),
                        createdBy = doc.getString("createdBy")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing conversation ${doc.id}", e)
                    null
                }
            } ?: emptyList()
            
            trySend(conversations)
        }
        
        awaitClose { registration.remove() }
    }
    
    /**
     * Listen to conversations filtered by type.
     */
    fun listenConversationsByType(
        userId: String, 
        convType: ConversationType
    ): Flow<List<ConversationEntity>> = callbackFlow {
        val ref = firestore.collection("conversations")
            .whereArrayContains("memberIds", userId)
            .whereEqualTo("convType", convType.name)
        
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to conversations by type, sending empty list", error)
                trySend(emptyList())  // Keep flow alive, will update when error resolves
                return@addSnapshotListener
            }
            
            val conversations = snapshot?.documents?.mapNotNull { doc ->
                try {
                    ConversationEntity(
                        id = doc.id,
                        memberIds = (doc.get("memberIds") as? List<*>)
                            ?.mapNotNull { it as? String } 
                            ?: emptyList(),
                        convType = doc.getString("convType") ?: convType.name,
                        lastMessageText = doc.getString("lastMessageText"),
                        updatedAtMs = doc.getLong("updatedAtMs") ?: 0L,
                        createdAtMs = doc.getLong("createdAtMs") ?: 0L,
                        groupName = doc.getString("groupName")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing conversation ${doc.id}", e)
                    null
                }
            } ?: emptyList()
            
            trySend(conversations)
        }
        
        awaitClose { registration.remove() }
    }
    
    /**
     * Get a single conversation by ID (one-time read, not a listener).
     */
    suspend fun getConversation(conversationId: String): ConversationEntity? {
        return try {
            val doc = firestore.collection("conversations")
                .document(conversationId)
                .get()
                .await()
            
            if (!doc.exists()) return null
            
            ConversationEntity(
                id = doc.id,
                memberIds = (doc.get("memberIds") as? List<*>)
                    ?.mapNotNull { it as? String } 
                    ?: emptyList(),
                convType = doc.getString("convType") ?: ConversationType.DIRECT.name,
                lastMessageText = doc.getString("lastMessageText"),
                updatedAtMs = doc.getLong("updatedAtMs") ?: 0L,
                createdAtMs = doc.getLong("createdAtMs") ?: 0L,
                groupName = doc.getString("groupName")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting conversation $conversationId", e)
            null
        }
    }
    
    // ============================================================
    // WRITE OPERATIONS
    // ============================================================
    
    /**
     * Create a direct (1-on-1) conversation.
     * Returns conversation ID if successful.
     */
    suspend fun createDirectConversation(userIds: List<String>): String? {
        if (userIds.size != 2) {
            Log.e(TAG, "Direct conversation must have exactly 2 users")
            return null
        }
        
        val sortedIds = userIds.sorted()
        
        // Check if already exists
        val existing = firestore.collection("conversations")
            .whereEqualTo("memberIds", sortedIds)
            .whereEqualTo("convType", ConversationType.DIRECT.name)
            .limit(1)
            .get()
            .await()
        
        if (!existing.isEmpty) {
            return existing.documents.first().id
        }
        
        // Create new
        val data = mapOf(
            "memberIds" to sortedIds,
            "convType" to ConversationType.DIRECT.name,
            "createdAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        
        return try {
            val docRef = firestore.collection("conversations").add(data).await()
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating direct conversation", e)
            null
        }
    }
    
    /**
     * Create a group conversation.
     * Returns conversation ID if successful.
     * 
     * @param memberIds List of user IDs (must include at least 1 member)
     * @param groupName Optional group name
     * @param createdBy User ID of the creator/admin
     */
    suspend fun createGroupConversation(
        memberIds: List<String>,
        groupName: String? = null,
        createdBy: String
    ): String? {
        if (memberIds.isEmpty()) {
            Log.e(TAG, "Group conversation must have at least 1 member")
            return null
        }
        
        val sortedIds = memberIds.sorted()
        
        // Check if already exists (by memberIds + convType)
        val existing = firestore.collection("conversations")
            .whereEqualTo("memberIds", sortedIds)
            .whereEqualTo("convType", ConversationType.GROUP.name)
            .limit(1)
            .get()
            .await()
        
        if (!existing.isEmpty) {
            return existing.documents.first().id
        }
        
        // Create new group
        val data = mutableMapOf<String, Any>(
            "memberIds" to sortedIds,
            "convType" to ConversationType.GROUP.name,
            "createdBy" to createdBy,  // Store creator as admin
            "createdAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        
        if (groupName != null) {
            data["groupName"] = groupName
        }
        
        return try {
            val docRef = firestore.collection("conversations").add(data).await()
            Log.d(TAG, "Group created by $createdBy with ${memberIds.size} members")
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group conversation", e)
            null
        }
    }
    
    /**
     * Create a self conversation (user talking to themselves / AI assistant).
     * Returns conversation ID if successful.
     */
    suspend fun createSelfConversation(userId: String): String? {
        // Check if already exists
        val existing = firestore.collection("conversations")
            .whereEqualTo("memberIds", listOf(userId))
            .whereEqualTo("convType", ConversationType.SELF.name)
            .limit(1)
            .get()
            .await()
        
        if (!existing.isEmpty) {
            return existing.documents.first().id
        }
        
        // Create new
        val data = mapOf(
            "memberIds" to listOf(userId),
            "convType" to ConversationType.SELF.name,
            "createdAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        
        return try {
            val docRef = firestore.collection("conversations").add(data).await()
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating self conversation", e)
            null
        }
    }
    
    /**
     * Update conversation metadata after sending a message.
     * Updates lastMessageText and updatedAtMs.
     * 
     * Uses set() with merge instead of update() to work better offline.
     * set() with merge will queue the update even if offline, while update() might fail.
     */
    suspend fun updateConversationMetadata(
        conversationId: String,
        lastMessageText: String,
        timestamp: Long
    ) {
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .set(
                    mapOf(
                        "lastMessageText" to lastMessageText,
                        "updatedAtMs" to timestamp
                    ),
                    SetOptions.merge()
                )
                .await()
            
            Log.d(TAG, "Updated conversation $conversationId metadata: lastMessageText='${lastMessageText.take(20)}...', timestamp=$timestamp")
        } catch (e: Exception) {
            // Log but don't fail - Firestore will cache this and sync when online
            Log.w(TAG, "Error updating conversation metadata (will retry when online)", e)
        }
    }
    
    /**
     * Add a user to a group conversation.
     */
    suspend fun addMemberToGroup(conversationId: String, userId: String) {
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "memberIds" to com.google.firebase.firestore.FieldValue.arrayUnion(userId),
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding member to group", e)
        }
    }
    
    /**
     * Remove a user from a group conversation.
     */
    suspend fun removeMemberFromGroup(conversationId: String, userId: String) {
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "memberIds" to com.google.firebase.firestore.FieldValue.arrayRemove(userId),
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing member from group", e)
        }
    }
    
    companion object {
        private const val TAG = "FirestoreConversationDS"
    }
}

