package com.synapse.data.source.firestore.entity

/**
 * Raw Firestore entity representing a message document.
 * This is NOT a domain model - it's a 1:1 mapping of Firestore data.
 * 
 * Status tracking is now done via conversation.memberStatus timestamps.
 * Old per-message fields (readBy, receivedBy, etc) are obsolete.
 */
data class MessageEntity(
    val id: String,
    val text: String,
    val senderId: String,
    val createdAtMs: Long,
    val memberIdsAtCreation: List<String> = emptyList(),  // Snapshot of group members when message was created
    val serverTimestamp: Long? = null,  // Server-assigned timestamp (null = never reached server)
    val type: String = "text",  // Message type: "text", "AI_SUMMARY"
    val isDeleted: Boolean = false,  // Soft delete flag
    val deletedBy: String? = null,  // User who deleted the message
    val deletedAtMs: Long? = null  // Timestamp of deletion
)

