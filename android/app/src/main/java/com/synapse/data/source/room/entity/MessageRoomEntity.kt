package com.synapse.data.source.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.synapse.data.source.firestore.entity.MessageEntity

/**
 * Room entity for caching messages locally.
 * Provides instant read performance compared to Firestore deserializing.
 * 
 * Status tracking is now done via conversation.memberStatus timestamps.
 * Old per-message fields (readBy, receivedBy, etc) removed for simplicity.
 */
@Entity(tableName = "messages")
data class MessageRoomEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val text: String,
    val senderId: String,
    val createdAtMs: Long,
    val memberIdsAtCreation: String,  // Stored as comma-separated
    val serverTimestamp: Long?,
    val type: String = "text",  // Message type: "text", "AI_SUMMARY"
    val isDeleted: Boolean = false,  // Soft delete flag
    val deletedBy: String? = null,  // User who deleted the message
    val deletedAtMs: Long? = null  // Timestamp of deletion
) {
    /**
     * Convert Room entity back to Firestore entity.
     */
    fun toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            text = text,
            senderId = senderId,
            createdAtMs = createdAtMs,
            memberIdsAtCreation = memberIdsAtCreation.split(",").filter { it.isNotBlank() },
            serverTimestamp = serverTimestamp,
            type = type,
            isDeleted = isDeleted,
            deletedBy = deletedBy,
            deletedAtMs = deletedAtMs
        )
    }
    
    companion object {
        /**
         * Convert Firestore entity to Room entity.
         */
        fun fromEntity(entity: MessageEntity, conversationId: String): MessageRoomEntity {
            return MessageRoomEntity(
                id = entity.id,
                conversationId = conversationId,
                text = entity.text,
                senderId = entity.senderId,
                createdAtMs = entity.createdAtMs,
                memberIdsAtCreation = entity.memberIdsAtCreation.joinToString(","),
                serverTimestamp = entity.serverTimestamp,
                type = entity.type,
                isDeleted = entity.isDeleted,
                deletedBy = entity.deletedBy,
                deletedAtMs = entity.deletedAtMs
            )
        }
    }
}

