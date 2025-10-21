package com.synapse.domain.user

data class User(
    val id: String,
    val displayName: String?,
    val photoUrl: String? = null,
    val isMyself: Boolean = false
)


