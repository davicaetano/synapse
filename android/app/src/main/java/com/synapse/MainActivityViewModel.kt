package com.synapse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.tokens.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.synapse.data.auth.AuthRepository
import com.synapse.data.auth.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.synapse.data.repository.UserRepository
import com.synapse.data.repository.AIRepository

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val aiRepository: AIRepository,
    private val devPreferences: com.synapse.data.local.DevPreferences
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState
    
    /**
     * Observe AI error messages for global Toast notifications
     */
    val aiErrorMessage: StateFlow<String?> = aiRepository.errorMessages
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, null)
    
    /**
     * Dev setting: Should AI error toasts be shown?
     */
    val showAIErrorToasts: StateFlow<Boolean> = devPreferences.showAIErrorToasts
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, false)
    
    /**
     * Clear AI error after Toast is shown
     */
    fun clearAIError() {
        aiRepository.clearError()
    }

    fun refreshOnStartIfLoggedIn() {
        viewModelScope.launch { tokenRepository.getAndSaveCurrentToken() }
    }

    fun registerCurrentToken() {
        viewModelScope.launch { tokenRepository.getAndSaveCurrentToken() }
    }
    fun upsertCurrentUser() {
        viewModelScope.launch { userRepository.upsertCurrentUser() }
    }

    suspend fun requestGoogleIdToken(activity: android.app.Activity): String? =
        authRepository.requestGoogleIdToken(activity)

    suspend fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) {
        authRepository.signInWithIdToken(idToken, onComplete)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}


