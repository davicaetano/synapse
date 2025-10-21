package com.synapse.data.source.firestore

import android.os.Build
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.synapse.data.source.firestore.entity.FCMTokenEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource for accessing FCM tokens subcollection.
 * Path: users/{userId}/fcmTokens/{tokenId}
 * 
 * RESPONSIBILITY: Raw CRUD operations on FCM tokens only.
 */
@Singleton
class FirestoreFCMTokenDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {
    
    /**
     * Fetch current FCM token from Firebase Messaging.
     */
    suspend fun getCurrentToken(): String? {
        return try {
            messaging.token.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch FCM token", e)
            null
        }
    }
    
    /**
     * Save an FCM token for a user.
     */
    suspend fun saveToken(userId: String, token: String) {
        val docRef = firestore.collection("users")
            .document(userId)
            .collection("fcmTokens")
            .document(token)
        
        val data = mapOf(
            "createdAt" to Timestamp.now(),
            "platform" to "android",
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "sdk" to Build.VERSION.SDK_INT
        )
        
        try {
            docRef.set(data).await()
            Log.d(TAG, "Saved FCM token for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save FCM token", e)
        }
    }
    
    /**
     * Fetch current token and save it.
     */
    suspend fun fetchAndSaveCurrentToken(userId: String) {
        val token = getCurrentToken()
        if (token != null) {
            saveToken(userId, token)
        }
    }
    
    /**
     * Remove a specific FCM token.
     */
    suspend fun removeToken(userId: String, token: String) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("fcmTokens")
                .document(token)
                .delete()
                .await()
            Log.d(TAG, "Removed FCM token for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove FCM token", e)
        }
    }
    
    companion object {
        private const val TAG = "FirestoreFCMTokenDS"
    }
}

