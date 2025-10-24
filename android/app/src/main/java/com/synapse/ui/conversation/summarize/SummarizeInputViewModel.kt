package com.synapse.ui.conversation.summarize

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.synapse.data.repository.AIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for AI Summarization Input Screen
 * 
 * Simple ViewModel that just calls AIRepository to generate summaries
 */
@HiltViewModel
class SummarizeInputViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiRepo: AIRepository
) : ViewModel() {
    
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    
    /**
     * Generate AI summary (fire-and-forget)
     * Job runs in ApplicationScope and survives Activity destruction
     */
    fun generateSummary(customInstructions: String? = null) {
        aiRepo.summarizeThreadAsync(conversationId, customInstructions)
    }
}

