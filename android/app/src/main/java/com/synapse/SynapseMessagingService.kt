package com.synapse

import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.synapse.notifications.NotificationExtras
import com.synapse.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.synapse.data.tokens.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class SynapseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var tokenRepository: TokenRepository
    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var auth: FirebaseAuth
    
    companion object {
        private const val TAG = "SynapseMessagingService"
        private const val NOTIFICATION_ID_BASE = 1001
    }
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tokenRepository.getAndSaveCurrentToken()
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
        val conversationId = message.data[NotificationExtras.CHAT_ID]
        val messageId = message.data[NotificationExtras.MESSAGE_ID]

        // Mark message as received (DELIVERED status)
        // This happens as soon as FCM delivers the notification to the device

        notificationHelper.showMessageNotification(title, body, conversationId, messageId)
    }
}


