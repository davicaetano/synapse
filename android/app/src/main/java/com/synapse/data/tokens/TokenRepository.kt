package com.synapse.data.tokens

import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.source.firestore.FirestoreFCMTokenDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for FCM token operations.
 * Now delegates to FirestoreFCMTokenDataSource.
 */
@Singleton
class TokenRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val fcmTokenDataSource: FirestoreFCMTokenDataSource
) {
    suspend fun getAndSaveCurrentToken() {
        val userId = auth.currentUser?.uid ?: return
        fcmTokenDataSource.fetchAndSaveCurrentToken(userId)
    }
}


