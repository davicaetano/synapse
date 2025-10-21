package com.synapse.domain.conversation

data class ConversationSummary(
    val id: String,
    val title: String?,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val memberIds: List<String>
)


