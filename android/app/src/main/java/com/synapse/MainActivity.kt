package com.synapse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import com.synapse.ui.theme.SynapseTheme
import com.synapse.notifications.requestNotificationPermissionIfNeeded
import com.synapse.ui.navigation.AppNavHost
import com.synapse.data.presence.PresenceManager
import com.synapse.data.network.NetworkConnectivityMonitor
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainActivity"

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainVm: MainActivityViewModel by viewModels()
    
    @Inject
    lateinit var presenceManager: PresenceManager
    
    @Inject
    lateinit var networkMonitor: NetworkConnectivityMonitor
    
    // NavController reference to navigate from intents
    private var navController: NavHostController? = null
    
    // Job to track network monitoring coroutine
    private var networkMonitoringJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            SynapseTheme {
                AppNavHost(
                    mainVm = mainVm,
                    startGoogleSignIn = { startGoogleSignIn() },
                    onNavControllerCreated = { controller ->
                        navController = controller
                        // Handle deep link after nav controller is ready
                        handleDeepLink(intent)
                    }
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: Intent?) {
        val deepLinkUri = intent?.data
        val conversationId = deepLinkUri?.lastPathSegment
        
        Log.d(TAG, "handleDeepLink - Action: ${intent?.action}")
        Log.d(TAG, "handleDeepLink - URI: $deepLinkUri")
        Log.d(TAG, "handleDeepLink - Conversation ID: $conversationId")
        
        if (conversationId != null && deepLinkUri.host == "conversation") {
            Log.d(TAG, "Will navigate to conversation: $conversationId")
            
            lifecycleScope.launch {
                // Wait for nav controller to be ready
                var attempts = 0
                while (navController == null && attempts < 50) {
                    kotlinx.coroutines.delay(50)
                    attempts++
                }
                
                if (navController != null) {
                    Log.d(TAG, "NavController ready, navigating now...")
                    navController?.navigate("conversation/$conversationId") {
                        popUpTo("inbox") {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                } else {
                    Log.e(TAG, "NavController not ready after waiting")
                }
            }
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
                    Log.d(TAG, "Network connectivity changed: isConnected=$isConnected")
                    
                    // Only call markOnline() when connection is RECOVERED (was offline, now online)
                    // Don't try to call markOffline() when losing connection - it won't work anyway!
                    // Firebase's onDisconnect() handler will mark us offline after ~30 seconds.
                    if (!wasConnected && isConnected) {
                        Log.d(TAG, "Connection recovered - re-establishing presence in Firebase")
                        presenceManager.markOnline()
                    }
                    
                    wasConnected = isConnected
                }
        }
        
        Log.d(TAG, "Network monitoring started for UI indicator and connection recovery")
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
        
        Log.d(TAG, "Network monitoring stopped and user marked as offline")
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
