package com.synapse.ui.inbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun InboxScreen(onOpenConversation: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Inbox (placeholder)")
    }
}


