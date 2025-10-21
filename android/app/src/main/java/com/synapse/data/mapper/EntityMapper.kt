package com.synapse.data.mapper

import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.MessageEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.data.source.realtime.entity.PresenceEntity
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import com.synapse.domain.conversation.MessageStatus
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
    // Calculate how many other members should receive/read this message
    // (exclude the sender from the count)
    val otherMembersCount = memberCount - 1
    
    // Determine message status based on WhatsApp logic:
    // Order matters! Check from most restrictive to least restrictive:
    // 1. PENDING: serverTimestamp is null (never reached server)
    // 2. SENT: Only sender in receivedBy (receivedBy <= 1)
    // 3. READ: Everyone read (readBy >= memberCount, includes sender)
    // 4. DELIVERED: Others received but not everyone read yet
    val status = when {
        serverTimestamp == null -> MessageStatus.PENDING
        receivedBy.size <= 1 -> MessageStatus.SENT          // Only sender received
        readBy.size >= memberCount -> MessageStatus.READ    // Everyone read (includes sender)
        receivedBy.size > 1 -> MessageStatus.DELIVERED      // Someone else received
        else -> MessageStatus.SENT
    }
    
    return Message(
        id = this.id,
        text = this.text,
        senderId = this.senderId,
        createdAtMs = this.createdAtMs,
        isMine = (currentUserId != null && this.senderId == currentUserId),
        receivedBy = emptyList(), // For now, we don't populate full User objects
        readBy = emptyList(),
        isReadByEveryone = this.readBy.size >= memberCount,
        status = status
    )
}

