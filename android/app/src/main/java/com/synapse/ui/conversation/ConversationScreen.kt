package com.synapse.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ConversationScreen(conversationId: String, vm: ConversationViewModel = hiltViewModel()) {
    val messages by vm.messages.collectAsState()
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { m ->
                Text(text = "${m.senderId}: ${m.text}", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Row {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f))
            Button(onClick = { if (input.isNotBlank()) { vm.send(input); input = "" } }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Send")
            }
        }
    }
}


