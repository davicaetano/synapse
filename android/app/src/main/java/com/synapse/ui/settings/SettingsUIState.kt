package com.synapse.ui.settings

/**
 * UI state for Settings screen.
 */
data class SettingsUIState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val userId: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

