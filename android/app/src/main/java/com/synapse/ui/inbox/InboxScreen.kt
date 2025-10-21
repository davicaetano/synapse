package com.synapse.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun InboxScreen(onOpenConversation: (String) -> Unit, vm: InboxViewModel = hiltViewModel()) {
    val conversations by vm.conversations.collectAsState()
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenConversation("userPicker") }) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().then(Modifier)) {
            if (conversations.isEmpty()) {
                Text("No conversations yet")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations) { c ->
                        Text(
                            text = c.title ?: c.id,
                            modifier = Modifier.clickable { onOpenConversation(c.id) }
                        )
                    }
                }
            }
        }
    }
}


