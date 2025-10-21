package com.synapse.ui.userpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.firestore.UserRepository
import com.synapse.data.firestore.ConversationRepository
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.user.User
import com.synapse.ui.userpicker.UserPickerItem
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
    val pickerItems: StateFlow<List<UserPickerItem>> = usersRepo.listenUsers()
        .map { users ->
            // Adiciona a opção de criar grupo no topo
            listOf(UserPickerItem.CreateGroupItem) + users.map { UserPickerItem.UserItem(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(UserPickerItem.CreateGroupItem))

    suspend fun createDirectConversation(otherUserId: String): String? =
        conversationRepo.getOrCreateDirectConversation(otherUserId)

    suspend fun createSelfConversation(): String? =
        conversationRepo.createSelfConversation()

    suspend fun createGroupConversation(memberIds: List<String>): String? =
        conversationRepo.createGroupConversation(memberIds)

    suspend fun addUserToGroup(conversationId: String, userId: String) =
        conversationRepo.addUserToGroupConversation(conversationId, userId)

    suspend fun removeUserFromGroup(conversationId: String, userId: String) =
        conversationRepo.removeUserFromGroupConversation(conversationId, userId)

    // Função para lidar com a seleção de itens do picker
    fun onItemSelected(item: UserPickerItem, onGroupCreationRequested: () -> Unit, onUserSelected: (User) -> Unit) {
        when (item) {
            is UserPickerItem.CreateGroupItem -> onGroupCreationRequested()
            is UserPickerItem.UserItem -> onUserSelected(item.user)
        }
    }
}


