package com.synapse.data.source.firestore.entity

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
    val createdBy: String? = null  // Creator/admin user ID (only for GROUP conversations)
)

