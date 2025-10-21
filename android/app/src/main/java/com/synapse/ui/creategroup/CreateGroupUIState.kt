package com.synapse.ui.creategroup

import com.synapse.domain.user.User

/**
 * Wrapper for a user with selection state.
 */
data class SelectableUser(
    val user: User,
    val isSelected: Boolean = false
)

/**
 * UI state for create group screen.
 */
data class CreateGroupUIState(
    val selectableUsers: List<SelectableUser> = emptyList(),
    val groupName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    // Computed properties
    val selectedCount: Int
        get() = selectableUsers.count { it.isSelected }
    
    val selectedUserIds: List<String>
        get() = selectableUsers.filter { it.isSelected }.map { it.user.id }
    
    // Can create even with 0 selected (group with just yourself)
    val canCreate: Boolean
        get() = !isLoading
}

