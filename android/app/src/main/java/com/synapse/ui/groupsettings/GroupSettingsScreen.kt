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
import com.synapse.ui.components.UserAvatar

@Composable
fun GroupSettingsScreen(
    onNavigateBack: () -> Unit = {},
    onAddMember: () -> Unit = {},
    vm: GroupSettingsViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    
    var showNameDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf<User?>(null) }
    
    GroupSettingsScreen(
        uiState = uiState.copy(members = members),
        onNavigateBack = onNavigateBack,
        onClickGroupName = { showNameDialog = true },
        onAddMember = onAddMember,
        onRemoveMember = { user -> showRemoveDialog = user }
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
    
    // Confirm remove member dialog
    showRemoveDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text("Remove member?") },
            text = { Text("Remove ${user.displayName ?: user.id} from this group?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeMember(user.id)
                        showRemoveDialog = null
                    }
                ) {
                    Text("REMOVE", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text("CANCEL")
                }
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
    onAddMember: () -> Unit,
    onRemoveMember: (User) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Group Settings") },
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
        },
        floatingActionButton = {
            if (uiState.isUserAdmin) {
                FloatingActionButton(
                    onClick = onAddMember,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add member")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Group info header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Group icon
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = uiState.groupName,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Text(
                        text = "${uiState.members.size} members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            item { HorizontalDivider() }
            
            // Group name setting
            item {
                SettingsItem(
                    icon = Icons.Default.Edit,
                    title = "Group name",
                    subtitle = uiState.groupName,
                    onClick = onClickGroupName,
                    enabled = uiState.isUserAdmin
                )
            }
            
            item { 
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "${uiState.members.size} MEMBERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Members list
            items(uiState.members) { member ->
                MemberRow(
                    member = member,
                    isAdmin = member.id == uiState.createdBy,
                    canRemove = uiState.isUserAdmin && member.id != uiState.createdBy,
                    onRemove = { onRemoveMember(member) }
                )
            }
            
            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: User,
    isAdmin: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
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
                    Text(
                        text = "Admin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
            if (member.email != null) {
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove member",
                    tint = MaterialTheme.colorScheme.error
                )
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

