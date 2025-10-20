package com.synapse

import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.synapse.notifications.NotificationExtras
import com.synapse.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.synapse.tokens.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SynapseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var tokenRepository: TokenRepository
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    tokenRepository.saveToken(user.uid, token)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Foreground handling: show a simple notification using the messages channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = NotificationManagerCompat.from(this)
            val permissionGranted = nm.areNotificationsEnabled()
            if (!permissionGranted) return
        }

        val title = message.notification?.title ?: message.data["title"] ?: "Synapse"
        val body = message.notification?.body ?: message.data["preview"] ?: "New message"
        val chatId = message.data[NotificationExtras.CHAT_ID]
        val msgId = message.data[NotificationExtras.MESSAGE_ID]
        notificationHelper.showMessageNotification(title, body, chatId, msgId)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 1001
    }
}


