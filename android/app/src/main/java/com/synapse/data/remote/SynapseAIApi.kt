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
     * Extract action items from conversation
     * Creates an AI message with formatted action items
     * 
     * @param request Action items request with conversation_id and optional custom_instructions
     * @return Response with message_id and metadata
     */
    @POST("action-items")
    suspend fun extractActionItems(
        @Body request: ActionItemsRequest
    ): ActionItemsResponse
    
    /**
     * Semantic search in conversation (WhatsApp-style)
     * Returns message IDs for in-conversation navigation
     * 
     * @param request Search request with conversation_id and query
     * @return Response with message_ids array
     */
    @POST("search")
    suspend fun searchMessages(
        @Body request: SearchRequest
    ): SearchResponse
}

/**
 * Request body for thread summarization
 */
data class SummarizeRequest(
    val conversation_id: String,
    val custom_instructions: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val max_messages: Int = 1000,
    val dev_summary: Boolean = false
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
 * Request body for action items extraction
 */
data class ActionItemsRequest(
    val conversation_id: String,
    val custom_instructions: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val dev_summary: Boolean = false
)

/**
 * Response from /api/action-items endpoint
 */
data class ActionItemsResponse(
    val success: Boolean,
    val message_id: String,
    val conversation_id: String,
    val action_items_count: Int,
    val message_count: Int,
    val processing_time_ms: Int
)

/**
 * Request body for semantic search
 */
data class SearchRequest(
    val conversation_id: String,
    val query: String,
    val max_results: Int = 20
)

/**
 * Response from /api/search endpoint
 */
data class SearchResponse(
    val success: Boolean,
    val conversation_id: String,
    val query: String,
    val message_ids: List<String>,
    val total_count: Int,
    val processing_time_ms: Int
)

