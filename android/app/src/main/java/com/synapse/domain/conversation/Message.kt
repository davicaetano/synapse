package com.synapse.domain.conversation

import com.synapse.domain.user.User

data class Message constructor(
    val id: String,
    val text: String,
    val senderId: String,
    val createdAtMs: Long,
    val isMine: Boolean = false,
    val receivedBy: List<User> = emptyList(), // Users that have received this message
    val readBy: List<User> = emptyList(),      // Users that have read this message
    val isReadByEveryone: Boolean = false,    // True if all conversation members have read this message
    val status: MessageStatus = MessageStatus.DELIVERED,  // Message delivery status
    val type: String = "text"  // Message type: "text", "AI_SUMMARY"
)


