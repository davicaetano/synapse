package com.synapse.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.synapse.NotificationChannels

object NotificationInitializer {
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Messages"
            val channelDesc = "Notifications for incoming chat messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NotificationChannels.MESSAGES, channelName, importance).apply {
                description = channelDesc
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}


