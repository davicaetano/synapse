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
 * Recalculate message status based on current members.
 * Used for real-time checkmark updates in UI.
 */
fun Message.recalculateStatus(members: Map<String, com.synapse.data.source.firestore.entity.Member>): MessageStatus {
    // Get all other members (exclude sender)
    val otherMembers = memberIdsAtCreation.filter { it != senderId }
    
    val status = when {
        // PENDING: serverTimestamp is null (never reached server)
        serverTimestamp == null -> MessageStatus.PENDING
        
        // For single-user conversations (SELF), always mark as READ
        otherMembers.isEmpty() -> MessageStatus.READ
        
        // SENT: No other members received yet
        otherMembers.all { userId ->
            val member = members[userId]
            val lastReceivedMs = member?.lastReceivedAt?.toDate()?.time
            lastReceivedMs == null || serverTimestamp > lastReceivedMs
        } -> MessageStatus.SENT
        
        // READ: All other members have seen this message
        otherMembers.all { userId ->
            val member = members[userId]
            val lastSeenMs = member?.lastSeenAt?.toDate()?.time
            val isRead = lastSeenMs != null && serverTimestamp <= lastSeenMs
            
            android.util.Log.d("MessageStatus", "📖 Check READ: userId=${userId.takeLast(6)}, lastSeenMs=$lastSeenMs, serverTs=$serverTimestamp, isRead=$isRead")
            
            isRead
        } -> MessageStatus.READ
        
        // DELIVERED: At least one other member received but not everyone read yet
        else -> MessageStatus.DELIVERED
    }
    
    android.util.Log.d("MessageStatus", "✅ Final status for msg ${id.takeLast(6)}: $status (serverTs=$serverTimestamp, otherMembers=${otherMembers.size})")
    
    return status
}


