package com.synapse.data.presence

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PresenceManager"

/**
 * Manager for user presence operations.
 * 
 * NOW REACTIVE:
 * - Observes auth state changes (login/logout)
 * - Automatically calls markOnline() when user logs in
 * - Automatically calls markOffline() when user logs out
 * 
 * This solves the issue where presence wasn't working after login because
 * markOnline() was called before auth.currentUser was populated.
 */
@Singleton
class PresenceManager @Inject constructor(
    private val presenceDataSource: RealtimePresenceDataSource,
    private val auth: FirebaseAuth
) {
    // Application-level scope (survives Activity destruction)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Flow that emits the current user's UID whenever auth state changes.
     * Emits null when logged out, non-null UID when logged in.
     */
    private val authUserIdFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val userId = firebaseAuth.currentUser?.uid
            Log.d(TAG, "🔐 Auth state changed: userId=${userId?.takeLast(6) ?: "null"}")
            trySend(userId)
        }
        
        // Add listener
        auth.addAuthStateListener(listener)
        
        // Emit current state immediately
        trySend(auth.currentUser?.uid)
        
        awaitClose {
            Log.d(TAG, "🔐 Removing auth state listener")
            auth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged()  // Only emit when UID actually changes
    
    init {
        // Observe auth state changes and react to login/logout
        authUserIdFlow
            .onEach { userId ->
                if (userId != null) {
                    // User logged in → mark online
                    Log.d(TAG, "✅ User logged in (${userId.takeLast(6)}), marking online...")
                    presenceDataSource.markOnline()
                } else {
                    // User logged out → mark offline
                    Log.d(TAG, "❌ User logged out, marking offline...")
                    presenceDataSource.markOffline()
                }
            }
            .launchIn(scope)
    }
    
    /**
     * Manually mark user as online.
     * (Usually not needed - auth state observer handles this automatically)
     */
    fun markOnline() {
        scope.launch {
            presenceDataSource.markOnline()
        }
    }
    
    /**
     * Manually mark user as offline.
     * (Usually not needed - auth state observer handles this automatically)
     */
    fun markOffline() {
        scope.launch {
            presenceDataSource.markOffline()
        }
    }
}

