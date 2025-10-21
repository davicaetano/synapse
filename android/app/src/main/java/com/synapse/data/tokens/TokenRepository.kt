package com.synapse.data.tokens

import android.os.Build
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) {
    suspend fun getAndSaveCurrentToken() {
        val user = auth.currentUser ?: return
        try {
            val token = messaging.token.await()
            Log.d(TAG, "Fetched FCM token: ${'$'}token")
            saveToken(user.uid, token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch FCM token", e)
        }
    }

    suspend fun saveToken(userId: String, token: String) {
        val docRef = firestore.collection("users").document(userId)
            .collection("fcmTokens").document(token)
        val data = mapOf(
            "createdAt" to Timestamp.now(),
            "platform" to "android",
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "sdk" to Build.VERSION.SDK_INT
        )
        try {
            docRef.set(data).await()
            Log.d(TAG, "Saved FCM token for user ${'$'}userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save FCM token for user ${'$'}userId", e)
        }
    }

    companion object { private const val TAG = "TokenRepository" }
}


