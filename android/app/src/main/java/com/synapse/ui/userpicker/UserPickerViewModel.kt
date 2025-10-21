package com.synapse.ui.userpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.firestore.UserRepository
import com.synapse.data.firestore.ConversationRepository
import com.synapse.domain.user.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class UserPickerViewModel @Inject constructor(
    private val usersRepo: UserRepository,
    private val conversationRepo: ConversationRepository
) : ViewModel() {
    val users: StateFlow<List<User>> = usersRepo.listenUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createDirectConversation(otherUserId: String): String? =
        conversationRepo.getOrCreateDirectConversation(otherUserId)
}


