package com.synapse.ui.conversation

data class ConversationUIMessage(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val displayTime: String
)

data class ConversationUIState(
    val conversationId: String,
    val title: String,
    val messages: List<ConversationUIMessage>
)
