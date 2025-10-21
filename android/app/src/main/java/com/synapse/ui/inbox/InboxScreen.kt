package com.synapse.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun InboxScreen(onOpenConversation: (String) -> Unit, vm: InboxViewModel = hiltViewModel()) {
    val conversations by vm.conversations.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        if (conversations.isEmpty()) {
            Text("No conversations yet")
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                items(conversations) { c ->
                    Text(
                        text = c.title ?: c.id,
                        modifier = Modifier
                            .clickable { onOpenConversation(c.id) }
                            
                    )
                }
            }
        }
    }
}


