package com.synapse.ui.creategroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CreateGroupUIState())
    val uiState: StateFlow<CreateGroupUIState> = _uiState.asStateFlow()
    
    init {
        // Load all users with presence
        viewModelScope.launch {
            userRepository.observeUsersWithPresence()
                .collect { users ->
                    val selectableUsers = users
                        .filter { !it.isMyself }  // Exclude current user from selection
                        .map { user ->
                            // Preserve selection state if user was already in list
                            val existing = _uiState.value.selectableUsers
                                .find { it.user.id == user.id }
                            
                            SelectableUser(
                                user = user,
                                isSelected = existing?.isSelected ?: false
                            )
                        }
                    
                    _uiState.update { it.copy(selectableUsers = selectableUsers) }
                }
        }
    }
    
    /**
     * Toggle selection for a user.
     */
    fun toggleUserSelection(userId: String) {
        _uiState.update { state ->
            val updatedUsers = state.selectableUsers.map { selectable ->
                if (selectable.user.id == userId) {
                    selectable.copy(isSelected = !selectable.isSelected)
                } else {
                    selectable
                }
            }
            
            state.copy(selectableUsers = updatedUsers)
        }
    }
    
    /**
     * Update group name.
     */
    fun setGroupName(name: String) {
        _uiState.update { it.copy(groupName = name) }
    }
    
    /**
     * Create the group conversation.
     * Returns conversation ID if successful, null otherwise.
     */
    suspend fun createGroup(): String? {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        val selectedUserIds = _uiState.value.selectedUserIds
        val groupName = _uiState.value.groupName.trim().ifBlank { null }
        
        return try {
            val conversationId = conversationRepository.createGroupConversation(
                memberIds = selectedUserIds,  // Can be empty (just you)
                groupName = groupName
            )
            
            if (conversationId != null) {
                _uiState.update { it.copy(isLoading = false) }
                conversationId
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to create group"
                    )
                }
                null
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
            null
        }
    }
}

