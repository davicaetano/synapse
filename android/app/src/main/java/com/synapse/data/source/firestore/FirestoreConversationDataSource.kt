package com.synapse.data.source.firestore

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.Member
import com.synapse.domain.conversation.ConversationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    
    // Application-level scope (survives entire app lifecycle)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Global conversations flow - listens to ALL user's conversations ONCE.
     * 
     * Uses callbackFlow + stateIn for proper lifecycle management:
     * - SharingStarted.Eagerly: starts immediately, stays active
     * - StateFlow: caches last value, provides instant data to new collectors
     * - awaitClose: guarantees cleanup when scope is cancelled
     * 
     * This single listener serves ALL observeConversations() calls.
     * Query is already filtered (only user's conversations via memberIds).
     */
    private val globalConversationsFlow: StateFlow<Map<String, ConversationEntity>> = callbackFlow {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            Log.w(TAG, "🌍 Cannot start global conversations listener: user not authenticated")
            trySend(emptyMap())
            awaitClose {}
            return@callbackFlow
        }
        
        val query = firestore.collection("conversations")
            .whereArrayContains("memberIds", userId)
        
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "❌ Global conversations listener error: ${error.message}")
                return@addSnapshotListener
            }
            
            val conversationsMap = snapshot?.documents
                ?.mapNotNull { doc ->
                    try {
                        parseConversationEntity(doc)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing conversation ${doc.id}", e)
                        null
                    }
                }
                ?.associateBy { it.id }
                ?: emptyMap()
            
            Log.d(TAG, "🌍 Global conversations updated: ${conversationsMap.size} conversations")
            trySend(conversationsMap)
        }
        
        Log.d(TAG, "🌍 Started global conversations listener for user $userId")
        
        awaitClose {
            Log.d(TAG, "🌍 Removing global conversations listener")
            listener.remove()
        }
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )
    
    // ============================================================
    // READ OPERATIONS
    // ============================================================
    
    /**
     * Listen to all conversations where the user is a member.
     * 
     * NOW USES GLOBAL LISTENER:
     * - No new listeners created (all data comes from global flow)
     * - Filters from globalConversationsFlow (already filtered by userId in query)
     * - INSTANT response (data already in memory)
     * - Eliminates race conditions and repeated listener creation
     * 
     * Returns raw Firestore data without user details or presence.
     */
    fun listenConversations(
        userId: String,
        includesCacheChanges: Boolean = true
    ): Flow<List<ConversationEntity>> {
        // Simply map the global flow to a list
        // Global flow is already filtered by current user's memberIds
        return globalConversationsFlow.map { conversationsMap ->
            conversationsMap.values.toList()
        }
    }
    
    /**
     * Listen to a single conversation by ID.
     * 
     * NOW USES GLOBAL LISTENER:
     * - Filters from globalConversationsFlow by conversationId
     * - INSTANT response (data already in memory)
     * - No additional listener created
     */
    fun listenConversation(
        conversationId: String,
        includesCacheChanges: Boolean = true
    ): Flow<ConversationEntity?> {
        // Simply filter the global flow by ID
        return globalConversationsFlow.map { conversationsMap ->
            conversationsMap[conversationId]
        }
    }
    
    /**
     * Parse ConversationEntity from Firestore document.
     * Handles members map parsing.
     */
    private fun parseConversationEntity(doc: DocumentSnapshot): ConversationEntity {
        // Parse members map
        val membersRaw = doc.get("members") as? Map<*, *>
        val members = membersRaw?.mapNotNull { (key, value) ->
            val userId = key as? String ?: return@mapNotNull null
            val memberMap = value as? Map<*, *> ?: return@mapNotNull null
            
            val lastSeenAt = memberMap["lastSeenAt"] as? Timestamp ?: return@mapNotNull null
            val lastReceivedAt = memberMap["lastReceivedAt"] as? Timestamp ?: return@mapNotNull null
            val lastMessageSentAt = memberMap["lastMessageSentAt"] as? Timestamp ?: return@mapNotNull null
            val isBot = memberMap["isBot"] as? Boolean ?: false
            val isAdmin = memberMap["isAdmin"] as? Boolean ?: false
            val isDeleted = memberMap["isDeleted"] as? Boolean ?: false
            
            userId to Member(
                lastSeenAt = lastSeenAt,
                lastReceivedAt = lastReceivedAt,
                lastMessageSentAt = lastMessageSentAt,
                isBot = isBot,
                isAdmin = isAdmin,
                isDeleted = isDeleted
            )
        }?.toMap() ?: emptyMap()
        
        return ConversationEntity(
            id = doc.id,
            convType = doc.getString("convType") ?: ConversationType.DIRECT.name,
            localTimestamp = doc.getTimestamp("localTimestamp") ?: Timestamp.now(),
            updatedAt = doc.getTimestamp("updatedAt") ?: Timestamp.now(),
            lastMessageText = doc.getString("lastMessageText") ?: "",
            groupName = doc.getString("groupName"),
            createdBy = doc.getString("createdBy"),
            memberIds = (doc.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            members = members
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
        val now = Timestamp.now()
        val members = sortedIds.associate { userId ->
            userId to mapOf(
                "lastSeenAt" to now,
                "lastReceivedAt" to now,
                "lastMessageSentAt" to now,
                "isBot" to false,
                "isAdmin" to false,
                "isDeleted" to false
            )
        }
        
        val data = mapOf(
            "convType" to ConversationType.DIRECT.name,
            "localTimestamp" to now,
            "updatedAt" to now,
            "lastMessageText" to "",
            "memberIds" to sortedIds,  // Pre-populate for instant inbox visibility
            "members" to members
            // NOTE: memberIds is kept in sync by Cloud Function after creation
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
        val now = Timestamp.now()
        val members = sortedIds.associate { userId ->
            userId to mapOf(
                "lastSeenAt" to now,
                "lastReceivedAt" to now,
                "lastMessageSentAt" to now,
                "isBot" to false,
                "isAdmin" to (userId == createdBy),  // Creator is admin
                "isDeleted" to false
            )
        }
        
        val data = mutableMapOf<String, Any>(
            "convType" to ConversationType.GROUP.name,
            "createdBy" to createdBy,
            "localTimestamp" to now,
            "updatedAt" to now,
            "lastMessageText" to "",
            "memberIds" to sortedIds,  // Pre-populate for instant inbox visibility
            "members" to members
            // NOTE: memberIds is kept in sync by Cloud Function after creation
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
        val now = Timestamp.now()
        val members = mapOf(
            userId to mapOf(
                "lastSeenAt" to now,
                "lastReceivedAt" to now,
                "lastMessageSentAt" to now,
                "isBot" to false,
                "isAdmin" to true,  // Self conversation - user is admin
                "isDeleted" to false
            )
        )
        
        val data = mapOf(
            "convType" to ConversationType.SELF.name,
            "localTimestamp" to now,
            "updatedAt" to now,
            "lastMessageText" to "",
            "memberIds" to listOf(userId),  // Pre-populate for instant inbox visibility
            "members" to members
            // NOTE: memberIds is kept in sync by Cloud Function after creation
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
        timestamp: Timestamp
    ) {
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .set(
                    mapOf(
                        "lastMessageText" to lastMessageText,
                        "updatedAt" to timestamp
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
    suspend fun addMemberToGroup(conversationId: String, userId: String, isAdmin: Boolean = false) {
        try {
            val now = Timestamp.now()
            firestore.collection("conversations")
                .document(conversationId)
                .set(
                    mapOf(
                        "members" to mapOf(
                            userId to mapOf(
                                "lastSeenAt" to now,
                                "lastReceivedAt" to now,
                                "lastMessageSentAt" to now,
                                "isBot" to false,
                                "isAdmin" to isAdmin,
                                "isDeleted" to false
                            )
                        ),
                        "updatedAt" to now
                        // NOTE: memberIds is auto-synced by Cloud Function
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding member to group", e)
        }
    }
    
    /**
     * Remove a user from a group conversation (soft delete).
     */
    suspend fun removeMemberFromGroup(conversationId: String, userId: String) {
        try {
            firestore.collection("conversations")
                .document(conversationId)
                .set(
                    mapOf(
                        "members" to mapOf(
                            userId to mapOf(
                                "isDeleted" to true
                            )
                        ),
                        "updatedAt" to Timestamp.now()
                    ),
                    SetOptions.merge()
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
                .set(
                    mapOf(
                        "groupName" to groupName,
                        "updatedAt" to Timestamp.now()
                    ),
                    SetOptions.merge()
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
                        "members" to mapOf(
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
                        "members" to mapOf(
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
                        "members" to mapOf(
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

