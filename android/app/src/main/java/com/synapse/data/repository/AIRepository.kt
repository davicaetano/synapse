package com.synapse.data.repository

import android.util.Log
import com.synapse.data.local.DevPreferences
import com.synapse.data.remote.SummarizeRequest
import com.synapse.data.remote.SynapseAIApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AI features (Thread Summarization, Action Items, etc.)
 * 
 * **Architecture:**
 * - Jobs run in ApplicationScope (survive Activity/ViewModel destruction)
 * - Multiple jobs can run per conversation simultaneously
 * - Real-time job count observable per conversation
 * - Backend creates AI messages directly in Firestore (success or error)
 * - Android receives results via Firestore listener (reliable, always works)
 */
@Singleton
class AIRepository @Inject constructor(
    private val api: SynapseAIApi,
    private val devPreferences: DevPreferences
) {
    // Application-level scope (survives Activity/ViewModel destruction)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Flow: Map of conversationId -> job count (for real-time UI updates)
    private val _jobCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    
    // Flow: Global error messages (for Toast notifications in MainActivity)
    private val _errorMessages = MutableStateFlow<String?>(null)
    val errorMessages: Flow<String?> = _errorMessages
    
    /**
     * Clear error message after Toast is shown
     */
    fun clearError() {
        _errorMessages.value = null
    }
    
    /**
     * Observe active job count for a specific conversation
     * UI can use this to show loading spinners
     * 
     * @param conversationId Conversation to monitor
     * @return Flow<Int> emitting current job count (0, 1, 2, ...)
     */
    fun observeActiveJobCount(conversationId: String): Flow<Int> {
        return _jobCounts
            .map { it[conversationId] ?: 0 }
            .distinctUntilChanged()
    }
    
    /**
     * Generate thread summary (Fire-and-forget)
     * 
     * Job runs in ApplicationScope and survives Activity/ViewModel destruction.
     * Multiple summaries can be requested simultaneously for the same conversation.
     * 
     * **Flow:**
     * 1. Android calls Backend API (this method)
     * 2. Backend processes with OpenAI
     * 3. Backend writes to Firestore (success message OR error message)
     * 4. Android receives via Firestore listener (reliable, always works)
     * 
     * @param conversationId Firestore conversation ID
     * @param customInstructions Optional custom instructions for focused summary
     */
    fun summarizeThreadAsync(
        conversationId: String,
        customInstructions: String? = null
    ) {
        applicationScope.launch {
            val jobId = "summary_${conversationId.takeLast(6)}_${System.currentTimeMillis()}"
            
            try {
                incrementJobCount(conversationId)
                Log.d(TAG, "🚀 [$jobId] Starting AI job (active: ${getJobCount(conversationId)})")
                
                // Read dev preference for dev summary (includes processing time + API version)
                val devSummary = devPreferences.showAIProcessingTime.first()
                
                // Call backend API (auth token is added automatically by interceptor)
                val request = SummarizeRequest(
                    conversation_id = conversationId,
                    custom_instructions = customInstructions,
                    dev_summary = devSummary
                )
                
                val response = api.summarizeThread(request)
                
                Log.d(TAG, "✅ [$jobId] AI job completed: messageId=${response.message_id.takeLast(6)}, " +
                        "messageCount=${response.message_count}, time=${response.processing_time_ms}ms")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ [$jobId] AI job failed: ${e.message}", e)
                
                // Emit error message for MainActivity to show Toast
                val errorMsg = when {
                    e.message?.contains("502") == true -> "AI service is starting up. Please try again in a moment."
                    e.message?.contains("timeout") == true -> "Request timeout. Please check your connection."
                    e.message?.contains("401") == true -> "Authentication error. Please sign in again."
                    else -> "AI processing failed: ${e.message}"
                }
                _errorMessages.value = errorMsg
                
                // Backend will handle error posting to Firestore
                
            } finally {
                decrementJobCount(conversationId)
                Log.d(TAG, "🏁 [$jobId] Job finished (remaining: ${getJobCount(conversationId)})")
            }
        }
    }
    
    // ========== Job Management ==========
    
    private fun incrementJobCount(conversationId: String) {
        val current = _jobCounts.value.toMutableMap()
        current[conversationId] = (current[conversationId] ?: 0) + 1
        _jobCounts.value = current
    }
    
    private fun decrementJobCount(conversationId: String) {
        val current = _jobCounts.value.toMutableMap()
        val newCount = (current[conversationId] ?: 1) - 1
        if (newCount <= 0) {
            current.remove(conversationId)
        } else {
            current[conversationId] = newCount
        }
        _jobCounts.value = current
    }
    
    private fun getJobCount(conversationId: String): Int {
        return _jobCounts.value[conversationId] ?: 0
    }
    
    companion object {
        private const val TAG = "AIRepository"
    }
}

