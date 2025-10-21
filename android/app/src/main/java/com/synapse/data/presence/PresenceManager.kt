package com.synapse.data.presence

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val realtimeDb: DatabaseReference
) {
    
    fun markOnline() {
        val uid = auth.currentUser?.uid ?: return
        val presenceRef = realtimeDb.child("presence").child(uid)
        
        val presenceData = mapOf(
            "online" to true,
            "lastSeenMs" to System.currentTimeMillis()
        )
        
        presenceRef.setValue(presenceData)
            .addOnSuccessListener {
                Log.d(TAG, "Marked user $uid as online")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to mark online", e)
            }
        
        // Auto-disconnect handler: marca offline quando cliente desconectar
        presenceRef.onDisconnect().setValue(
            mapOf(
                "online" to false,
                "lastSeenMs" to System.currentTimeMillis()
            )
        )
    }
    
    fun markOffline() {
        val uid = auth.currentUser?.uid ?: return
        val presenceRef = realtimeDb.child("presence").child(uid)
        
        presenceRef.setValue(
            mapOf(
                "online" to false,
                "lastSeenMs" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            Log.d(TAG, "Marked user $uid as offline")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to mark offline", e)
        }
    }
    
    companion object {
        private const val TAG = "PresenceManager"
    }
}

