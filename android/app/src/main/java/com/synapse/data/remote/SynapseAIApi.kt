package com.synapse.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for Synapse AI Backend API
 * 
 * Base URL: http://localhost:8000/api (development)
 * Production: https://synapse-ai.render.com/api
 */
interface SynapseAIApi {
    
    /**
     * Generate thread summary
     * Creates an AI summary message in Firestore
     * 
     * @param request Summarization request with conversation_id and optional custom_instructions
     * @return Response with message_id and metadata
     */
    @POST("summarize")
    suspend fun summarizeThread(
        @Body request: SummarizeRequest
    ): SummarizeResponse
    
    /**
     * Refine existing summary
     * Creates a new refined AI summary message
     * 
     * @param conversationId Firestore conversation ID
     * @param previousSummaryId Message ID of the previous summary
     * @param refinementInstructions User's refinement instructions
     * @return Response with new message_id and metadata
     */
    @POST("summarize/refine")
    suspend fun refineSummary(
        @Query("conversation_id") conversationId: String,
        @Query("previous_summary_id") previousSummaryId: String,
        @Query("refinement_instructions") refinementInstructions: String
    ): RefineResponse
}

/**
 * Request body for thread summarization
 */
data class SummarizeRequest(
    val conversation_id: String,
    val custom_instructions: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val max_messages: Int = 1000
)

/**
 * Response from /api/summarize endpoint
 */
data class SummarizeResponse(
    val success: Boolean,
    val message_id: String,
    val conversation_id: String,
    val message_count: Int,
    val processing_time_ms: Int
)

/**
 * Response from /api/summarize/refine endpoint
 */
data class RefineResponse(
    val success: Boolean,
    val message_id: String,
    val conversation_id: String,
    val previous_summary_id: String,
    val message_count: Int,
    val processing_time_ms: Int
)

