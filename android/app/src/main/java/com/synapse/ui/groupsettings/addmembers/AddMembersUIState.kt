package com.synapse.ui.groupsettings.addmembers

import com.synapse.domain.user.User

data class AddMembersUIState(
    val conversationId: String = "",
    val groupName: String = "",
    val currentMemberIds: Set<String> = emptySet(),
    val allUsers: List<User> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false
)

