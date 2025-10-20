package com.synapse.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun InboxScreen(onOpenConversation: (String) -> Unit, vm: InboxViewModel = hiltViewModel()) {
    val conversations by vm.conversations.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
            items(conversations) { c ->
                Text(
                    text = c.title ?: c.id,
                    modifier = Modifier
                        .clickable { onOpenConversation(c.id) }
                        .height(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onOpenConversation("test_conversation_123") }) {
            Text("Open test conversation")
        }
    }
}


