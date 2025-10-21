package com.synapse.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.MainActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenConversation: (String) -> Unit,
    vm: InboxViewModel = hiltViewModel(),
    mainVm: MainActivityViewModel = hiltViewModel(),
) {
    val items by vm.observeInboxForCurrentUser().collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synapse") },
                actions = {
                    Row(modifier = Modifier.padding(end = 8.dp)) {
                        Button(onClick = { mainVm.sendTestNotification() }) { Text("Test notif") }
                        Spacer(modifier = Modifier.height(0.dp))
                        Button(onClick = { mainVm.signOut() }) { Text("Sign out") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenConversation("userPicker") }) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .then(Modifier)
        ) {
            if (items.isEmpty()) {
                Text("No conversations yet")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    items(items) { c ->
                        ConversationRow(
                            title = c.title,
                            lastMessage = c.lastMessageText ?: "",
                            displayTime = c.displayTime,
                            onClick = { onOpenConversation(c.id) })
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    title: String,
    lastMessage: String,
    displayTime: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxSize()
            .padding(16.dp)
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = lastMessage,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = displayTime,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

