package com.synapse.data.source.realtime

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.synapse.data.source.realtime.entity.PresenceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource for accessing the 'presence' Realtime Database.
 * Path: /presence/{userId}
 * 
 * RESPONSIBILITY: Raw CRUD operations on user presence only.
 * - No business logic
 * - No combining with other data sources
 * - Returns raw entities (not domain models)
 * 
 * This handles both reads (listening) and writes (marking online/offline).
 */
@Singleton
class RealtimePresenceDataSource @Inject constructor(
    private val realtimeDb: DatabaseReference,
    private val auth: FirebaseAuth
) {
    
    // Heartbeat job to update lastSeenMs periodically
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Global presence flow - listens to ALL users in /presence.
     * 
     * Uses callbackFlow + stateIn for proper lifecycle management:
     * - SharingStarted.Eagerly: starts immediately, stays active
     * - StateFlow: caches last value, provides instant data to new collectors
     * - awaitClose: guarantees cleanup when scope is cancelled
     * 
     * This single listener serves ALL observePresence() calls via filtering.
     */
    private val globalPresenceFlow: StateFlow<Map<String, PresenceEntity>> = callbackFlow {
        val presenceRef = realtimeDb.child("presence")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val presenceMap = mutableMapOf<String, PresenceEntity>()
                
                snapshot.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key ?: return@forEach
                    val online = userSnapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeenMs = userSnapshot.child("lastSeenMs").getValue(Long::class.java)
                    
                    presenceMap[userId] = PresenceEntity(online = online, lastSeenMs = lastSeenMs)
                }
                
                Log.d(TAG, "🌍 Global presence updated: ${presenceMap.size} users")
                trySend(presenceMap)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Global presence listener cancelled: ${error.message}")
            }
        }
        
        presenceRef.addValueEventListener(listener)
        Log.d(TAG, "🌍 Started global presence listener for ALL users")
        
        awaitClose {
            Log.d(TAG, "🌍 Removing global presence listener")
            presenceRef.removeEventListener(listener)
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )
    
    // ============================================================
    // READ OPERATIONS
    // ============================================================
    
    /**
     * Listen to multiple users' presence in real-time.
     * 
     * NOW USES GLOBAL LISTENER:
     * - No new listeners created (all data comes from global flow)
     * - Filters from globalPresenceFlow by userIds
     * - INSTANT response (data already in memory)
     * - Eliminates race conditions and glitches
     * 
     * Returns a Map<userId, PresenceEntity> that updates whenever any user's presence changes.
     */
    fun listenMultiplePresence(userIds: List<String>): Flow<Map<String, PresenceEntity>> {
        if (userIds.isEmpty()) {
            return callbackFlow {
                trySend(emptyMap())
                awaitClose {}
            }
        }
        
        // Filter global flow by requested userIds
        return globalPresenceFlow.map { allPresence ->
            allPresence.filterKeys { it in userIds }
        }
    }
    
    // ============================================================
    // WRITE OPERATIONS
    // ============================================================
    
    /**
     * Mark the current user as online.
     * Also sets up automatic offline marking via onDisconnect() handler.
     * 
     * This should be called when:
     * - App starts
     * - Network becomes available (detected by NetworkConnectivityMonitor)
     * 
     * The onDisconnect() handler serves as a fallback for:
     * - App crashes
     * - Force close
     * - Process kill
     */
    fun markOnline() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot mark online: user not authenticated")
            return
        }
        
        val presenceRef = realtimeDb.child("presence").child(userId)
        
        // Set online status
        presenceRef.setValue(
            mapOf(
                "online" to true,
                "lastSeenMs" to ServerValue.TIMESTAMP
            )
        ).addOnSuccessListener {

            // Start heartbeat to keep lastSeenMs fresh
            startHeartbeat()
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to mark user $userId as online", e)
        }
        
        // Set up onDisconnect handler as fallback for crashes/force close
        // NOTE: Firebase detects disconnect after ~30 seconds
        // For instant offline detection, use NetworkConnectivityMonitor
        presenceRef.onDisconnect().setValue(
            mapOf(
                "online" to false,
                "lastSeenMs" to ServerValue.TIMESTAMP
            )
        ).addOnSuccessListener {
            Log.d(TAG, "onDisconnect handler set for user $userId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to set onDisconnect handler", e)
        }
    }
    
    /**
     * Mark the current user as offline.
     * Called explicitly when the user closes the app or logs out.
     */
    fun markOffline() {
        // Stop heartbeat first
        stopHeartbeat()
        
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot mark offline: user not authenticated")
            return
        }
        
        val presenceRef = realtimeDb.child("presence").child(userId)
        val presenceData = mapOf(
            "online" to false,
            "lastSeenMs" to ServerValue.TIMESTAMP  // Use server timestamp
        )
        
        presenceRef.setValue(presenceData)
            .addOnSuccessListener {
                Log.d(TAG, "Marked user $userId as offline")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to mark user $userId as offline", e)
            }
    }
    
    /**
     * Start heartbeat to update lastSeenMs every 5 seconds.
     * This keeps the user's presence "fresh" so other clients can infer online status.
     * 
     * With 5s heartbeat + 15s threshold in EntityMapper, users will appear offline
     * within ~15-20 seconds of disconnecting (e.g., Airplane Mode).
     * 
     * Must be called when user goes online or network becomes available.
     */
    fun startHeartbeat() {
        // Stop any existing heartbeat
        stopHeartbeat()
        
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot start heartbeat: user not authenticated")
            return
        }

        heartbeatJob = scope.launch {
            while (true) {
                delay(5_000L) // 5 seconds heartbeat
                
                // Update online status and lastSeenMs to keep presence fresh
                val presenceRef = realtimeDb.child("presence").child(userId)
                presenceRef.updateChildren(
                    mapOf<String, Any>(
                        "online" to true,
                        "lastSeenMs" to ServerValue.TIMESTAMP
                    )
                ).addOnFailureListener { e ->
                    Log.e(TAG, "Heartbeat: failed to update presence", e)
                }
            }
        }
    }
    
    /**
     * Stop heartbeat updates.
     * Must be called when user goes offline or app stops.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    // ============================================================
    // TYPING INDICATOR OPERATIONS
    // ============================================================
    // Path: /typing/{conversationId}/{userId} = timestamp
    
    /**
     * Mark current user as typing in a conversation.
     * Sets a timestamp that can be used for auto-cleanup (e.g., remove if > 3 seconds old).
     */
    fun setTyping(conversationId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            return
        }
        
        val typingRef = realtimeDb.child("typing").child(conversationId).child(userId)
        val timestamp = System.currentTimeMillis()
        
        
        typingRef.setValue(timestamp)
            .addOnSuccessListener {
            }
            .addOnFailureListener { e ->
            }
        
        // Auto-remove after disconnect
        typingRef.onDisconnect().removeValue()
    }
    
    /**
     * Remove typing indicator for current user in a conversation.
     */
    fun removeTyping(conversationId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot remove typing: user not authenticated")
            return
        }
        
        val typingRef = realtimeDb.child("typing").child(conversationId).child(userId)
        
        typingRef.removeValue()
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove typing for $userId in $conversationId", e)
            }
    }
    
    /**
     * Listen to who is typing in a specific conversation.
     * Returns a map of userId -> timestamp for all users currently typing.
     * 
     * NOTE: This includes ALL users typing (including self), so the caller
     * should filter out the current user if needed.
     */
    fun listenTypingInConversation(conversationId: String): Flow<Map<String, Long>> = callbackFlow {
        val ref = realtimeDb.child("typing").child(conversationId)
        
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                
                val typingUsers = mutableMapOf<String, Long>()
                
                snapshot.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key
                    val timestamp = userSnapshot.getValue(Long::class.java)
                    
                    
                    if (userId != null && timestamp != null) {
                        // Only include if timestamp is recent (< 5 seconds old)
                        // This handles stale data from network issues
                        val age = System.currentTimeMillis() - timestamp
                        if (age < 5000) {
                            typingUsers[userId] = timestamp
                        } else {
                        }
                    }
                }
                
                
                try {
                    trySend(typingUsers).isSuccess
                } catch (e: Exception) {
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                try {
                    trySend(emptyMap()).isSuccess
                } catch (e: Exception) {
                }
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { 
            ref.removeEventListener(listener) 
        }
    }
    
    /**
     * Listen to typing status across multiple conversations.
     * Returns a map: conversationId -> map of userId -> timestamp
     * 
     * Useful for Inbox screen to show "typing..." for multiple chats.
     */
    fun listenTypingInMultipleConversations(conversationIds: List<String>): Flow<Map<String, Map<String, Long>>> = callbackFlow {
        if (conversationIds.isEmpty()) {
            trySend(emptyMap())
            awaitClose {}
            return@callbackFlow
        }
        
        val listeners = mutableMapOf<String, ValueEventListener>()
        val typingMap = mutableMapOf<String, Map<String, Long>>()
        
        conversationIds.forEach { conversationId ->
            val ref = realtimeDb.child("typing").child(conversationId)
            
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val typingUsers = mutableMapOf<String, Long>()
                    
                    snapshot.children.forEach { userSnapshot ->
                        val userId = userSnapshot.key
                        val timestamp = userSnapshot.getValue(Long::class.java)
                        
                        if (userId != null && timestamp != null) {
                            val age = System.currentTimeMillis() - timestamp
                            if (age < 5000) {
                                typingUsers[userId] = timestamp
                            }
                        }
                    }
                    
                    typingMap[conversationId] = typingUsers
                    
                    try {
                        trySend(typingMap.toMap()).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send typing map update", e)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    try {
                        trySend(typingMap.toMap()).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send typing map after cancellation", e)
                    }
                }
            }
            
            listeners[conversationId] = listener
            ref.addValueEventListener(listener)
        }
        
        awaitClose {
            listeners.forEach { (conversationId, listener) ->
                realtimeDb.child("typing").child(conversationId).removeEventListener(listener)
            }
        }
    }
    
    companion object {
        private const val TAG = "RealtimePresenceDS"
    }
}

