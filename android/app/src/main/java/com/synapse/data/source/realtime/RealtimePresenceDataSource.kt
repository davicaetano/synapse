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
                Log.e(TAG, "Presence listener cancelled for $userId", error.toException())
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
                    Log.e(TAG, "Presence listener cancelled for $userId", error.toException())
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
    
    companion object {
        private const val TAG = "RealtimePresenceDS"
    }
}

