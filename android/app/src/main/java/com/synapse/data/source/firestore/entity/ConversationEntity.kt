package com.synapse.data.source.firestore.entity

import com.google.firebase.Timestamp

/**
 * Member in a conversation.
 * Tracks status, permissions, and activity timestamps.
 */
data class Member(
    val lastSeenAt: Timestamp,
    val lastReceivedAt: Timestamp,
    val lastMessageSentAt: Timestamp,
    val isBot: Boolean,
    val isAdmin: Boolean,
    val isDeleted: Boolean
)

/**
 * Raw Firestore entity representing a conversation document.
 * This is NOT a domain model - it's a 1:1 mapping of Firestore data.
 */
data class ConversationEntity(
    val id: String,
    val convType: String,
    val localTimestamp: Timestamp,
    val updatedAt: Timestamp,
    val lastMessageText: String,
    val groupName: String? = null,  // Optional: Only for GROUP type
    val createdBy: String? = null,  // Optional: Only for GROUP type
    val members: Map<String, Member>  // Replaces memberIds + memberStatus (removes duplication)
)

