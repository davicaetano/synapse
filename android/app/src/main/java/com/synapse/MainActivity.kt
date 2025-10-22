package com.synapse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.synapse.notifications.requestNotificationPermissionIfNeeded
import com.synapse.data.presence.PresenceManager
import com.synapse.data.network.NetworkConnectivityMonitor
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val mainVm: MainActivityViewModel by viewModels()
    
    @Inject
    lateinit var presenceManager: PresenceManager
    
    @Inject
    lateinit var networkMonitor: NetworkConnectivityMonitor
    
    // NavController reference to navigate from intents
    private var navController: NavController? = null
    
    // Job to track network monitoring coroutine
    private var networkMonitoringJob: kotlinx.coroutines.Job? = null
    
    // Pending deep link navigation (from notification when app was killed)
    private var pendingChatId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extract chatId from notification intent (when app was killed)
        val chatId = intent?.extras?.getString("chatId")
        if (chatId != null) {
            pendingChatId = chatId
        }
        
        requestNotificationPermissionIfNeeded()
        
        setContentView(R.layout.activity_main)
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        lifecycleScope.launch {
            mainVm.authState.collect { authState ->
                when (authState) {
                    is com.synapse.data.auth.AuthState.SignedIn -> {
                        val currentDest = navController?.currentDestination?.id
                        if (currentDest == R.id.authFragment) {
                            navController?.navigate(R.id.action_auth_to_inbox)
                        }
                        
                        // Navigate to pending conversation from notification (if app was killed)
                        if (pendingChatId != null) {
                            navController?.navigate(
                                R.id.conversationFragment,
                                bundleOf("conversationId" to pendingChatId)
                            )
                            pendingChatId = null
                        }
                    }
                    else -> {
                        val currentDest = navController?.currentDestination?.id
                        if (currentDest != R.id.authFragment) {
                            val navOptions = androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(0, inclusive = true)
                                .build()
                            navController?.navigate(R.id.authFragment, null, navOptions)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Public method to trigger Google Sign In from AuthFragment.
     */
    fun triggerGoogleSignIn() {
        startGoogleSignIn()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle FCM notification clicks by extracting chatId from extras
        val chatId = intent.extras?.getString("chatId")
        if (chatId != null) {
            navController?.navigate(
                R.id.conversationFragment,
                bundleOf("conversationId" to chatId)
            )
        } else {
            // Fallback to standard deep link handling
            navController?.handleDeepLink(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Start monitoring network connectivity for UI indicator (green/red light)
        networkMonitor.startMonitoring()
        
        // Mark user as online in Firebase when app starts
        presenceManager.markOnline()
        
        // Observe network connectivity to re-establish presence when connection is recovered
        networkMonitoringJob = lifecycleScope.launch {
            var wasConnected = networkMonitor.isConnected.value

            networkMonitor.isConnected
                .collect { isConnected ->
                    
                    // Only call markOnline() when connection is RECOVERED (was offline, now online)
                    // Don't try to call markOffline() when losing connection - it won't work anyway!
                    // Firebase's onDisconnect() handler will mark us offline after ~30 seconds.
                    if (!wasConnected && isConnected) {
                        presenceManager.markOnline()
                    }
                    
                    wasConnected = isConnected
                }
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // Cancel network monitoring coroutine
        networkMonitoringJob?.cancel()
        networkMonitoringJob = null
        
        // Stop monitoring network connectivity
        networkMonitor.stopMonitoring()
        
        // Mark user as offline when app stops
        presenceManager.markOffline()
    }

    private fun startGoogleSignIn() {
        lifecycleScope.launch {
            try {
                val idToken = mainVm.requestGoogleIdToken(this@MainActivity)
                if (idToken != null) {
                    mainVm.signInWithIdToken(idToken) { success ->
                        if (success) {
                            mainVm.upsertCurrentUser()
                            mainVm.registerCurrentToken()
                        }
                    }
                } else {
                    Log.w(TAG, "Google sign-in returned null idToken")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google sign-in failed", e)
            }
        }
    }
}
