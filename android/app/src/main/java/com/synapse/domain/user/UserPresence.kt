package com.synapse.domain.user

data class UserPresence(
    val online: Boolean = false,
    val lastSeenMs: Long? = null
)

