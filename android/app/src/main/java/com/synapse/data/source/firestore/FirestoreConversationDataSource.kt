package com.synapse.data.source.firestore

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.MemberStatus
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
            val isFromCache = snapshot?.metadata?.isFromCache ?: false
            val hasPendingWrites = snapshot?.metadata?.hasPendingWrites() ?: false
            
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            val conversations = snapshot?.documents?.mapNotNull { doc ->
                try {
                    parseConversationEntity(doc)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            
            conversations.forEach { conv ->
            }
            
            trySend(conversations)
        }
        
        awaitClose { 
            registration.remove()
        }
    }
    
    /**
     * Listen to a single conversation by ID.
     * More efficient than listening to all conversations and filtering.
     */
    fun listenConversation(conversationId: String): Flow<ConversationEntity?> = callbackFlow {
        val registration = firestore.collection("conversations")
            .document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val conversation = snapshot?.let { doc ->
                    try {
                        parseConversationEntity(doc)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                trySend(conversation)
            }
        
        awaitClose { registration.remove() }
    }
    
    /**
     * Parse ConversationEntity from Firestore document.
     * Handles memberStatus map parsing.
     */
    private fun parseConversationEntity(doc: com.google.firebase.firestore.DocumentSnapshot): ConversationEntity {
        // Parse memberStatus map
        val memberStatusRaw = doc.get("memberStatus") as? Map<*, *>
        val memberStatus = memberStatusRaw?.mapNotNull { (key, value) ->
            val userId = key as? String ?: return@mapNotNull null
            val statusMap = value as? Map<*, *> ?: return@mapNotNull null
            
            val lastSeenAt = statusMap["lastSeenAt"] as? Timestamp
            val lastReceivedAt = statusMap["lastReceivedAt"] as? Timestamp
            val lastMessageSentAt = statusMap["lastMessageSentAt"] as? Timestamp
            
            userId to MemberStatus(
                lastSeenAt = lastSeenAt,
                lastReceivedAt = lastReceivedAt,
                lastMessageSentAt = lastMessageSentAt
            )
        }?.toMap() ?: emptyMap()
        
        return ConversationEntity(
            id = doc.id,
            memberIds = (doc.get("memberIds") as? List<*>)
                ?.mapNotNull { it as? String } 
                ?: emptyList(),
            convType = doc.getString("convType") ?: ConversationType.DIRECT.name,
            lastMessageText = doc.getString("lastMessageText"),
            updatedAtMs = doc.getLong("updatedAtMs") ?: 0L,
            createdAtMs = doc.getLong("createdAtMs") ?: 0L,
            groupName = doc.getString("groupName"),
            createdBy = doc.getString("createdBy"),
            memberStatus = memberStatus
        )
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
        
        // Always create new group (allow multiple groups with same members)
        // Each group is unique by ID - useful for different topics/projects
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
            
        } catch (e: Exception) {
            // Log but don't fail - Firestore will cache this and sync when online
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
                        "memberIds" to FieldValue.arrayUnion(userId),
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
                        "memberIds" to FieldValue.arrayRemove(userId),
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing member from group", e)
        }
    }
    
    /**
     * Update group conversation name.
     */
    suspend fun updateGroupName(conversationId: String, groupName: String) {
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "groupName" to groupName,
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                )
                .await()
            Log.d(TAG, "Group name updated to: $groupName")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group name", e)
        }
    }
    
    /**
     * Update member's lastSeenAt to NOW (when user opens conversation).
     * Uses FieldValue.serverTimestamp() for current server time.
     * 
     * @param conversationId Conversation ID
     */
    suspend fun updateMemberLastSeenAtNow(conversationId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .set(
                    mapOf(
                        "memberStatus" to mapOf(
                            userId to mapOf(
                                "lastSeenAt" to FieldValue.serverTimestamp()
                            )
                        )
                    ),
                    SetOptions.merge()  // Merge with existing data - creates field if doesn't exist
                )
                .await()
            
            Log.d(TAG, "✅ Updated lastSeenAt to NOW for user $userId in conversation $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating lastSeenAt", e)
        }
    }
    
    /**
     * Update member's lastReceivedAt timestamp (when user receives messages).
     * Uses server timestamp for accuracy.
     * 
     * @param conversationId Conversation ID
     * @param serverTimestamp Server timestamp from the most recent message
     */
    suspend fun updateMemberLastReceivedAt(conversationId: String, serverTimestamp: Timestamp) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .set(
                    mapOf(
                        "memberStatus" to mapOf(
                            userId to mapOf(
                                "lastReceivedAt" to serverTimestamp
                            )
                        )
                    ),
                    SetOptions.merge()  // Merge with existing data - creates field if doesn't exist
                )
                .await()
            
            Log.d(TAG, "✅ Updated lastReceivedAt for user $userId in conversation $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating lastReceivedAt", e)
        }
    }
    
    /**
     * Update member's lastMessageSentAt to NOW (when user sends a message).
     * Uses FieldValue.serverTimestamp() for current server time.
     * 
     * @param conversationId Conversation ID
     */
    suspend fun updateMemberLastMessageSentAtNow(conversationId: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .set(
                    mapOf(
                        "memberStatus" to mapOf(
                            userId to mapOf(
                                "lastMessageSentAt" to FieldValue.serverTimestamp()
                            )
                        )
                    ),
                    SetOptions.merge()
                )
                .await()
            
            Log.d(TAG, "✅ Updated lastMessageSentAt to NOW for user $userId in conversation $conversationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating lastMessageSentAt", e)
        }
    }
    
    companion object {
        private const val TAG = "FirestoreConversationDS"
    }
}

