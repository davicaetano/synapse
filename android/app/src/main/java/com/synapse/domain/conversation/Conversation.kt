package com.synapse.domain.conversation

data class Conversation(
    val summary: ConversationSummary,
    val messages: List<Message>
)


