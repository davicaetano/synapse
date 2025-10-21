package com.synapse.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val googleIdOption: GetGoogleIdOption
) {
    private val _authState: MutableStateFlow<AuthState> = MutableStateFlow(
        if (auth.currentUser != null) AuthState.SignedIn(auth.currentUser?.email) else AuthState.SignedOut
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val u = firebaseAuth.currentUser
        _authState.value = if (u != null) AuthState.SignedIn(u.email) else AuthState.SignedOut
    }

    init {
        auth.addAuthStateListener(listener)
    }

    fun signOut() { auth.signOut() }
    fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { onComplete(it.isSuccessful) }
    }
    suspend fun requestGoogleIdToken(activity: Activity): String? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(activity, request)
        val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
        return googleCred.idToken
    }
}


