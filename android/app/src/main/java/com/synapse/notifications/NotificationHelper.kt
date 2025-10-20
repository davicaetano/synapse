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

object NotificationHelper {
    fun showMessageNotification(context: Context, title: String, body: String, chatId: String?, messageId: String?) {
        val pending = createChatPendingIntent(context, chatId, messageId)
        val notification = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationIdProvider.messageIdForChat(chatId), notification)
    }

    fun createChatPendingIntent(context: Context, chatId: String?, messageId: String?): PendingIntent {
        val openIntent = Intent(context, ChatActivity::class.java).apply {
            action = ChatActivity.ACTION_OPEN_CHAT
            putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ChatActivity.EXTRA_MESSAGE_ID, messageId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, 0, openIntent, flags)
    }
}


