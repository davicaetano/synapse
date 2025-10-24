package com.synapse.ui.groupsettings.removemembers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
fun RemoveMembersScreen(
    onNavigateBack: () -> Unit = {},
    vm: RemoveMembersViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    RemoveMembersScreen(
        uiState = uiState.copy(members = members),
        onNavigateBack = onNavigateBack,
        onToggleUser = { userId -> vm.toggleUserSelection(userId) },
        onConfirm = { showConfirmDialog = true }
    )
    
    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Remove members?") },
            text = { Text("Remove ${uiState.selectedUserIds.size} member(s) from this group?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeSelectedMembers(onSuccess = onNavigateBack)
                        showConfirmDialog = false
                    }
                ) {
                    Text("REMOVE", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveMembersScreen(
    uiState: RemoveMembersUIState,
    onNavigateBack: () -> Unit,
    onToggleUser: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Remove members") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            if (uiState.selectedUserIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Remove ${uiState.selectedUserIds.size}") },
                    icon = { Icon(Icons.Default.Delete, null) },
                    onClick = onConfirm,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(uiState.members) { member ->
                val isAdmin = member.id == uiState.createdBy
                val canRemove = member.id != uiState.createdBy  // Cannot remove admin
                
                UserSelectRow(
                    user = member,
                    isSelected = member.id in uiState.selectedUserIds,
                    isAdmin = isAdmin,
                    enabled = canRemove,
                    onToggle = { if (canRemove) onToggleUser(member.id) }
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
private fun UserSelectRow(
    user: User,
    isSelected: Boolean,
    isAdmin: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            enabled = enabled
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        UserAvatar(
            photoUrl = user.photoUrl,
            displayName = user.displayName,
            size = 48.dp,
            showPresence = false
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName ?: user.id,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    }
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
                if (user.isMyself) {
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

