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
    val type: String = "text",  // Message type: "text", "AI_SUMMARY"
    val memberIdsAtCreation: List<String> = emptyList(),  // Member IDs at message creation (for status calculation)
    val serverTimestamp: Long? = null  // Server timestamp (null = PENDING)
)

/**
 * Recalculate message status based on current memberStatus.
 * Used for real-time checkmark updates in UI.
 */
fun Message.recalculateStatus(memberStatus: Map<String, com.synapse.data.source.firestore.entity.MemberStatus>): MessageStatus {
    // Get all other members (exclude sender)
    val otherMembers = memberIdsAtCreation.filter { it != senderId }
    
    return when {
        // PENDING: serverTimestamp is null (never reached server)
        serverTimestamp == null -> MessageStatus.PENDING
        
        // For single-user conversations (SELF), always mark as READ
        otherMembers.isEmpty() -> MessageStatus.READ
        
        // SENT: No other members received yet
        otherMembers.all { userId ->
            val userStatus = memberStatus[userId]
            val lastReceivedMs = userStatus?.lastReceivedAt?.toDate()?.time
            lastReceivedMs == null || serverTimestamp > lastReceivedMs
        } -> MessageStatus.SENT
        
        // READ: All other members have seen this message
        otherMembers.all { userId ->
            val userStatus = memberStatus[userId]
            val lastSeenMs = userStatus?.lastSeenAt?.toDate()?.time
            lastSeenMs != null && serverTimestamp <= lastSeenMs
        } -> MessageStatus.READ
        
        // DELIVERED: At least one other member received but not everyone read yet
        else -> MessageStatus.DELIVERED
    }
}


