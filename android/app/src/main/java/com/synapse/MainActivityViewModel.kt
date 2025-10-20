package com.synapse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.notifications.NotificationHelper
import com.synapse.tokens.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.synapse.auth.AuthRepository
import com.synapse.auth.AuthState
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val notificationHelper: NotificationHelper,
    private val tokenRepository: TokenRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState

    fun refreshOnStartIfLoggedIn() {
        viewModelScope.launch { tokenRepository.getAndSaveCurrentToken() }
    }

    fun registerCurrentToken() {
        viewModelScope.launch { tokenRepository.getAndSaveCurrentToken() }
    }

    fun sendTestNotification() {
        notificationHelper.showMessageNotification(
            title = "Alex",
            body = "Sent you a message",
            chatId = "test_chat_123",
            messageId = "m1"
        )
    }

    suspend fun requestGoogleIdToken(activity: android.app.Activity): String? =
        authRepository.requestGoogleIdToken(activity)

    fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) =
        authRepository.signInWithIdToken(idToken, onComplete)

    fun signOut() = authRepository.signOut()
}


