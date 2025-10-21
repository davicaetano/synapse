package com.synapse.domain.conversation

data class ConversationSummary constructor(
    val id: String,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val memberIds: List<String>
)


