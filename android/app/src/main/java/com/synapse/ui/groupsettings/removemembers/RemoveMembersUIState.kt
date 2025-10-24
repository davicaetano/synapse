package com.synapse.ui.groupsettings.removemembers

import com.synapse.domain.user.User

data class RemoveMembersUIState(
    val conversationId: String = "",
    val groupName: String = "",
    val members: List<User> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val createdBy: String = "",
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isRemoving: Boolean = false
)

