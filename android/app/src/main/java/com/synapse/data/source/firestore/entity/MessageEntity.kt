package com.synapse.data.source.firestore.entity

/**
 * Raw Firestore entity representing a message document.
 * This is NOT a domain model - it's a 1:1 mapping of Firestore data.
 */
data class MessageEntity(
    val id: String,
    val text: String,
    val senderId: String,
    val createdAtMs: Long,
    val receivedBy: List<String>,  // List of user IDs who received this message
    val readBy: List<String>,      // List of user IDs who read this message
    val notReceivedBy: List<String> = emptyList(),  // List of user IDs who have NOT received yet
    val memberIdsAtCreation: List<String> = emptyList(),  // Snapshot of group members when message was created
    val serverTimestamp: Long? = null  // Server-assigned timestamp (null = never reached server)
)

