package com.synapse.data.source.firestore.entity

/**
 * Raw Firestore entity representing an FCM token document.
 * Path: users/{userId}/fcmTokens/{tokenId}
 */
data class FCMTokenEntity(
    val token: String,
    val createdAtMs: Long,
    val platform: String,
    val brand: String?,
    val model: String?,
    val sdk: Int?
)

