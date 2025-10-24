package com.synapse.ui.conversation.refine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Refine Summary Screen
 * Allows users to provide instructions to refine an existing AI summary
 * 
 * Flow:
 * 1. User clicks "Refine" on an AI summary message
 * 2. Opens this screen showing the previous summary
 * 3. User provides refinement instructions
 * 4. New refined summary is created and posted to chat
 */
@Composable
fun RefineSummaryScreen(
    vm: RefineSummaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isRefining by vm.isRefining.collectAsStateWithLifecycle()
    
    var refinementInstructions by remember { mutableStateOf("") }
    
    RefineSummaryContent(
        state = state,
        isRefining = isRefining,
        refinementInstructions = refinementInstructions,
        onRefinementInstructionsChange = { refinementInstructions = it },
        onRefineClick = {
            vm.refineSummary(refinementInstructions)
            onNavigateBack()
        },
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefineSummaryContent(
    state: RefineSummaryUIState,
    isRefining: Boolean,
    refinementInstructions: String,
    onRefinementInstructionsChange: (String) -> Unit,
    onRefineClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Refine AI Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .imePadding()
            ) {
                // Original Summary Card
                Text(
                    text = "Original Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = state.previousSummaryText ?: "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Refinement Instructions
                Text(
                    text = "Refinement Instructions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = refinementInstructions,
                    onValueChange = onRefinementInstructionsChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    placeholder = {
                        Text("Example:\n• Make it more concise\n• Focus on action items\n• Include more details about...")
                    },
                    maxLines = 8
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Refine Button
                Button(
                    onClick = onRefineClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = refinementInstructions.isNotBlank() && !isRefining
                ) {
                    if (isRefining) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(if (isRefining) "Refining..." else "Generate Refined Summary")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "The refined summary will appear as a new message in the chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

