package com.synapse.ui.groupsettings.removemembers

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoveMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    private val currentUserId = auth.currentUser?.uid ?: ""
    
    private val _selectedUserIds = MutableStateFlow<Set<String>>(emptySet())
    
    val uiState: StateFlow<RemoveMembersUIState> = combine(
        convRepo.observeConversation(currentUserId, conversationId),
        _selectedUserIds
    ) { conversation, selected ->
        if (conversation == null) {
            RemoveMembersUIState(isLoading = true)
        } else {
            RemoveMembersUIState(
                conversationId = conversationId,
                groupName = conversation.groupName ?: "",
                members = emptyList(),  // Will be filled from separate flow
                selectedUserIds = selected,
                createdBy = conversation.createdBy ?: "",
                currentUserId = currentUserId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, RemoveMembersUIState(isLoading = true))
    
    // Observe members separately
    val members: StateFlow<List<com.synapse.domain.user.User>> = run {
        convRepo.observeConversation(currentUserId, conversationId)
            .flatMapLatest { conversation ->
                if (conversation == null || conversation.memberIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    convRepo.observeUsers(conversation.memberIds)
                        .map { userEntities ->
                            userEntities.map { it.toDomain(presence = null, isMyself = it.id == currentUserId) }
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }
    
    fun toggleUserSelection(userId: String) {
        _selectedUserIds.update { current ->
            if (userId in current) {
                current - userId
            } else {
                current + userId
            }
        }
    }
    
    fun removeSelectedMembers(onSuccess: () -> Unit) {
        val selected = _selectedUserIds.value
        if (selected.isEmpty()) return
        
        viewModelScope.launch {
            try {
                selected.forEach { userId ->
                    convRepo.removeUserFromGroup(conversationId, userId)
                }
                Log.d(TAG, "Removed ${selected.size} members from group")
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error removing members", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "RemoveMembersVM"
    }
}

