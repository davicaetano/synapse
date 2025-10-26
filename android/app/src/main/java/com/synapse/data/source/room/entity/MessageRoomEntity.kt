package com.synapse.data.source.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.synapse.data.source.firestore.entity.MessageEntity

/**
 * Room entity for caching messages locally.
 * Provides instant read performance compared to Firestore deserializing.
 * 
 * NOTE: Room stores timestamps as Long (ms) because SQLite doesn't have Timestamp type.
 * Conversions to/from Firestore Timestamp happen in toEntity() and fromEntity().
 */
@Entity(tableName = "messages")
data class MessageRoomEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val text: String,
    val senderId: String,
    val createdAtMs: Long,  // SQLite: Long ms
    val memberIdsAtCreation: String,  // Stored as comma-separated
    val serverTimestampMs: Long?,  // SQLite: Long ms (nullable for offline)
    val type: String = "text",
    val isDeleted: Boolean = false,
    val sendNotification: Boolean = true,
    val deletedBy: String? = null,
    val deletedAtMs: Long? = null  // SQLite: Long ms
) {
    /**
     * Convert Room entity back to Firestore entity.
     * Converts Long ms → Timestamp.
     */
    fun toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            text = text,
            senderId = senderId,
            localTimestamp = Timestamp(java.util.Date(createdAtMs)),  // Long ms → Timestamp
            memberIdsAtCreation = memberIdsAtCreation.split(",").filter { it.isNotBlank() },
            type = type,
            isDeleted = isDeleted,
            sendNotification = sendNotification,
            serverTimestamp = serverTimestampMs?.let { Timestamp(java.util.Date(it)) },  // Long ms → Timestamp
            deletedBy = deletedBy,
            deletedAt = deletedAtMs?.let { Timestamp(java.util.Date(it)) }  // Long ms → Timestamp
        )
    }
    
    companion object {
        /**
         * Convert Firestore entity to Room entity.
         * Converts Timestamp → Long ms.
         */
        fun fromEntity(entity: MessageEntity, conversationId: String): MessageRoomEntity {
            return MessageRoomEntity(
                id = entity.id,
                conversationId = conversationId,
                text = entity.text,
                senderId = entity.senderId,
                createdAtMs = entity.localTimestamp.toDate().time,  // Timestamp → Long ms
                memberIdsAtCreation = entity.memberIdsAtCreation.joinToString(","),
                serverTimestampMs = entity.serverTimestamp?.toDate()?.time,  // Timestamp → Long ms
                type = entity.type,
                isDeleted = entity.isDeleted,
                sendNotification = entity.sendNotification,
                deletedBy = entity.deletedBy,
                deletedAtMs = entity.deletedAt?.toDate()?.time  // Timestamp → Long ms
            )
        }
    }
}

