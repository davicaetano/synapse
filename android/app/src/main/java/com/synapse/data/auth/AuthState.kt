package com.synapse.data.auth

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val email: String?) : AuthState
}


