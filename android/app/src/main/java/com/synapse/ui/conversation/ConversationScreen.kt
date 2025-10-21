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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.ui.theme.SynapseTheme

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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ui.messages) { m ->

                    MessageBubble(
                        text = m.text,
                        displayTime = m.displayTime,
                        isMine = m.isMine
                    )
                }
            }
            Row {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendClick(input)
                            input = ""
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Send")
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
            Text(
                text = displayTime,
                color = fg.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isMine) TextAlign.End else TextAlign.Start
            )
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
                            displayTime = "09:12"
                        ),
                        ConversationUIMessage(
                            id = "m2",
                            text = "Hi!",
                            isMine = false,
                            displayTime = "09:13"
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


