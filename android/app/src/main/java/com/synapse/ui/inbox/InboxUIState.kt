package com.synapse.ui.inbox

data class InboxItem(
    val id: String,
    val title: String,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val displayTime: String
)

data class InboxUIState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
