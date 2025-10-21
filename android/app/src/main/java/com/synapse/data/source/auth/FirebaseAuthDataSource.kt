package com.synapse.data.source.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource for Firebase Authentication operations.
 * 
 * RESPONSIBILITY: Raw auth operations only.
 * - No business logic
 * - No user profile creation (that's Repository responsibility)
 */
@Singleton
class FirebaseAuthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val googleIdOption: GetGoogleIdOption
) {
    
    /**
     * Get current user ID, or null if not authenticated.
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid
    
    /**
     * Check if user is authenticated.
     */
    fun isAuthenticated(): Boolean = auth.currentUser != null
    
    /**
     * Request Google ID token using Credential Manager.
     */
    suspend fun requestGoogleIdToken(activity: Activity): String? {
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            
            val result = credentialManager.getCredential(activity, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            googleCred.idToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Google ID token", e)
            null
        }
    }
    
    /**
     * Sign in with Google ID token.
     */
    suspend fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }
    
    /**
     * Sign out current user.
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Add auth state listener.
     */
    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.addAuthStateListener(listener)
    }
    
    /**
     * Remove auth state listener.
     */
    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
    }
    
    companion object {
        private const val TAG = "FirebaseAuthDS"
    }
}

