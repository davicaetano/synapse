package com.synapse.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.synapse.MainActivityViewModel
import com.synapse.domain.conversation.ConversationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenConversation: (String) -> Unit,
    vm: InboxViewModel = hiltViewModel(),
    mainVm: MainActivityViewModel = hiltViewModel(),
) {
    val uiState by vm.observeInboxForCurrentUser().collectAsStateWithLifecycle()
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
            when {
                uiState.isLoading -> {
                    Text("Loading conversations...")
                }
                uiState.error != null -> {
                    Text("Error: ${uiState.error}")
                }
                uiState.items.isEmpty() -> {
                    Text("No conversations yet")
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        items(uiState.items) { item ->
                            ConversationRow(
                                item = item,
                                onClick = { onOpenConversation(item.id) })
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    item: InboxItem,
    onClick: () -> Unit,
) {
    when (item) {
        is InboxItem.SelfConversation -> SelfConversationRow(item, onClick)
        is InboxItem.OneOnOneConversation -> OneOnOneConversationRow(item, onClick)
        is InboxItem.GroupConversation -> GroupConversationRow(item, onClick)
    }
}

@Composable
private fun SelfConversationRow(
    item: InboxItem.SelfConversation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Self conversations don't show a profile picture
        Spacer(modifier = Modifier.size(48.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.lastMessageText ?: "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = item.displayTime,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun OneOnOneConversationRow(
    item: InboxItem.OneOnOneConversation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show the other user's profile picture for direct conversations
        if (item.otherUser.photoUrl != null) {
            AsyncImage(
                model = item.otherUser.photoUrl,
                contentDescription = "Foto de ${item.otherUser.displayName}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.padding(start = 12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.lastMessageText ?: "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = item.displayTime,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun GroupConversationRow(
    item: InboxItem.GroupConversation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // For group conversations, show multiple profile pictures or a group icon
        // For now, we'll show a placeholder for the group
        Spacer(modifier = Modifier.size(48.dp)) // Placeholder for group icon

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.lastMessageText ?: "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = item.displayTime,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

