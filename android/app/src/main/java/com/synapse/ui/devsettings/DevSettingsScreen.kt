package com.synapse.ui.devsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.ui.settings.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsScreen(
    vm: DevSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    
    var customUrlInput by remember { mutableStateOf(state.customUrl) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ Developer Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // API Base URL Section
            Text(
                text = "API Base URL",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            
            // Production URL (Fixed)
            SettingsItem(
                title = "Production (Render)",
                subtitle = com.synapse.data.local.DevPreferences.PRODUCTION_URL,
                isSelected = state.urlMode == com.synapse.data.local.DevPreferences.URL_MODE_PRODUCTION,
                onClick = { vm.setUrlMode(com.synapse.data.local.DevPreferences.URL_MODE_PRODUCTION) }
            )
            
            // Local URL (Fixed)
            SettingsItem(
                title = "Local Development",
                subtitle = com.synapse.data.local.DevPreferences.LOCAL_URL,
                isSelected = state.urlMode == com.synapse.data.local.DevPreferences.URL_MODE_LOCAL,
                onClick = { vm.setUrlMode(com.synapse.data.local.DevPreferences.URL_MODE_LOCAL) }
            )
            
            // Custom URL (Editable)
            SettingsItem(
                title = "Custom URL",
                subtitle = if (customUrlInput.isNotBlank()) customUrlInput else "Not set",
                isSelected = state.urlMode == com.synapse.data.local.DevPreferences.URL_MODE_CUSTOM,
                onClick = { vm.setUrlMode(com.synapse.data.local.DevPreferences.URL_MODE_CUSTOM) }
            )
            
            // Custom URL input field (shown when custom mode is selected)
            if (state.urlMode == com.synapse.data.local.DevPreferences.URL_MODE_CUSTOM) {
                OutlinedTextField(
                    value = customUrlInput,
                    onValueChange = { 
                        customUrlInput = it
                        vm.setCustomUrl(it)
                    },
                    label = { Text("Enter custom URL") },
                    placeholder = { Text("https://your-api.com/api/") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Debug Features Section
            Text(
                text = "Debug Features",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Batch Message Buttons Toggle
            SettingsItem(
                title = "Show Batch Message Buttons",
                subtitle = if (state.showBatchButtons) "Enabled (20, 100, 500 msg buttons)" else "Disabled",
                trailing = {
                    Switch(
                        checked = state.showBatchButtons,
                        onCheckedChange = { vm.toggleBatchButtons(it) }
                    )
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Info Section
            Text(
                text = "⚠️ Warning",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "These settings are for development only. Changes require app restart to take effect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

