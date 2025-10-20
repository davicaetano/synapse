package com.synapse.auth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val email: String?) : AuthState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    fun currentState(): AuthState = if (repository.isSignedIn()) {
        AuthState.SignedIn(repository.userEmail())
    } else AuthState.SignedOut

    fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) =
        repository.signInWithIdToken(idToken, onComplete)

    fun signOut() = repository.signOut()

    fun signInIntent() = repository.getSignInIntent()
}


