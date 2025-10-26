package com.synapse.util

import java.util.concurrent.TimeUnit

/**
 * Format last seen time in a human-readable way.
 * Returns null if user is currently online.
 * 
 * Examples:
 * - "just now" (< 1 minute)
 * - "5m ago" (< 1 hour)
 * - "2h ago" (< 1 day)
 * - "3d ago" (< 1 week)
 * - "long ago" (>= 1 week)
 */
fun formatLastSeen(lastSeenMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - lastSeenMs
    
    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diffMs < TimeUnit.HOURS.toMillis(1) -> {
            val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            "${mins}m ago"
        }
        diffMs < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
            "${hours}h ago"
        }
        diffMs < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)
            "${days}d ago"
        }
        else -> "long ago"
    }
}

/**
 * Get presence status text for a user.
 * Returns "online" if user is online, otherwise formatted last seen time.
 * 
 * @param isOnline Whether user is currently online
 * @param lastSeenMs User's last seen timestamp
 * @return Status text like "online", "5m ago", "2h ago", etc.
 */
fun getPresenceStatus(isOnline: Boolean, lastSeenMs: Long?): String? {
    return when {
        isOnline -> "online"
        lastSeenMs != null -> formatLastSeen(lastSeenMs)
        else -> null
    }
}

