package com.synapse.data.source.firestore.entity

import com.google.firebase.Timestamp

/**
 * Member status in a conversation.
 * Tracks when a user last saw, received, and sent messages.
 */
data class MemberStatus(
    val lastSeenAt: Timestamp? = null,         // Server timestamp when user last viewed conversation
    val lastReceivedAt: Timestamp? = null,     // Server timestamp when user last received messages
    val lastMessageSentAt: Timestamp? = null   // Server timestamp when user last sent a message
)

/**
 * Raw Firestore entity representing a conversation document.
 * This is NOT a domain model - it's a 1:1 mapping of Firestore data.
 */
data class ConversationEntity(
    val id: String,
    val memberIds: List<String>,
    val convType: String,
    val lastMessageText: String?,
    val updatedAtMs: Long,
    val createdAtMs: Long,
    val groupName: String? = null,
    val createdBy: String? = null,  // Creator/admin user ID (only for GROUP conversations)
    val memberStatus: Map<String, MemberStatus> = emptyMap()  // Per-user read/received timestamps
)

