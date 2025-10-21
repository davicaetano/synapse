package com.synapse.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.Message
import com.synapse.ui.theme.SynapseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationScreen(
    vm: ConversationViewModel = hiltViewModel(),
) {
    val conversation: Conversation by vm.conversation.collectAsStateWithLifecycle()

    ConversationScreen(
        conversation = conversation,
        onSendClick = { text: String -> vm.send(text) }
    )
}

@Composable
fun ConversationScreen(
    conversation: Conversation,
    onSendClick: (text: String) -> Unit,
) {

    var input by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversation.messages) { m ->

                MessageBubble(
                    text = m.text,
                    timeMs = m.createdAtMs,
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

@Composable
private fun MessageBubble(
    text: String,
    timeMs: Long,
    isMine: Boolean
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
                text = formatTime(timeMs),
                color = fg.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isMine) TextAlign.End else TextAlign.Start
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return ""
    val date = Date(ms)
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(date)
}

@Preview(showBackground = true)
@Composable
private fun ConversationScreenPreview() {
    SynapseTheme {
        Column {
            ConversationScreen(
                conversation = Conversation(
                    summary = ConversationSummary(
                        id = "123",
                        lastMessageText = "123",
                        updatedAtMs = 123,
                        title = "as",
                        memberIds = listOf()
                    ),
                    messages = listOf(
                        Message(
                            id = "123",
                            text = "Hello!",
                            senderId = "123",
                            isMine = true,
                            createdAtMs = 123
                        ),
                        Message(
                            id = "123",
                            text = "Hello!",
                            senderId = "123",
                            isMine = true,
                            createdAtMs = 123
                        ),
                        Message(
                            id = "123",
                            text = "Hello!",
                            senderId = "123",
                            isMine = true,
                            createdAtMs = 123
                        )
                    ),
                ),
                onSendClick = {}

            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

            }
//            Row {
//                OutlinedTextField(
//                    value = "",
//                    onValueChange = {},
//                    modifier = Modifier.weight(1f),
//                    placeholder = { Text("Type a message") })
//                Button(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Text("Send") }
//            }
        }
    }
}


