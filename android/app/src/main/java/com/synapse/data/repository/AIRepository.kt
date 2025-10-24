package com.synapse.data.repository

import android.util.Log
import com.synapse.data.remote.RefineResponse
import com.synapse.data.remote.SummarizeRequest
import com.synapse.data.remote.SummarizeResponse
import com.synapse.data.remote.SynapseAIApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AI features (Thread Summarization, Action Items, etc.)
 * 
 * Communicates with Python backend API via Retrofit.
 * Backend creates AI messages directly in Firestore, which are then
 * picked up by the message listeners for real-time updates.
 */
@Singleton
class AIRepository @Inject constructor(
    private val api: SynapseAIApi
) {
    
    /**
     * Generate thread summary
     * Backend will create an AI_SUMMARY message in Firestore
     * 
     * @param conversationId Firestore conversation ID
     * @param customInstructions Optional custom instructions for focused summary
     * @return Response with created message ID
     */
    suspend fun summarizeThread(
        conversationId: String,
        customInstructions: String? = null
    ): Result<SummarizeResponse> {
        return try {
            val request = SummarizeRequest(
                conversation_id = conversationId,
                custom_instructions = customInstructions
            )
            
            Log.d(TAG, "📊 Requesting thread summary for conversation: ${conversationId.takeLast(6)}")
            val response = api.summarizeThread(request)
            
            Log.d(TAG, "✅ Summary created: messageId=${response.message_id.takeLast(6)}, " +
                    "messageCount=${response.message_count}, " +
                    "processingTime=${response.processing_time_ms}ms")
            
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to generate summary", e)
            Result.failure(e)
        }
    }
    
    /**
     * Refine existing summary
     * Backend will create a new refined AI_SUMMARY message
     * 
     * @param conversationId Firestore conversation ID
     * @param previousSummaryId Message ID of the previous summary
     * @param refinementInstructions User's refinement instructions
     * @return Response with new refined message ID
     */
    suspend fun refineSummary(
        conversationId: String,
        previousSummaryId: String,
        refinementInstructions: String
    ): Result<RefineResponse> {
        return try {
            Log.d(TAG, "🔧 Refining summary: ${previousSummaryId.takeLast(6)}")
            
            val response = api.refineSummary(
                conversationId = conversationId,
                previousSummaryId = previousSummaryId,
                refinementInstructions = refinementInstructions
            )
            
            Log.d(TAG, "✅ Refined summary created: messageId=${response.message_id.takeLast(6)}")
            
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to refine summary", e)
            Result.failure(e)
        }
    }
    
    companion object {
        private const val TAG = "AIRepository"
    }
}

