package com.synapse.ui.conversation.messagedetail

import com.synapse.domain.user.User

data class MessageDetailUIState(
    val messageId: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val sentAt: Long = 0L,
    val serverTimestamp: Long? = null,
    val deliveredTo: List<User> = emptyList(),
    val readBy: List<User> = emptyList(),
    val isLoading: Boolean = false
)

