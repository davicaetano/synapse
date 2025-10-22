package com.synapse.data.source.realtime

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.synapse.data.source.realtime.entity.PresenceEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    
    // ============================================================
    // READ OPERATIONS
    // ============================================================
    
    /**
     * Listen to a single user's presence in real-time.
     */
    fun listenUserPresence(userId: String): Flow<PresenceEntity> = callbackFlow {
        val ref = realtimeDb.child("presence").child(userId)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeenMs = snapshot.child("lastSeenMs").getValue(Long::class.java)
                
                // Safe send - only send if channel is not closed
                try {
                    trySend(PresenceEntity(online = online, lastSeenMs = lastSeenMs)).isSuccess
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send presence update for $userId", e)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Presence listener cancelled for $userId, sending offline", error.toException())
                // Keep flow alive by sending offline status
                try {
                    trySend(PresenceEntity(online = false, lastSeenMs = System.currentTimeMillis())).isSuccess
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send offline presence for $userId", e)
                }
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
    
    /**
     * Listen to multiple users' presence in real-time.
     * Useful for inbox screens where you need presence for many users.
     * 
     * Returns a Map<userId, PresenceEntity> that updates whenever any user's presence changes.
     */
    fun listenMultiplePresence(userIds: List<String>): Flow<Map<String, PresenceEntity>> = callbackFlow {
        if (userIds.isEmpty()) {
            trySend(emptyMap())
            awaitClose {}
            return@callbackFlow
        }
        
        val listeners = mutableMapOf<String, ValueEventListener>()
        val presenceMap = mutableMapOf<String, PresenceEntity>()
        
        userIds.forEach { userId ->
            val ref = realtimeDb.child("presence").child(userId)
            
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                    val lastSeenMs = snapshot.child("lastSeenMs").getValue(Long::class.java)
                    
                    presenceMap[userId] = PresenceEntity(online = online, lastSeenMs = lastSeenMs)
                    
                    // Safe send - only send if channel is not closed
                    try {
                        trySend(presenceMap.toMap()).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send presence update for $userId", e)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Presence listener cancelled for $userId, sending current map", error.toException())
                    // Keep flow alive by sending current presence map
                    try {
                        trySend(presenceMap.toMap()).isSuccess
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send presence map after cancellation", e)
                    }
                }
            }
            
            listeners[userId] = listener
            ref.addValueEventListener(listener)
        }
        
        awaitClose {
            listeners.forEach { (userId, listener) ->
                realtimeDb.child("presence").child(userId).removeEventListener(listener)
            }
        }
    }
    
    // ============================================================
    // WRITE OPERATIONS
    // ============================================================
    
    /**
     * Mark the current user as online.
     * Also sets up automatic offline marking via onDisconnect() handler.
     * 
     * This is the magic of Realtime Database: even if the app crashes or loses connection,
     * the server will automatically mark the user offline.
     */
    suspend fun markOnline() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot mark online: user not authenticated")
            return
        }
        
        val presenceRef = realtimeDb.child("presence").child(userId)
        val presenceData = mapOf(
            "online" to true,
            "lastSeenMs" to System.currentTimeMillis()
        )
        
        presenceRef.setValue(presenceData)
            .addOnSuccessListener {
                Log.d(TAG, "Marked user $userId as online")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to mark user $userId as online", e)
            }
        
        // CRITICAL: Auto-disconnect handler
        // When the client disconnects (crash, force kill, network loss),
        // the server will automatically execute this write
        presenceRef.onDisconnect().setValue(
            mapOf(
                "online" to false,
                "lastSeenMs" to System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Mark the current user as offline.
     * Called explicitly when the user closes the app or logs out.
     */
    suspend fun markOffline() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot mark offline: user not authenticated")
            return
        }
        
        val presenceRef = realtimeDb.child("presence").child(userId)
        val presenceData = mapOf(
            "online" to false,
            "lastSeenMs" to System.currentTimeMillis()
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
     * Remove presence data for a user.
     * Usually not needed, but provided for cleanup/testing.
     */
    suspend fun removePresence(userId: String) {
        val presenceRef = realtimeDb.child("presence").child(userId)
        
        presenceRef.removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Removed presence for user $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove presence for user $userId", e)
            }
    }
    
    // ============================================================
    // TYPING INDICATOR OPERATIONS
    // ============================================================
    // Path: /typing/{conversationId}/{userId} = timestamp
    
    /**
     * Mark current user as typing in a conversation.
     * Sets a timestamp that can be used for auto-cleanup (e.g., remove if > 3 seconds old).
     */
    suspend fun setTyping(conversationId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("TYPING_DEBUG", "Cannot set typing: user not authenticated")
            return
        }
        
        val typingRef = realtimeDb.child("typing").child(conversationId).child(userId)
        val timestamp = System.currentTimeMillis()
        
        Log.d("TYPING_DEBUG", "RealtimeDB.setTyping: path=/typing/$conversationId/$userId timestamp=$timestamp")
        
        typingRef.setValue(timestamp)
            .addOnSuccessListener {
                Log.d("TYPING_DEBUG", "RealtimeDB.setTyping SUCCESS: userId=$userId convId=$conversationId")
            }
            .addOnFailureListener { e ->
                Log.e("TYPING_DEBUG", "RealtimeDB.setTyping FAILED: userId=$userId convId=$conversationId", e)
            }
        
        // Auto-remove after disconnect
        typingRef.onDisconnect().removeValue()
    }
    
    /**
     * Remove typing indicator for current user in a conversation.
     */
    suspend fun removeTyping(conversationId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot remove typing: user not authenticated")
            return
        }
        
        val typingRef = realtimeDb.child("typing").child(conversationId).child(userId)
        
        typingRef.removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Removed typing for user $userId in $conversationId")
            }
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
        
        Log.d("TYPING_DEBUG", "listenTypingInConversation: Setting up listener for convId=$conversationId path=/typing/$conversationId")
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("TYPING_DEBUG", "listenTypingInConversation.onDataChange: convId=$conversationId childrenCount=${snapshot.childrenCount}")
                
                val typingUsers = mutableMapOf<String, Long>()
                
                snapshot.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key
                    val timestamp = userSnapshot.getValue(Long::class.java)
                    
                    Log.d("TYPING_DEBUG", "listenTypingInConversation: userId=$userId timestamp=$timestamp")
                    
                    if (userId != null && timestamp != null) {
                        // Only include if timestamp is recent (< 5 seconds old)
                        // This handles stale data from network issues
                        val age = System.currentTimeMillis() - timestamp
                        Log.d("TYPING_DEBUG", "listenTypingInConversation: age=${age}ms (threshold=5000ms)")
                        if (age < 5000) {
                            typingUsers[userId] = timestamp
                        } else {
                            Log.d("TYPING_DEBUG", "listenTypingInConversation: SKIPPING stale timestamp userId=$userId")
                        }
                    }
                }
                
                Log.d("TYPING_DEBUG", "listenTypingInConversation: Sending ${typingUsers.size} typing users to Flow")
                
                try {
                    trySend(typingUsers).isSuccess
                } catch (e: Exception) {
                    Log.e("TYPING_DEBUG", "Failed to send typing update for $conversationId", e)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("TYPING_DEBUG", "Typing listener CANCELLED for convId=$conversationId", error.toException())
                try {
                    trySend(emptyMap()).isSuccess
                } catch (e: Exception) {
                    Log.e("TYPING_DEBUG", "Failed to send empty typing map", e)
                }
            }
        }
        
        ref.addValueEventListener(listener)
        awaitClose { 
            Log.d("TYPING_DEBUG", "listenTypingInConversation: Removing listener for convId=$conversationId")
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
                    Log.e(TAG, "Typing listener cancelled for $conversationId", error.toException())
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

