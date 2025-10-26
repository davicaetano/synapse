package com.synapse.ui.conversation.summarize

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.local.DevPreferences
import com.synapse.data.repository.AIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for AI Summarization Input Screen
 * 
 * Simple ViewModel that just calls AIRepository to generate summaries
 */
@HiltViewModel
class SummarizeInputViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiRepo: AIRepository,
    devPreferences: DevPreferences
) : ViewModel() {
    
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    
    /**
     * Dev setting: Should "Force Error" option be shown?
     */
    val forceAIError: StateFlow<Boolean> = devPreferences.forceAIError
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    
    /**
     * Generate AI analysis based on mode
     * Job runs in ApplicationScope and survives Activity destruction
     * 
     * Supports 5 AI features:
     * - THREAD_SUMMARIZATION: Generate conversation summary
     * - ACTION_ITEMS: Extract tasks and assignments
     * - PRIORITY_DETECTION: Identify urgent messages
     * - DECISION_TRACKING: Track decisions made
     * - CUSTOM: Custom instructions
     * 
     * @param mode AIAgentMode as string
     * @param customInstructions Optional custom instructions
     */
    fun generateAIAnalysis(mode: String, customInstructions: String? = null) {
        when (mode) {
            "THREAD_SUMMARIZATION" -> aiRepo.summarizeThreadAsync(conversationId, customInstructions)
            "ACTION_ITEMS" -> aiRepo.extractActionItemsAsync(conversationId, customInstructions)
            "PRIORITY_DETECTION" -> aiRepo.detectPriorityAsync(conversationId)
            "DECISION_TRACKING" -> aiRepo.trackDecisionsAsync(conversationId)
            "CUSTOM" -> aiRepo.summarizeThreadAsync(conversationId, customInstructions) // Custom uses summary with instructions
            else -> aiRepo.summarizeThreadAsync(conversationId, customInstructions) // Default to summary
        }
    }
}

