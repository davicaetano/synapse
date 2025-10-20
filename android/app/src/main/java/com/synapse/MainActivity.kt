package com.synapse

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.synapse.notifications.NotificationHelper
import com.synapse.auth.AuthState
import com.synapse.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
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
                        Button(onClick = { sendTestNotification() }) {
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

const val MESSAGES_CHANNEL_ID = NotificationChannels.MESSAGES
private const val TAG = "MainActivity"

private fun ComponentActivity.createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelName = "Messages"
        val channelDesc = "Notifications for incoming chat messages"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(MESSAGES_CHANNEL_ID, channelName, importance).apply {
            description = channelDesc
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

private fun ComponentActivity.sendTestNotification() {
    val title = "Alex"
    val body = "Sent you a message"
    val openIntent = Intent(this, ChatActivity::class.java).apply {
        action = ChatActivity.ACTION_OPEN_CHAT
        putExtra(ChatActivity.EXTRA_CHAT_ID, "test_chat_123")
        putExtra(ChatActivity.EXTRA_MESSAGE_ID, "m1")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    val pending = PendingIntent.getActivity(this, 0, openIntent, flags)

    NotificationHelper.showMessageNotification(this, title, body, "test_chat_123", "m1")
}

private fun ComponentActivity.requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
                // No-op; user choice respected. We can show in-app prompt later if denied.
            }
            launcher.launch(permission)
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