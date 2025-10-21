package com.synapse.ui.userpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.repository.UserRepository
import com.synapse.data.repository.ConversationRepository
import com.synapse.domain.user.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class UserPickerViewModel @Inject constructor(
    private val usersRepo: UserRepository,
    private val conversationRepo: ConversationRepository
) : ViewModel() {
    val pickerItems: StateFlow<List<UserPickerItem>> = usersRepo.observeUsersWithPresence()
        .map { users ->
            // Add the option to create a group at the top
            listOf(UserPickerItem.CreateGroupItem) + users.map { UserPickerItem.UserItem(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(UserPickerItem.CreateGroupItem))

    suspend fun createDirectConversation(user: User): String? =
        conversationRepo.getOrCreateDirectConversation(user.id)

    suspend fun createSelfConversation(): String? =
        conversationRepo.createSelfConversation()

    // Main method that decides which type of conversation to create
    suspend fun createConversation(user: User): String? {
        return when {
            user.isMyself -> createSelfConversation()
            else -> createDirectConversation(user)
        }
    }

    suspend fun createGroupConversation(memberIds: List<String>): String? =
        conversationRepo.createGroupConversation(memberIds)

    suspend fun addUserToGroup(conversationId: String, userId: String) =
        conversationRepo.addUserToGroup(conversationId, userId)

    suspend fun removeUserFromGroup(conversationId: String, userId: String) =
        conversationRepo.removeUserFromGroup(conversationId, userId)

    // Function to handle picker item selection
    fun onItemSelected(item: UserPickerItem, onGroupCreationRequested: () -> Unit, onUserSelected: (User) -> Unit) {
        when (item) {
            is UserPickerItem.CreateGroupItem -> onGroupCreationRequested()
            is UserPickerItem.UserItem -> onUserSelected(item.user)
        }
    }
}


