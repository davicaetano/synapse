package com.synapse.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.synapse.MainActivity
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
            .setSmallIcon(R.mipmap.ic_launcher)  // TODO: Create ic_notification.xml for proper notification icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationIdProvider.messageIdForChat(chatId), notification)
    }

    private fun createChatPendingIntent(chatId: String?, messageId: String?): PendingIntent {
        // Use deep link to open specific conversation in MainActivity
        val deepLinkUri = if (chatId != null) {
            Uri.parse("synapse://conversation/$chatId")
        } else {
            Uri.parse("synapse://inbox")
        }
        
        val openIntent = Intent(Intent.ACTION_VIEW, deepLinkUri, appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (messageId != null) {
                putExtra("messageId", messageId)
            }
        }
        
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(appContext, chatId.hashCode(), openIntent, flags)
    }
}


