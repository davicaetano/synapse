package com.synapse.ui.inbox

import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.data.source.realtime.entity.PresenceEntity
import com.synapse.data.mapper.toDomain
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.user.User

/**
 * Mapper functions to transform raw data into InboxUIState
 * Separated from ViewModel for testability and reusability
 */

/**
 * Build InboxUIState from raw data.
 */
fun buildInboxUIState(
    userId: String,
    conversations: List<ConversationEntity>,
    users: List<UserEntity>,
    presence: Map<String, PresenceEntity>,
    typing: Map<String, List<User>>,
    unreadCounts: Map<String, Int>,
    isConnected: Boolean,
): InboxUIState {
    if (conversations.isEmpty()) {
        return InboxUIState(items = emptyList(), isLoading = false, isConnected = isConnected)
    }
    
    // Build user map
    val usersMap = users.associateBy { it.id }
    
    // Transform to UI items (unreadCounts comes from reactive Flow)
    val items = conversations.mapNotNull { conv ->
        buildInboxItem(userId, conv, usersMap, presence, typing, unreadCounts)
    }.sortedByDescending { it.updatedAt.toDate().time }
    
    return InboxUIState(items = items, isLoading = false, isConnected = isConnected)
}

/**
 * Build a single InboxItem from raw data.
 */
fun buildInboxItem(
    userId: String,
    conv: ConversationEntity,
    usersMap: Map<String, UserEntity>,
    presence: Map<String, PresenceEntity>,
    typing: Map<String, List<User>>,
    unreadCounts: Map<String, Int>
): InboxItem? {
    val unreadCount = unreadCounts[conv.id] ?: 0
    val typingUsers = typing[conv.id] ?: emptyList()
    val typingText = when {
        typingUsers.isEmpty() -> null
        typingUsers.size == 1 -> "typing..."
        else -> "${typingUsers.size} people are typing..."
    }
    
    // Build members with presence
    val members = conv.members
        .filter { !it.value.isAdmin && !it.value.isBot && !it.value.isDeleted }
        .map { it.key }
        .mapNotNull { memberId ->
        val userEntity = usersMap[memberId]
        val presenceEntity = presence[memberId]
        userEntity?.toDomain(
            presence = presenceEntity,
            isMyself = (memberId == userId)
        )
    }
    
    val title = when (conv.convType) {
        ConversationType.SELF.name -> "AI Assistant"
        ConversationType.DIRECT.name -> {
            members.firstOrNull { it.id != userId }?.displayName ?: "Unknown User"
        }
        ConversationType.GROUP.name -> {
            conv.groupName?.ifBlank { null } ?: "Group"
        }
        else -> "Unknown"
    }
    
    return when (conv.convType) {
        ConversationType.SELF.name -> InboxItem.SelfConversation(
            id = conv.id,
            title = title,
            lastMessageText = conv.lastMessageText,
            updatedAt = conv.updatedAt,
            displayTime = formatTime(conv.updatedAt),
            convType = ConversationType.SELF,
            unreadCount = unreadCount,
            typingText = typingText
        )
        ConversationType.DIRECT.name -> {
            val otherUser = members.firstOrNull { it.id != userId }
            if (otherUser != null) {
                InboxItem.OneOnOneConversation(
                    id = conv.id,
                    title = title,
                    lastMessageText = conv.lastMessageText,
                    updatedAt = conv.updatedAt,
                    displayTime = formatTime(conv.updatedAt),
                    convType = ConversationType.DIRECT,
                    otherUser = otherUser,
                    unreadCount = unreadCount,
                    typingText = typingText
                )
            } else {
                null // Skip if other user not found
            }
        }
        ConversationType.GROUP.name -> InboxItem.GroupConversation(
            id = conv.id,
            title = title,
            lastMessageText = conv.lastMessageText,
            updatedAt = conv.updatedAt,
            displayTime = formatTime(conv.updatedAt),
            convType = ConversationType.GROUP,
            members = members,
            groupName = conv.groupName,
            unreadCount = unreadCount,
            typingText = typingText
        )
        else -> null
    }
}

private fun formatTime(timestamp: com.google.firebase.Timestamp): String {
    val date = timestamp.toDate()
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return fmt.format(date)
}

