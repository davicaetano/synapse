package com.synapse.ui.groupsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.domain.user.User
import com.synapse.ui.components.GroupAvatar
import com.synapse.ui.components.UserAvatar

@Composable
fun GroupSettingsScreen(
    onNavigateBack: () -> Unit = {},
    onAddMembers: () -> Unit = {},
    onRemoveMembers: () -> Unit = {},
    vm: GroupSettingsViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    
    var showNameDialog by remember { mutableStateOf(false) }
    
    GroupSettingsScreen(
        uiState = uiState.copy(members = members),
        onNavigateBack = onNavigateBack,
        onClickGroupName = { if (uiState.isUserAdmin) showNameDialog = true },
        onAddMembers = onAddMembers,
        onRemoveMembers = onRemoveMembers
    )
    
    // Edit group name dialog
    if (showNameDialog) {
        EditGroupNameDialog(
            currentName = uiState.groupName,
            onDismiss = { showNameDialog = false },
            onSave = { newName ->
                vm.updateGroupName(newName)
                showNameDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    uiState: GroupSettingsUIState,
    onNavigateBack: () -> Unit,
    onClickGroupName: () -> Unit,
    onAddMembers: () -> Unit,
    onRemoveMembers: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Group info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            // Group header (WhatsApp-style - non-clickable)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GroupAvatar(
                        groupName = uiState.groupName,
                        size = 64.dp
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.groupName.ifBlank { "Unnamed Group" },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Group · ${uiState.members.size} members",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            item { HorizontalDivider() }
            
            // Group name (clickable for admin)
            item {
                SettingsItem(
                    icon = Icons.Default.Edit,
                    title = "Group name",
                    subtitle = uiState.groupName.ifBlank { "Not set" },
                    onClick = onClickGroupName,
                    enabled = uiState.isUserAdmin
                )
            }
            
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            
            // Add/Remove members buttons (only for admin)
            if (uiState.isUserAdmin) {
                item {
                    SettingsItem(
                        icon = Icons.Default.PersonAdd,
                        title = "Add members",
                        subtitle = "Invite people to this group",
                        onClick = onAddMembers
                    )
                }
                item {
                    SettingsItem(
                        icon = Icons.Default.PersonRemove,
                        title = "Remove members",
                        subtitle = "Remove people from this group",
                        onClick = onRemoveMembers
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }
            
            // Members section header
            item {
                Text(
                    text = "${uiState.members.size} MEMBERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Members list (read-only)
            items(uiState.members) { member ->
                MemberRowSimple(
                    member = member,
                    isAdmin = member.id == uiState.createdBy
                )
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MemberRowSimple(
    member: User,
    isAdmin: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            photoUrl = member.photoUrl,
            displayName = member.displayName,
            size = 48.dp,
            showPresence = false
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName ?: member.id,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Admin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (member.isMyself) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(You)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(32.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EditGroupNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change group name") },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(nameInput) },
                enabled = nameInput.isNotBlank()
            ) {
                Text("SAVE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
