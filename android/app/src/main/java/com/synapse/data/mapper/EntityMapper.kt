package com.synapse.data.mapper

import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.MemberStatus
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
    memberCount: Int,
    memberStatus: Map<String, MemberStatus>
): Message {
    // Calculate status based on memberStatus timestamps (conversation-level tracking)
    // Get all other members (exclude sender)
    val otherMembers = memberIdsAtCreation.filter { it != senderId }
    
    val status = when {
        // PENDING: serverTimestamp is null (never reached server)
        serverTimestamp == null -> MessageStatus.PENDING
        
        // For single-user conversations (SELF), always mark as READ
        otherMembers.isEmpty() -> MessageStatus.READ
        
        // SENT: No other members received yet
        otherMembers.all { userId ->
            val userStatus = memberStatus[userId]
            val lastReceivedMs = userStatus?.lastReceivedAt?.toDate()?.time
            lastReceivedMs == null || serverTimestamp!! > lastReceivedMs
        } -> MessageStatus.SENT
        
        // READ: All other members have seen this message
        otherMembers.all { userId ->
            val userStatus = memberStatus[userId]
            val lastSeenMs = userStatus?.lastSeenAt?.toDate()?.time
            lastSeenMs != null && serverTimestamp!! <= lastSeenMs
        } -> MessageStatus.READ
        
        // DELIVERED: At least one other member received but not everyone read yet
        else -> MessageStatus.DELIVERED
    }
    
    return Message(
        id = this.id,
        text = this.text,
        senderId = this.senderId,
        createdAtMs = this.createdAtMs,
        isMine = (currentUserId != null && this.senderId == currentUserId),
        receivedBy = emptyList(),  // Not used anymore
        readBy = emptyList(),  // Not used anymore
        isReadByEveryone = status == MessageStatus.READ,
        status = status,
        type = this.type
    )
}

