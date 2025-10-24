package com.synapse.data.source.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.synapse.data.source.room.dao.MessageDao
import com.synapse.data.source.room.entity.MessageRoomEntity

/**
 * Room database for local caching.
 * Provides instant reads compared to Firestore deserializing.
 */
@Database(
    entities = [MessageRoomEntity::class],
    version = 2,  // Incremented for new fields: type, isDeleted, deletedBy, deletedAtMs
    exportSchema = false
)
abstract class SynapseDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}

