package com.synapse.ui.inbox

import com.synapse.domain.user.User

data class InboxItem(
    val id: String,
    val title: String,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val displayTime: String,
    val otherUser: User? = null, // ← Dados do outro usuário para conversas diretas
    val convType: com.synapse.domain.conversation.ConversationType
)

data class InboxUIState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
