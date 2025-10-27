package com.synapse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.synapse.data.mapper.toDomain
import com.synapse.data.source.firestore.FirestoreUserDataSource
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import com.synapse.domain.user.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user operations.
 * 
 * RESPONSIBILITY: Simple data access - expose flows from DataSources.
 * - NO complex transformations
 * - ViewModel handles combining with presence if needed
 * 
 * NOW REACTIVE:
 * - Observes auth state changes (login/logout)
 * - Re-evaluates presence listeners when user ID changes
 */
@Singleton
class UserRepository @Inject constructor(
    private val userDataSource: FirestoreUserDataSource,
    private val presenceDataSource: RealtimePresenceDataSource,
    private val auth: FirebaseAuth
) {
    
    /**
     * Flow that emits the current user's UID whenever auth state changes.
     * Emits null when logged out, non-null UID when logged in.
     */
    private val currentUserIdFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        
        // Add listener
        auth.addAuthStateListener(listener)
        
        // Emit current state immediately
        trySend(auth.currentUser?.uid)
        
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged()  // Only emit when UID actually changes
    
    /**
     * Observe all users WITH presence data (online/offline, last seen).
     * Use for inbox, user picker, etc.
     * 
     * NOW REACTIVE:
     * - Re-evaluates when auth state changes (login/logout)
     * - Presence listeners start working immediately after login
     */
    fun observeUsersWithPresence(): Flow<List<User>> {
        return currentUserIdFlow.flatMapLatest { currentUserId ->
            userDataSource.listenAllUsers()
                .flatMapLatest { userEntities ->
                    // Filter out system bots (e.g. Synapse Bot)
                    val realUsers = userEntities.filter { !it.isSystemBot }
                    
                    if (realUsers.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val userIds = realUsers.map { it.id }
                        
                        combine(
                            flowOf(realUsers),
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
    }
    
    /**
     * Upsert current authenticated user.
     * Creates or updates user profile based on Firebase Auth data.
     */
    suspend fun upsertCurrentUser() {
        userDataSource.upsertCurrentUser()
    }
    
    /**
     * Observe a single user by ID (for settings screen).
     */
    fun observeUser(userId: String): Flow<com.synapse.data.source.firestore.entity.UserEntity?> {
        return userDataSource.listenUser(userId)
    }
    
    /**
     * Update user display name.
     */
    suspend fun updateDisplayName(userId: String, displayName: String) {
        userDataSource.updateUserProfile(userId, displayName = displayName)
    }
    
    /**
     * Update user email.
     */
    suspend fun updateEmail(userId: String, email: String) {
        // Update email field directly in Firestore
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update(mapOf(
                "email" to email,
                "updatedAtMs" to System.currentTimeMillis()
            ))
            .await()
    }
    
    /**
     * Update user profile.
     */
    suspend fun updateUserProfile(displayName: String?, photoUrl: String?) {
        val userId = auth.currentUser?.uid ?: return
        userDataSource.updateUserProfile(userId, displayName, photoUrl)
    }
}

