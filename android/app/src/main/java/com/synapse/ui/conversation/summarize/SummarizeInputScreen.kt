package com.synapse.ui.conversation.summarize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class AIAgentMode {
    THREAD_SUMMARIZATION,
    ACTION_ITEMS,
    CUSTOM
}

/**
 * Synapse AI Agent Screen
 * 
 * Provides 3 AI capabilities:
 * 1. Thread Summarization (Rubric Item 1)
 * 2. Action Item Extraction (Rubric Item 2)
 * 3. Custom Instructions (flexible AI interaction)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarizeInputScreen(
    onBack: () -> Unit,
    onGenerate: (customInstructions: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(AIAgentMode.THREAD_SUMMARIZATION) }
    var customText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    // Auto-focus TextField when Custom mode is selected
    LaunchedEffect(selectedMode) {
        if (selectedMode == AIAgentMode.CUSTOM) {
            focusRequester.requestFocus()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synapse AI Agent") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()  // Adjust for keyboard
        ) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            // Radio button options
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option 1: Thread Summarization
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedMode == AIAgentMode.THREAD_SUMMARIZATION,
                            onClick = { selectedMode = AIAgentMode.THREAD_SUMMARIZATION },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == AIAgentMode.THREAD_SUMMARIZATION,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Thread Summarization",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Get a comprehensive summary of the conversation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Option 2: Action Items
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedMode == AIAgentMode.ACTION_ITEMS,
                            onClick = { selectedMode = AIAgentMode.ACTION_ITEMS },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == AIAgentMode.ACTION_ITEMS,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Action Item Extraction",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Extract tasks, deadlines, and assignments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Option 3: Custom Instructions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedMode == AIAgentMode.CUSTOM,
                            onClick = { selectedMode = AIAgentMode.CUSTOM },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == AIAgentMode.CUSTOM,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Instructions",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Enter your own instructions for AI analysis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Custom text field (always visible, enabled/disabled based on selection)
            // Clicking on it when disabled automatically selects Custom mode
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .focusRequester(focusRequester)
                    .then(
                        if (selectedMode != AIAgentMode.CUSTOM) {
                            Modifier.clickable { selectedMode = AIAgentMode.CUSTOM }
                        } else {
                            Modifier
                        }
                    ),
                label = { Text("Your Instructions") },
                placeholder = { Text("e.g., Create a timeline, List decisions, Extract key points...") },
                maxLines = 6,
                enabled = selectedMode == AIAgentMode.CUSTOM
            )
            }
            
            // Generate button (fixed at bottom, outside scroll)
            Button(
                onClick = {
                    val instructions = when (selectedMode) {
                        AIAgentMode.THREAD_SUMMARIZATION -> null  // Default summary
                        AIAgentMode.ACTION_ITEMS -> "Extract all action items from this conversation. For each action item, identify: the task, who is responsible, deadline (if mentioned), and priority level."
                        AIAgentMode.CUSTOM -> customText.trim().takeIf { it.isNotEmpty() }
                    }
                    onGenerate(instructions)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp),  // Equal spacing top and bottom
                enabled = selectedMode != AIAgentMode.CUSTOM || customText.isNotBlank(),
                contentPadding = PaddingValues(vertical = 16.dp)  // Internal padding for text
            ) {
                Text(
                    text = "Generate",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

