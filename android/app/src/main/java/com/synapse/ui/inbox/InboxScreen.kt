package com.synapse.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.MainActivityViewModel
import com.synapse.ui.components.EmptyState
import com.synapse.ui.components.ErrorState
import com.synapse.ui.components.GroupAvatar
import com.synapse.ui.components.LoadingState
import com.synapse.ui.components.PresenceIndicator
import com.synapse.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: InboxViewModel = hiltViewModel(),
    mainVm: MainActivityViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Synapse")
                        Spacer(modifier = Modifier.size(8.dp))
                        // User's own connection status
                        PresenceIndicator(isOnline = uiState.isConnected)
                    }
                },
                actions = {
                    // Search icon (disabled for now, visual only)
                    IconButton(
                        onClick = { /* TODO: Implement search */ },
                        enabled = false
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search conversations",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    
                    // Menu button (3 dots)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Status indicator
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PresenceIndicator(isOnline = uiState.isConnected)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(if (uiState.isConnected) "Online" else "Offline")
                                    }
                                },
                                onClick = { /* TODO: Change status */ },
                                enabled = false
                            )
                            
                            HorizontalDivider()
                            
                            DropdownMenuItem(
                                text = { Text("New Group") },
                                onClick = {
                                    showMenu = false
                                    onOpenConversation("createGroup")
                                }
                            )
                            
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                }
                            )
                            
                            HorizontalDivider()
                            
                            DropdownMenuItem(
                                text = { Text("Sign Out") },
                                onClick = {
                                    showMenu = false
                                    mainVm.signOut()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
                    LoadingState(text = "Loading conversations...")
                }
                uiState.error != null -> {
                    ErrorState(message = uiState.error ?: "Unknown error")
                }
                uiState.items.isEmpty() -> {
                    EmptyState(
                        title = "No conversations yet",
                        subtitle = "Start a new conversation by tapping the + button"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        items(uiState.items) { item ->
                            ConversationRow(
                                item = item,
                                onClick = { onOpenConversation(item.id) },
                                currentUserIsConnected = uiState.isConnected
                            )
                            HorizontalDivider()
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
    currentUserIsConnected: Boolean,
) {
    when (item) {
        is InboxItem.SelfConversation -> SelfConversationRow(item, onClick)
        is InboxItem.OneOnOneConversation -> OneOnOneConversationRow(item, onClick, currentUserIsConnected)
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
            // Show typing indicator if someone is typing, otherwise show last message
            Text(
                text = item.typingText ?: item.lastMessageText ?: "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontStyle = if (item.typingText != null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                ),
                color = if (item.typingText != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = item.displayTime,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.size(4.dp))
            UnreadBadge(count = item.unreadCount)
        }
    }
}

@Composable
private fun OneOnOneConversationRow(
    item: InboxItem.OneOnOneConversation,
    onClick: () -> Unit,
    currentUserIsConnected: Boolean,
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User avatar with presence indicator
        // Only show presence if current user is connected
        UserAvatar(
            photoUrl = item.otherUser.photoUrl,
            displayName = item.otherUser.displayName,
            size = 48.dp,
            showPresence = currentUserIsConnected,
            isOnline = item.otherUser.isOnline
        )
        
        Spacer(modifier = Modifier.padding(start = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // User name + status (online or last seen)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Only show status if current user is connected
                if (currentUserIsConnected) {
                    if (item.otherUser.isOnline) {
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "• online",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                            fontSize = 11.sp
                        )
                    } else if (item.otherUser.lastSeenMs != null) {
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "• ${formatLastSeenShort(item.otherUser.lastSeenMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Show typing indicator if someone is typing, otherwise show last message
            Text(
                text = item.typingText ?: item.lastMessageText ?: "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontStyle = if (item.typingText != null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                ),
                color = if (item.typingText != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = item.displayTime,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.size(4.dp))
            UnreadBadge(count = item.unreadCount)
        }
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
        // Group avatar with initials or icon
        GroupAvatar(
            groupName = item.groupName,
            groupPhotoUrl = null,
            size = 48.dp
        )
        
        Spacer(modifier = Modifier.padding(start = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // First line: Group name (title)
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Second line: Typing indicator > Members list > Last message
            val membersText = item.members.joinToString(", ") { it.displayName ?: it.id }
            val displayText = item.typingText ?: if (membersText.isNotBlank()) membersText else (item.lastMessageText ?: "")
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontStyle = if (item.typingText != null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                ),
                color = if (item.typingText != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = item.displayTime,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.size(4.dp))
            UnreadBadge(count = item.unreadCount)
        }
    }
}

// Helper function for short format of last seen (used in inbox)
private fun formatLastSeenShort(lastSeenMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - lastSeenMs
    
    return when {
        diffMs < java.util.concurrent.TimeUnit.MINUTES.toMillis(1) -> "just now"
        diffMs < java.util.concurrent.TimeUnit.HOURS.toMillis(1) -> {
            val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diffMs)
            "${mins}m ago"
        }
        diffMs < java.util.concurrent.TimeUnit.DAYS.toMillis(1) -> {
            val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diffMs)
            "${hours}h ago"
        }
        diffMs < java.util.concurrent.TimeUnit.DAYS.toMillis(7) -> {
            val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs)
            "${days}d ago"
        }
        else -> "long ago"
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count > 0) {
        // Show real unread count in a badge (WhatsApp style)
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

