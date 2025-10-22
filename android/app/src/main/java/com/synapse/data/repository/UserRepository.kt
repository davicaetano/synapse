package com.synapse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.source.firestore.FirestoreUserDataSource
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import com.synapse.domain.user.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user operations.
 * 
 * RESPONSIBILITY: Business logic - combining User + Presence data.
 * - Combines: Firestore User + Realtime DB Presence
 * - Transforms entities to domain models
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UserRepository @Inject constructor(
    private val userDataSource: FirestoreUserDataSource,
    private val presenceDataSource: RealtimePresenceDataSource,
    private val auth: FirebaseAuth
) {
    
    /**
     * Observe all users WITHOUT presence data.
     * Lightweight - use when you don't need online/offline status.
     */
    fun observeUsers(): Flow<List<User>> {
        val currentUserId = auth.currentUser?.uid
        
        return userDataSource.listenAllUsers().flatMapLatest { userEntities ->
            flowOf(
                userEntities.map { userEntity ->
                    userEntity.toDomain(
                        presence = null,
                        isMyself = (userEntity.id == currentUserId)
                    )
                }
            )
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

