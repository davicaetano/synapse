package com.synapse.auth

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val googleIdOption: GetGoogleIdOption
) {
    fun isSignedIn(): Boolean = auth.currentUser != null
    fun userEmail(): String? = auth.currentUser?.email
    fun signOut() { auth.signOut() }
    fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { onComplete(it.isSuccessful) }
    }
    suspend fun requestGoogleIdToken(activity: android.app.Activity): String? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(activity, request)
        val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
        return googleCred.idToken
    }
}


