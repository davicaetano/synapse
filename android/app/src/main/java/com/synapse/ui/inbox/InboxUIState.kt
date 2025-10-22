package com.synapse.ui.inbox

import com.synapse.domain.user.User
import com.synapse.domain.conversation.ConversationType

sealed class InboxItem {
    abstract val id: String
    abstract val title: String
    abstract val lastMessageText: String?
    abstract val updatedAtMs: Long
    abstract val displayTime: String
    abstract val convType: ConversationType
    abstract val unreadCount: Int
    abstract val typingText: String?  // "John is typing..." or null

    data class SelfConversation(
        override val id: String,
        override val title: String,
        override val lastMessageText: String?,
        override val updatedAtMs: Long,
        override val displayTime: String,
        override val convType: ConversationType = ConversationType.SELF,
        override val unreadCount: Int = 0,
        override val typingText: String? = null
    ) : InboxItem()

    data class OneOnOneConversation(
        override val id: String,
        override val title: String,
        override val lastMessageText: String?,
        override val updatedAtMs: Long,
        override val displayTime: String,
        override val convType: ConversationType = ConversationType.DIRECT,
        val otherUser: User, // Other user for direct conversations
        override val unreadCount: Int = 0,
        override val typingText: String? = null
    ) : InboxItem()

    data class GroupConversation(
        override val id: String,
        override val title: String,
        override val lastMessageText: String?,
        override val updatedAtMs: Long,
        override val displayTime: String,
        override val convType: ConversationType = ConversationType.GROUP,
        val members: List<User>,
        val groupName: String? = null,  // Group name for avatar
        override val unreadCount: Int = 0,
        override val typingText: String? = null
    ) : InboxItem()
}

data class InboxUIState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnected: Boolean = true  // Network connectivity status
)
