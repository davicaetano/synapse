package com.synapse.data.source.firestore.entity

/**
 * Raw Firestore entity representing a user document.
 * This is NOT a domain model - it's a 1:1 mapping of Firestore data.
 * 
 * Note: Presence data (isOnline, lastSeenMs) is stored in Realtime Database,
 * not in this Firestore entity.
 */
data class UserEntity(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

