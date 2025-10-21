package com.synapse.domain.conversation

data class Message(
    val id: String,
    val text: String,
    val senderId: String,
    val createdAtMs: Long,
    val isMine: Boolean = false
)


