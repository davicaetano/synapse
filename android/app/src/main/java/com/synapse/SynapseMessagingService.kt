package com.synapse

import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.synapse.notifications.NotificationExtras
import com.synapse.notifications.NotificationHelper

class SynapseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // TODO: later - upsert token under users/{userId}/fcmTokens/{token}
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
        NotificationHelper.showMessageNotification(this, title, body, chatId, msgId)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 1001
    }
}


