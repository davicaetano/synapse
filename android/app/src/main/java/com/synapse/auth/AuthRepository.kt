package com.synapse.auth

import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val gsiClient: GoogleSignInClient
) {
    fun isSignedIn(): Boolean = auth.currentUser != null
    fun userEmail(): String? = auth.currentUser?.email
    fun signOut() {
        auth.signOut()
        gsiClient.signOut()
    }
    fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { onComplete(it.isSuccessful) }
    }
    fun getSignInIntent() = gsiClient.signInIntent
}


