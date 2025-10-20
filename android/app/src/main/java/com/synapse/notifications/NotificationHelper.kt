package com.synapse.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.synapse.ChatActivity
import com.synapse.NotificationChannels
import com.synapse.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    fun showMessageNotification(title: String, body: String, chatId: String?, messageId: String?) {
        val pending = createChatPendingIntent(chatId, messageId)
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationIdProvider.messageIdForChat(chatId), notification)
    }

    private fun createChatPendingIntent(chatId: String?, messageId: String?): PendingIntent {
        val openIntent = Intent(appContext, ChatActivity::class.java).apply {
            action = ChatActivity.ACTION_OPEN_CHAT
            putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ChatActivity.EXTRA_MESSAGE_ID, messageId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(appContext, 0, openIntent, flags)
    }
}


