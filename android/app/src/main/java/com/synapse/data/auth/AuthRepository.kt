package com.synapse.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.source.auth.FirebaseAuthDataSource
import com.synapse.data.presence.PresenceManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for authentication operations.
 * Now delegates to FirebaseAuthDataSource for auth operations.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val auth: FirebaseAuth,  // Still needed for listener
    private val presenceManager: PresenceManager
) {
    private val _authState: MutableStateFlow<AuthState> = MutableStateFlow(
        if (authDataSource.isAuthenticated()) {
            AuthState.SignedIn(auth.currentUser?.email)
        } else {
            AuthState.SignedOut
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val u = firebaseAuth.currentUser
        _authState.value = if (u != null) AuthState.SignedIn(u.email) else AuthState.SignedOut
    }

    init {
        authDataSource.addAuthStateListener(listener)
    }

    suspend fun signOut() {
        // Mark user as offline before signing out
        presenceManager.markOffline()
        authDataSource.signOut()
    }
    
    suspend fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) {
        authDataSource.signInWithIdToken(idToken, onComplete)
    }
    
    suspend fun requestGoogleIdToken(activity: Activity): String? {
        return authDataSource.requestGoogleIdToken(activity)
    }
}


