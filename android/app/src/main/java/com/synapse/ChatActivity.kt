package com.synapse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.synapse.ui.theme.SynapseTheme

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: ""

        setContent {
            SynapseTheme {
                ChatPlaceholder(chatId = chatId, messageId = messageId)
            }
        }
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.synapse.action.OPEN_CHAT"
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_MESSAGE_ID = "messageId"
    }
}

@Composable
private fun ChatPlaceholder(chatId: String, messageId: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Chat: $chatId", style = MaterialTheme.typography.titleLarge)
        Text(text = "Message: $messageId", style = MaterialTheme.typography.bodyMedium)
    }
}


