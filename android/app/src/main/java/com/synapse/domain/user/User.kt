package com.synapse.domain.user

data class User(
    val id: String,
    val displayName: String?,
    val photoUrl: String? = null,
    val isMyself: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeenMs: Long? = null,
    val isSystemBot: Boolean = false  // True for system bots (e.g. Synapse Bot)
)


