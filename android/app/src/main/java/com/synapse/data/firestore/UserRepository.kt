package com.synapse.data.firestore

import com.synapse.data.firebase.FirebaseDataSource
import com.synapse.data.presence.PresenceDataSource
import com.synapse.domain.user.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firebaseDataSource: FirebaseDataSource,
    private val presenceDataSource: PresenceDataSource
) {
    fun listenUsers(): Flow<List<User>> = firebaseDataSource.listenUsers()

    // Combina users do Firestore com presença do Realtime DB
    fun getUsersWithPresence(): Flow<List<User>> {
        return firebaseDataSource.listenUsers()
            .flatMapLatest { users ->
                if (users.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val userIds = users.map { it.id }
                    combine(
                        flowOf(users),
                        presenceDataSource.listenMultiplePresence(userIds)
                    ) { usersData, presenceMap ->
                        usersData.map { user ->
                            val presence = presenceMap[user.id]
                            user.copy(
                                isOnline = presence?.online ?: false,
                                lastSeenMs = presence?.lastSeenMs
                            )
                        }
                    }
                }
            }
    }

    suspend fun upsertCurrentUser() = firebaseDataSource.upsertCurrentUser()
}


