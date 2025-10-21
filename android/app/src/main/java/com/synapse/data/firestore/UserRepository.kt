package com.synapse.data.firestore

import com.synapse.data.firebase.FirebaseDataSource
import com.synapse.domain.user.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firebaseDataSource: FirebaseDataSource
) {
    fun listenUsers(): Flow<List<User>> = firebaseDataSource.listenUsers()

    suspend fun upsertCurrentUser() = firebaseDataSource.upsertCurrentUser()
}


