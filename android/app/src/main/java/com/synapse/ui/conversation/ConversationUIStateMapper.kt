package com.synapse.ui.conversation

import com.synapse.data.mapper.toDomain
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.data.source.realtime.entity.PresenceEntity
import com.synapse.domain.conversation.ConversationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Mapper functions to transform raw data into ConversationUIState
 * Separated from ViewModel for testability and reusability
 */

/**
 * Build ConversationUIState from raw data.
 */
fun buildConversationUIState(
    userId: String,
    conversationId: String,
    conversation: ConversationEntity?,
    users: List<UserEntity>,
    presence: Map<String, PresenceEntity>,
    typingText: String?,
    isConnected: Boolean
): ConversationUIState {
    if (conversation == null) {
        // Conversation not found or loading
        return ConversationUIState(
            conversationId = conversationId,
            title = "Loading...",
            messages = emptyList(),
            convType = ConversationType.DIRECT
        )
    }
    
    // Build user map
    val usersMap = users.associateBy { it.id }
    
    // Build members with presence (only active members, not deleted)
    val members = conversation.members
        .filterValues { !it.isDeleted }  // Exclude deleted members
        .keys
        .mapNotNull { memberId ->
            val userEntity = usersMap[memberId]
            val presenceEntity = presence[memberId]
            userEntity?.toDomain(
                presence = presenceEntity,
                isMyself = (memberId == userId)
            )
        }
    
    // NOTE: Messages are handled separately via Paging3 (messagesPaged)
    // uiState.messages will always be empty
    
    // Build title and subtitle
    val convType = try {
        ConversationType.valueOf(conversation.convType)
    } catch (e: Exception) {
        ConversationType.DIRECT
    }
    
    val (title, subtitle) = when (convType) {
        ConversationType.SELF -> "AI Assistant" to null
        ConversationType.DIRECT -> {
            val otherUser = members.firstOrNull { it.id != userId }
            val name = otherUser?.displayName ?: "Unknown"
            // Don't calculate status here - will be calculated in UI for real-time updates
            name to null
        }
        ConversationType.GROUP -> {
            val name = conversation.groupName ?: "Group"
            val memberCount = "${members.size} member${if (members.size == 1) "" else "s"}"
            name to memberCount
        }
    }
    
    return ConversationUIState(
        conversationId = conversation.id,
        title = title,
        subtitle = subtitle,
        messages = emptyList(),  // Always empty - messages handled by Paging3
        convType = convType,
        members = members,
        isUserAdmin = conversation.createdBy == userId,
        otherUserOnline = if (convType == ConversationType.DIRECT) {
            members.firstOrNull { it.id != userId }?.isOnline
        } else null,
        otherUserPhotoUrl = if (convType == ConversationType.DIRECT) {
            members.firstOrNull { it.id != userId }?.photoUrl
        } else null,
        typingText = typingText,
        isConnected = isConnected,
        lastMessageId = null  // Not used anymore - Paging3 handles scroll detection
    )
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return ""
    val date = Date(ms)
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(date)
}

