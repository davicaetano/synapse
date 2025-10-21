package com.synapse.domain.conversation

import com.synapse.domain.user.User

enum class ConversationType {
    SELF,    // Conversation with oneself (AI, reminders, etc.)
    DIRECT,  // Conversation between two people (unique)
    GROUP    // Group conversation (multiple people)
}

data class ConversationSummary constructor(
    val id: String,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val members: List<User>,  // CHANGED: List of complete User objects instead of IDs
    val convType: ConversationType
) {
    // Computed property to maintain compatibility where memberIds is still used
    val memberIds: List<String>
        get() = members.map { it.id }
}


