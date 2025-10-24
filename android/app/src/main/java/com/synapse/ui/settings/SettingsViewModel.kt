package com.synapse.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUIState())
    val uiState: StateFlow<SettingsUIState> = _uiState.asStateFlow()
    
    init {
        loadUserData()
    }
    
    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "User not authenticated")
            return
        }
        
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            userRepo.observeUser(userId).collect { userEntity ->
                if (userEntity != null) {
                    _uiState.update {
                        SettingsUIState(
                            displayName = userEntity.displayName ?: "",
                            email = userEntity.email ?: "",
                            photoUrl = userEntity.photoUrl,
                            userId = userEntity.id,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
    
    fun updateDisplayName(newName: String) {
        val userId = auth.currentUser?.uid ?: return
        
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                userRepo.updateDisplayName(userId, newName)
                _uiState.update { 
                    it.copy(
                        displayName = newName,
                        isSaving = false
                    )
                }
                Log.d(TAG, "Display name updated successfully")
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        errorMessage = "Failed to update name: ${e.message}"
                    )
                }
                Log.e(TAG, "Error updating display name", e)
            }
        }
    }
    
    fun updateEmail(newEmail: String) {
        val userId = auth.currentUser?.uid ?: return
        
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                userRepo.updateEmail(userId, newEmail)
                _uiState.update { 
                    it.copy(
                        email = newEmail,
                        isSaving = false
                    )
                }
                Log.d(TAG, "Email updated successfully")
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isSaving = false,
                        errorMessage = "Failed to update email: ${e.message}"
                    )
                }
                Log.e(TAG, "Error updating email", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "SettingsVM"
    }
}

