package com.synapse.ui.conversation.messagedetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.domain.user.User
import com.synapse.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageDetailScreen(
    onNavigateBack: () -> Unit = {},
    vm: MessageDetailViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    
    // Map user IDs to User objects
    val usersMap = users.associateBy { it.id }
    val sender = usersMap[uiState.senderId]
    
    // Calculate delivered/read users from memberStatus
    // This is a simplification - in reality you'd pass this from ViewModel
    val deliveredUsers = uiState.deliveredTo.mapNotNull { userId -> 
        usersMap[userId.id]
    }
    val readUsers = uiState.readBy.mapNotNull { userId ->
        usersMap[userId.id]  
    }
    
    MessageDetailScreen(
        uiState = uiState.copy(
            senderName = sender?.displayName ?: "Unknown",
            deliveredTo = deliveredUsers,
            readBy = readUsers
        ),
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    uiState: MessageDetailUIState,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Message info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Message preview
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = uiState.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // Sent info
            item {
                InfoRow(
                    icon = Icons.Default.Check,
                    label = "Sent",
                    value = formatTimestamp(uiState.sentAt),
                    subtitle = "By ${uiState.senderName}"
                )
            }
            
            if (uiState.serverTimestamp != null && uiState.serverTimestamp != uiState.sentAt) {
                item {
                    InfoRow(
                        icon = Icons.Default.Check,
                        label = "Delivered to server",
                        value = formatTimestamp(uiState.serverTimestamp)
                    )
                }
            }
            
            // Delivered section
            if (uiState.deliveredTo.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        icon = Icons.Default.DoneAll,
                        text = "DELIVERED TO",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                items(uiState.deliveredTo) { user ->
                    UserInfoRow(user = user)
                }
            }
            
            // Read section
            if (uiState.readBy.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        icon = Icons.Default.DoneAll,
                        text = "READ BY",
                        iconTint = Color(0xFF2196F3)  // Blue
                    )
                }
                
                items(uiState.readBy) { user ->
                    UserInfoRow(user = user)
                }
            }
            
            // Empty states
            if (uiState.deliveredTo.isEmpty() && uiState.readBy.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Not delivered yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(32.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconTint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun UserInfoRow(user: User) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            photoUrl = user.photoUrl,
            displayName = user.displayName,
            size = 40.dp,
            showPresence = false
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = user.displayName ?: user.id,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0) return "Unknown"
    val date = Date(ms)
    val fmt = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return fmt.format(date)
}

