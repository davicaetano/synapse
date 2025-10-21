package com.synapse

import android.app.Application
import com.synapse.notifications.NotificationInitializer
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SynapseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging (only in debug builds)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        NotificationInitializer.ensureChannels(this)
    }

}


