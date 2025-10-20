package com.synapse.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ConversationScreen(conversationId: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Conversation: $conversationId (placeholder)")
    }
}


