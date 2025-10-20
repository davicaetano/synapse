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

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val notificationHelper: NotificationHelper,
    private val tokenRepository: TokenRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

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

    fun currentAuthState(): AuthState = if (authRepository.isSignedIn()) {
        AuthState.SignedIn(authRepository.userEmail())
    } else AuthState.SignedOut

    suspend fun requestGoogleIdToken(activity: android.app.Activity): String? =
        authRepository.requestGoogleIdToken(activity)

    fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) =
        authRepository.signInWithIdToken(idToken, onComplete)

    fun signOut() = authRepository.signOut()
}


