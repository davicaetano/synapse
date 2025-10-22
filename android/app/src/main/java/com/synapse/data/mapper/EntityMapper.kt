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
    // Infer online status based on lastSeenMs timestamp
    // A user is considered online if:
    // 1. presence?.online == true AND
    // 2. lastSeenMs is recent (within last 15 seconds)
    // 
    // This handles the Airplane Mode case where the client can't write
    // "offline" to Firebase, but other clients can infer offline status
    // by seeing a stale lastSeenMs timestamp.
    // 
    // Heartbeat runs every 5s, so if we miss 3 heartbeats (15s) = offline.
    val isOnline = if (presence?.online == true) {
        val lastSeenMs = presence.lastSeenMs
        if (lastSeenMs != null) {
            val nowMs = System.currentTimeMillis()
            val ageMs = nowMs - lastSeenMs
            // 15 seconds threshold: heartbeat is 5s, so if we miss 3 heartbeats = offline
            val ONLINE_THRESHOLD_MS = 15_000L
            
            ageMs < ONLINE_THRESHOLD_MS
        } else {
            // No lastSeenMs - assume offline
            false
        }
    } else {
        // presence?.online is false or null
        false
    }
    
    return User(
        id = this.id,
        displayName = this.displayName,
        photoUrl = this.photoUrl,
        isMyself = isMyself,
        isOnline = isOnline,
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

