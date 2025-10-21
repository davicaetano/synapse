package com.synapse

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.synapse.ui.theme.SynapseTheme
import com.synapse.notifications.requestNotificationPermissionIfNeeded
import com.synapse.ui.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainVm: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            SynapseTheme {
                AppNavHost(
                    mainVm = mainVm,
                    startGoogleSignIn = { startGoogleSignIn() }
                )
            }
        }
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
