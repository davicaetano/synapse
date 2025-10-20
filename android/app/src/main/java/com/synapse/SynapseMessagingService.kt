package com.synapse

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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

        val openIntent = Intent(this, ChatActivity::class.java).apply {
            action = ChatActivity.ACTION_OPEN_CHAT
            putExtra(ChatActivity.EXTRA_CHAT_ID, message.data["chatId"])
            putExtra(ChatActivity.EXTRA_MESSAGE_ID, message.data["messageId"])
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(this, 0, openIntent, flags)

        val notification = NotificationCompat.Builder(this, MESSAGES_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE, notification)
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 1001
    }
}


