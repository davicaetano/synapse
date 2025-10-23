package com.synapse.data.source.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.synapse.data.source.firestore.entity.MessageEntity

/**
 * Room entity for caching messages locally.
 * Provides instant read performance compared to Firestore deserializing.
 */
@Entity(tableName = "messages")
data class MessageRoomEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val text: String,
    val senderId: String,
    val createdAtMs: Long,
    val receivedBy: String,  // Stored as comma-separated: "user1,user2,user3"
    val readBy: String,  // Stored as comma-separated: "user1,user2"
    val notReceivedBy: String,  // Stored as comma-separated
    val notReadBy: String,  // Stored as comma-separated
    val memberIdsAtCreation: String,  // Stored as comma-separated
    val serverTimestamp: Long?
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
            receivedBy = receivedBy.split(",").filter { it.isNotBlank() },
            readBy = readBy.split(",").filter { it.isNotBlank() },
            notReceivedBy = notReceivedBy.split(",").filter { it.isNotBlank() },
            notReadBy = notReadBy.split(",").filter { it.isNotBlank() },
            memberIdsAtCreation = memberIdsAtCreation.split(",").filter { it.isNotBlank() },
            serverTimestamp = serverTimestamp
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
                receivedBy = entity.receivedBy.joinToString(","),
                readBy = entity.readBy.joinToString(","),
                notReceivedBy = entity.notReceivedBy.joinToString(","),
                notReadBy = entity.notReadBy.joinToString(","),
                memberIdsAtCreation = entity.memberIdsAtCreation.joinToString(","),
                serverTimestamp = entity.serverTimestamp
            )
        }
    }
}

