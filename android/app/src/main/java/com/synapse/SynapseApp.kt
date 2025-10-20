package com.synapse

import android.app.Application
import com.synapse.notifications.NotificationInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SynapseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationInitializer.ensureChannels(this)
    }

}


