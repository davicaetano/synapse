package com.synapse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.source.firestore.FirestoreUserDataSource
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import com.synapse.domain.user.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user operations.
 * 
 * RESPONSIBILITY: Simple data access - expose flows from DataSources.
 * - NO complex transformations
 * - ViewModel handles combining with presence if needed
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDataSource: FirestoreUserDataSource,
    private val presenceDataSource: RealtimePresenceDataSource,
    private val auth: FirebaseAuth
) {
    
    /**
     * Observe all users WITHOUT presence data.
     * Simple transformation to domain model.
     */
    fun observeUsers(): Flow<List<User>> {
        val currentUserId = auth.currentUser?.uid
        
        return userDataSource.listenAllUsers().map { userEntities ->
            userEntities.map { userEntity ->
                userEntity.toDomain(
                    presence = null,
                    isMyself = (userEntity.id == currentUserId)
                )
            }
        }
    }
    
    /**
     * Observe all users WITH presence data (online/offline, last seen).
     * Use for inbox, user picker, etc.
     */
    fun observeUsersWithPresence(): Flow<List<User>> {
        val currentUserId = auth.currentUser?.uid
        
        return userDataSource.listenAllUsers()
            .flatMapLatest { userEntities ->
                if (userEntities.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val userIds = userEntities.map { it.id }
                    
                    combine(
                        flowOf(userEntities),
                        presenceDataSource.listenMultiplePresence(userIds)
                    ) { users, presenceMap ->
                        users.map { userEntity ->
                            val presence = presenceMap[userEntity.id]
                            userEntity.toDomain(
                                presence = presence,
                                isMyself = (userEntity.id == currentUserId)
                            )
                        }
                    }
                }
            }
    }
    
    /**
     * Upsert current authenticated user.
     * Creates or updates user profile based on Firebase Auth data.
     */
    suspend fun upsertCurrentUser() {
        userDataSource.upsertCurrentUser()
    }
    
    /**
     * Update user profile.
     */
    suspend fun updateUserProfile(displayName: String?, photoUrl: String?) {
        val userId = auth.currentUser?.uid ?: return
        userDataSource.updateUserProfile(userId, displayName, photoUrl)
    }
}

