package com.synapse.di

import android.content.Context
import androidx.room.Room
import com.synapse.data.source.room.SynapseDatabase
import com.synapse.data.source.room.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing Room database and DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SynapseDatabase {
        return Room.databaseBuilder(
            context,
            SynapseDatabase::class.java,
            "synapse-db"
        )
            .fallbackToDestructiveMigration()  // For development - recreate DB on schema changes
            .build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: SynapseDatabase): MessageDao {
        return database.messageDao()
    }
}

