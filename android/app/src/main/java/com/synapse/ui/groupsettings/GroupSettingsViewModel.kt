package com.synapse.ui.groupsettings

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
class GroupSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    
    val uiState: StateFlow<GroupSettingsUIState> = run {
        val userId = auth.currentUser?.uid ?: ""
        
        convRepo.observeConversation(conversationId)
            .map { conversation ->
                if (conversation == null) {
                    GroupSettingsUIState(isLoading = true)
                } else {


                    GroupSettingsUIState(
                        conversationId = conversation.id,
                        groupName = conversation.groupName ?: "Unnamed Group",
                        members = emptyList(),  // Will be filled by combining with users
                        isUserAdmin = conversation.createdBy == userId,
                        createdBy = conversation.createdBy ?: "",
                        isLoading = false
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                GroupSettingsUIState(isLoading = true)
            )
    }
    
    // Observe members separately and combine with uiState
    val members: StateFlow<List<com.synapse.domain.user.User>> = run {
        val userId = auth.currentUser?.uid ?: ""
        
        convRepo.observeConversation(conversationId)
            .flatMapLatest { conversation ->
                if (conversation == null || conversation.members.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // Get active member IDs (exclude deleted and bots)
                    val activeMemberIds = conversation.members
                        .filterValues { !it.isDeleted && !it.isBot }
                        .keys
                        .toList()
                    
                    if (activeMemberIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        convRepo.observeUsers(activeMemberIds)
                            .map { userEntities ->
                                userEntities.map { it.toDomain(presence = null, isMyself = it.id == userId) }
                            }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }
    
    fun updateGroupName(newName: String) {
        if (newName.isBlank()) return
        
        viewModelScope.launch {
            try {
                convRepo.updateGroupName(conversationId, newName)
                Log.d(TAG, "Group name updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating group name", e)
            }
        }
    }
    
    fun addMember(userId: String) {
        viewModelScope.launch {
            try {
                convRepo.addUserToGroup(conversationId, userId)
                Log.d(TAG, "Member added successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding member", e)
            }
        }
    }
    
    fun removeMember(userId: String) {
        viewModelScope.launch {
            try {
                convRepo.removeUserFromGroup(conversationId, userId)
                Log.d(TAG, "Member removed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing member", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "GroupSettingsVM"
    }
}

