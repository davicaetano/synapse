package com.synapse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.notifications.NotificationHelper
import com.synapse.data.tokens.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.synapse.data.auth.AuthRepository
import com.synapse.data.auth.AuthState
import kotlinx.coroutines.flow.StateFlow
import com.synapse.data.firestore.UserRepository

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val notificationHelper: NotificationHelper,
    private val tokenRepository: TokenRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.authState

    fun refreshOnStartIfLoggedIn() {
        viewModelScope.launch { tokenRepository.getAndSaveCurrentToken() }
    }

    fun registerCurrentToken() {
        viewModelScope.launch { tokenRepository.getAndSaveCurrentToken() }
    }
    fun upsertCurrentUser() {
        viewModelScope.launch { userRepository.upsertCurrentUser() }
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

    suspend fun signInWithIdToken(idToken: String, onComplete: (Boolean) -> Unit) {
        authRepository.signInWithIdToken(idToken, onComplete)
    }

    fun signOut() = authRepository.signOut()
}


