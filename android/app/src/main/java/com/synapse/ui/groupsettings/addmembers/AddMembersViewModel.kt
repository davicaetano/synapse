package com.synapse.ui.groupsettings.addmembers

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val userRepo: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    
    private val _selectedUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val _searchQuery = MutableStateFlow("")
    
    val uiState: StateFlow<AddMembersUIState> = combine(
        convRepo.observeConversation(conversationId),
        userRepo.observeUsersWithPresence(),
        _selectedUserIds,
        _searchQuery
    ) { conversation, allUsers, selected, query ->
        val currentMemberIds = conversation?.members?.keys ?: emptySet()
        
        // Filter: exclude current members and apply search
        val filteredUsers = allUsers
            .filter { user -> user.id !in currentMemberIds }
            .filter { user ->
                if (query.isBlank()) true
                else user.displayName?.contains(query, ignoreCase = true) == true
            }
        
        AddMembersUIState(
            conversationId = conversationId,
            groupName = conversation?.groupName ?: "",
            currentMemberIds = currentMemberIds,
            allUsers = filteredUsers,
            selectedUserIds = selected,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, AddMembersUIState(isLoading = true))
    
    fun toggleUserSelection(userId: String) {
        _selectedUserIds.update { current ->
            if (userId in current) {
                current - userId
            } else {
                current + userId
            }
        }
    }
    
    fun updateSearch(query: String) {
        _searchQuery.value = query
    }
    
    fun addSelectedMembers(onSuccess: () -> Unit) {
        val selected = _selectedUserIds.value
        if (selected.isEmpty()) return
        
        viewModelScope.launch {
            try {
                selected.forEach { userId ->
                    convRepo.addUserToGroup(conversationId, userId)
                }
                Log.d(TAG, "Added ${selected.size} members to group")
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding members", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "AddMembersVM"
    }
}

