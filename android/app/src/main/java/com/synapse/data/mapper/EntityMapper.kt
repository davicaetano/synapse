package com.synapse.data.mapper

import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.MessageEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.data.source.realtime.entity.PresenceEntity
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import com.synapse.domain.user.User

/**
 * Mappers to convert entities (raw data) to domain models.
 */

// User Entity → Domain
fun UserEntity.toDomain(
    presence: PresenceEntity? = null,
    isMyself: Boolean = false
): User {
    return User(
        id = this.id,
        displayName = this.displayName,
        photoUrl = this.photoUrl,
        isMyself = isMyself,
        isOnline = presence?.online ?: false,
        lastSeenMs = presence?.lastSeenMs
    )
}

// Conversation Entity → Domain
fun ConversationEntity.toDomain(members: List<User>): ConversationSummary {
    return ConversationSummary(
        id = this.id,
        lastMessageText = this.lastMessageText,
        updatedAtMs = this.updatedAtMs,
        members = members,
        convType = try {
            ConversationType.valueOf(this.convType)
        } catch (e: IllegalArgumentException) {
            ConversationType.DIRECT
        },
        createdBy = this.createdBy,
        groupName = this.groupName
    )
}

// Message Entity → Domain
fun MessageEntity.toDomain(
    currentUserId: String?,
    memberCount: Int
): Message {
    return Message(
        id = this.id,
        text = this.text,
        senderId = this.senderId,
        createdAtMs = this.createdAtMs,
        isMine = (currentUserId != null && this.senderId == currentUserId),
        receivedBy = emptyList(), // For now, we don't populate full User objects
        readBy = emptyList(),
        isReadByEveryone = this.readBy.size >= memberCount
    )
}

