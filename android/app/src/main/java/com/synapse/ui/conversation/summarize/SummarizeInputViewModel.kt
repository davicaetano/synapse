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
     * Generate AI summary (fire-and-forget)
     * Job runs in ApplicationScope and survives Activity destruction
     */
    fun generateSummary(customInstructions: String? = null) {
        aiRepo.summarizeThreadAsync(conversationId, customInstructions)
    }
}

