package com.synapse.domain.conversation

import com.synapse.domain.user.User

enum class ConversationType {
    SELF,    // Conversa consigo mesmo (AI, lembretes, etc.)
    DIRECT,  // Conversa entre duas pessoas (única)
    GROUP    // Conversa em grupo (múltiplas pessoas)
}

data class ConversationSummary constructor(
    val id: String,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val members: List<User>,  // ← ALTERADO: Lista de objetos User completos ao invés de IDs
    val convType: ConversationType
) {
    // Propriedade computada para manter compatibilidade onde memberIds ainda é usado
    val memberIds: List<String>
        get() = members.map { it.id }
}


