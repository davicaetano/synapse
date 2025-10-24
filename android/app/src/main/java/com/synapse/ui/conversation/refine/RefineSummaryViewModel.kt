package com.synapse.ui.conversation.refine

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.repository.AIRepository
import com.synapse.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * RefineSummaryViewModel
 * Manages state for refining an existing AI summary
 * 
 * Fetches the previous summary from Firestore by messageId
 * Handles refinement API call and loading state
 */
@HiltViewModel
class RefineSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val aiRepo: AIRepository
) : ViewModel() {
    
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    private val previousSummaryId: String = savedStateHandle.get<String>("previousSummaryId") ?: ""
    
    private val _isRefining = MutableStateFlow(false)
    val isRefining: StateFlow<Boolean> = _isRefining
    
    // Observe the previous summary message to display its text
    val uiState: StateFlow<RefineSummaryUIState> = convRepo.observeMessage(conversationId, previousSummaryId)
        .map { message ->
            RefineSummaryUIState(
                isLoading = false,
                previousSummaryText = message?.text
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RefineSummaryUIState(isLoading = true)
        )
    
    /**
     * Refine the existing summary with new instructions
     * Backend will:
     * - Fetch previous summary
     * - Fetch recent messages since previous summary
     * - Generate new summary with refinement context
     * - Create new AI_SUMMARY message in Firestore
     * 
     * @param refinementInstructions User's instructions for refinement
     */
    fun refineSummary(refinementInstructions: String) {
        if (refinementInstructions.isBlank()) {
            Log.w(TAG, "⚠️ Refinement instructions are blank")
            return
        }
        
        viewModelScope.launch {
            try {
                _isRefining.value = true
                Log.d(TAG, "🔧 Refining summary: ${previousSummaryId.takeLast(6)}")
                
                val result = aiRepo.refineSummary(
                    conversationId = conversationId,
                    previousSummaryId = previousSummaryId,
                    refinementInstructions = refinementInstructions
                )
                
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    Log.d(TAG, "✅ Refined summary created: ${response?.message_id?.takeLast(6)}")
                } else {
                    Log.e(TAG, "❌ Failed to refine summary", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception refining summary", e)
            } finally {
                _isRefining.value = false
            }
        }
    }
    
    companion object {
        private const val TAG = "RefineSummaryVM"
    }
}

