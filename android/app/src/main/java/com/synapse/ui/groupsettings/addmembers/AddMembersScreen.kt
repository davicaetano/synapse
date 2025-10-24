package com.synapse.ui.groupsettings.addmembers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
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
fun AddMembersScreen(
    onNavigateBack: () -> Unit = {},
    vm: AddMembersViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    
    AddMembersScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onToggleUser = { userId -> vm.toggleUserSelection(userId) },
        onSearchChange = { query -> vm.updateSearch(query) },
        onConfirm = { vm.addSelectedMembers(onSuccess = onNavigateBack) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMembersScreen(
    uiState: AddMembersUIState,
    onNavigateBack: () -> Unit,
    onToggleUser: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Add members") },
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
                    text = { Text("Add ${uiState.selectedUserIds.size}") },
                    icon = { Icon(Icons.Default.Check, null) },
                    onClick = onConfirm
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search users...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            
            // User list
            LazyColumn {
                items(uiState.allUsers) { user ->
                    UserSelectRow(
                        user = user,
                        isSelected = user.id in uiState.selectedUserIds,
                        onToggle = { onToggleUser(user.id) }
                    )
                }
                
                // Empty state
                if (uiState.allUsers.isEmpty() && !uiState.isLoading) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isBlank()) {
                                    "All users are already in the group"
                                } else {
                                    "No users found"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Bottom spacing for FAB
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun UserSelectRow(
    user: User,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        UserAvatar(
            photoUrl = user.photoUrl,
            displayName = user.displayName,
            size = 48.dp,
            showPresence = false
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = user.displayName ?: user.id,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

