package com.synapse

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.synapse.ui.theme.SynapseTheme
import com.synapse.notifications.requestNotificationPermissionIfNeeded
import com.synapse.notifications.NotificationHelper
import com.synapse.auth.AuthState
import com.synapse.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var notificationHelper: NotificationHelper
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            SynapseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        Button(onClick = { notificationHelper.showMessageNotification("Alex", "Sent you a message", "test_chat_123", "m1") }) {
                            Text("Send test notification")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        when (val s = authViewModel.currentState()) {
                            is AuthState.SignedOut -> {
                                Button(onClick = { startGoogleSignIn() }) { Text("Sign in with Google") }
                            }
                            is AuthState.SignedIn -> {
                                Text("Signed in: ${s.email}")
                            Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = {
                                    authViewModel.signOut()
                                    recreate()
                                }) { Text("Sign out") }
                            }
                        }
                        Greeting(
                            name = "Android",
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }

    private fun startGoogleSignIn() {
        lifecycleScope.launch {
            try {
                val idToken = authViewModel.requestGoogleIdToken(this@MainActivity)
                if (idToken != null) {
                    authViewModel.signInWithIdToken(idToken) { success ->
                        if (success) recreate()
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SynapseTheme {
        Greeting("Android")
    }
}