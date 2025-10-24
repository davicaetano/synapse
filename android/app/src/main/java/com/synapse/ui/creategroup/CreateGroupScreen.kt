package com.synapse.ui.creategroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.domain.user.User
import com.synapse.ui.components.LoadingState
import com.synapse.ui.components.UserAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onGroupCreated: (String) -> Unit,
    onClose: () -> Unit,
    vm: CreateGroupViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
            if (uiState.canCreate) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            if (uiState.selectedCount > 0) {
                                "Create (${uiState.selectedCount})"
                            } else {
                                "Create Group"
                            }
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        scope.launch {
                            val conversationId = vm.createGroup()
                            if (conversationId != null) {
                                onGroupCreated(conversationId)
                            }
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        if (uiState.isLoading) {
            LoadingState(text = "Creating group...")
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Group name input (required)
                OutlinedTextField(
                    value = uiState.groupName,
                    onValueChange = { vm.setGroupName(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    label = { Text("Group Name *") },
                    placeholder = { Text("My Awesome Group") },
                    singleLine = true,
                    isError = uiState.groupName.isBlank() && !uiState.isLoading,
                    supportingText = {
                        if (uiState.groupName.isBlank()) {
                            Text(
                                text = "Group name is required",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                
                // Selected count header
                Text(
                    text = if (uiState.selectedCount > 0) {
                        "${uiState.selectedCount} member${if (uiState.selectedCount == 1) "" else "s"} selected"
                    } else {
                        "Select members (optional - you'll be the only member)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                HorizontalDivider()
                
                // User list with checkboxes
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = uiState.selectableUsers,
                        key = { it.user.id }
                    ) { selectableUser ->
                        UserRowWithCheckbox(
                            user = selectableUser.user,
                            isSelected = selectableUser.isSelected,
                            onToggle = { vm.toggleUserSelection(selectableUser.user.id) }
                        )
                        HorizontalDivider()
                    }
                }
                
                // Show error if any
                uiState.error?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserRowWithCheckbox(
    user: User,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            showPresence = true,
            isOnline = user.isOnline
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName ?: user.id,
                style = MaterialTheme.typography.titleMedium
            )
            
            if (user.isOnline) {
                Text(
                    text = "Online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            } else if (user.lastSeenMs != null) {
                val lastSeenText = formatLastSeenShort(user.lastSeenMs)
                Text(
                    text = lastSeenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Helper for last seen formatting
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
        else -> "offline"
    }
}

