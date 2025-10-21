package com.synapse.domain.conversation

enum class ConversationType {
    SELF,    // Conversa consigo mesmo (AI, lembretes, etc.)
    DIRECT,  // Conversa entre duas pessoas (única)
    GROUP    // Conversa em grupo (múltiplas pessoas)
}

data class ConversationSummary constructor(
    val id: String,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val memberIds: List<String>,
    val convType: ConversationType
)


