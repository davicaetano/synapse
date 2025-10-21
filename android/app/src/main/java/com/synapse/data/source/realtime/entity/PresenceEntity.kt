package com.synapse.data.source.realtime.entity

/**
 * Raw Realtime Database entity representing user presence.
 * This is NOT a domain model - it's a 1:1 mapping of Realtime DB data.
 * 
 * Path: /presence/{userId}
 */
data class PresenceEntity(
    val online: Boolean = false,
    val lastSeenMs: Long? = null
)

