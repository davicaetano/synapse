package com.synapse.ui.groupsettings

import com.synapse.domain.user.User

/**
 * UI state for Group Settings screen.
 */
data class GroupSettingsUIState(
    val conversationId: String = "",
    val groupName: String = "",
    val members: List<User> = emptyList(),
    val isUserAdmin: Boolean = false,
    val createdBy: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

