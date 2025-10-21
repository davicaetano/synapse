package com.synapse.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.ui.theme.SynapseTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: ConversationViewModel = hiltViewModel(),
) {
    val ui: ConversationUIState by vm.uiState.collectAsStateWithLifecycle()

    ConversationScreen(
        ui = ui,
        onSendClick = { text: String -> vm.send(text) },
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    ui: ConversationUIState,
    onSendClick: (text: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(ui.messages.size) {
        if (ui.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(ui.messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.title) },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) { padding ->
        Column(
            modifier = modifier.padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ui.messages) { m ->
                    MessageBubble(
                        text = m.text,
                        displayTime = m.displayTime,
                        isMine = m.isMine,
                        isReadByEveryone = m.isReadByEveryone
                    )
                }
            }
            
            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    maxLines = 4
                )
                
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendClick(input)
                            input = ""
                        }
                    },
                    modifier = Modifier.padding(start = 4.dp),
                    enabled = input.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (input.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    displayTime: String,
    isMine: Boolean,
    isReadByEveryone: Boolean = false,
) {
    val bg = if (isMine) Color(0xFF0B93F6) else Color(0xFFE5E5EA)
    val fg = if (isMine) Color.White else Color.Black
    val shape = if (isMine)
        RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    else
        RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = text, color = fg)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayTime,
                    color = fg.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
                if (isReadByEveryone) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Read by everyone",
                        tint = fg.copy(alpha = 0.8f),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(12.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationScreenPreview() {
    SynapseTheme {
        Column {
            ConversationScreen(
                modifier = Modifier.weight(1.0f),
                ui = ConversationUIState(
                    conversationId = "123",
                    title = "Alice",
                    messages = listOf(
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true
                        ),
                        ConversationUIMessage(
                            id = "m2",
                            text = "Hi!",
                            isMine = false,
                            displayTime = "09:13",
                            isReadByEveryone = true
                        )
                    )
                ),
                onSendClick = {}

            )
            Column(
                modifier = Modifier
                    .defaultMinSize(minHeight = 50.dp)
                    .weight(1.0f)

            ) {
                Text(text = "abc")
            }
        }

    }
}


