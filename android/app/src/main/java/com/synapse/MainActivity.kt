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
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainActivity"

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainVm: MainActivityViewModel by viewModels()
    
    @Inject
    lateinit var presenceManager: PresenceManager
    
    // NavController reference to navigate from intents
    private var navController: NavHostController? = null

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
        presenceManager.markOnline()
        Log.d(TAG, "User marked as online")
    }
    
    override fun onStop() {
        super.onStop()
        presenceManager.markOffline()
        Log.d(TAG, "User marked as offline")
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
