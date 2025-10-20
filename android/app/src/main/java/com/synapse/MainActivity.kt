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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var gsiClient: GoogleSignInClient
    private var currentUserEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        setupGoogleSignIn()
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
                        if (currentUserEmail == null) {
                            Button(onClick = { startGoogleSignIn() }) { Text("Sign in with Google") }
                        } else {
                            Text("Signed in: $currentUserEmail")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                auth.signOut()
                                gsiClient.signOut()
                                currentUserEmail = null
                                recreate()
                            }) { Text("Sign out") }
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

    private fun setupGoogleSignIn() {
        auth = FirebaseAuth.getInstance()
        currentUserEmail = auth.currentUser?.email
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        gsiClient = GoogleSignIn.getClient(this, gso)
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    currentUserEmail = auth.currentUser?.email
                    recreate()
                }
            }
        } catch (_: ApiException) {
        }
    }

    private fun startGoogleSignIn() {
        val intent = gsiClient.signInIntent
        signInLauncher.launch(intent)
    }
}

const val MESSAGES_CHANNEL_ID = NotificationChannels.MESSAGES

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

    val notification = NotificationCompat.Builder(this, MESSAGES_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()

    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(1002, notification)
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