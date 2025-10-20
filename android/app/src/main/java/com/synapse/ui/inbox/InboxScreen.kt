package com.synapse.ui.inbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InboxScreen(onOpenConversation: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Inbox (placeholder)")
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onOpenConversation("test_conversation_123") }) {
            Text("Open test conversation")
        }
    }
}


