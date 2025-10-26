package com.synapse.data.source.firestore.entity

import com.google.firebase.Timestamp

/**
 * Raw Firestore entity representing a message document.
 * This is NOT a domain model - it's a 1:1 mapping of Firestore data.
 * 
 * Path: conversations/{conversationId}/messages/{messageId}
 */
data class MessageEntity(
    val id: String,
    val text: String,
    val senderId: String,
    val localTimestamp: Timestamp,
    val memberIdsAtCreation: List<String>,
    val type: String,
    val isDeleted: Boolean,
    val sendNotification: Boolean,
    val serverTimestamp: Timestamp? = null,  // Optional: Server-assigned (nullable for offline)
    val deletedBy: String? = null,  // Optional: Only when deleted
    val deletedAt: Timestamp? = null  // Optional: Only when deleted
)

