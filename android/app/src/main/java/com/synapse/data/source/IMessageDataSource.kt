package com.synapse.data.source

import com.synapse.data.source.firestore.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface for message data sources.
 * Allows switching between Firestore-only and Room+Firestore implementations.
 */
interface IMessageDataSource {
    
    fun listenMessages(conversationId: String): Flow<List<MessageEntity>>
    
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        memberIds: List<String>
    ): String?
    
    suspend fun sendMessagesBatch(
        conversationId: String,
        messages: List<String>,
        memberIds: List<String>
    )
    
    fun observeUnreadMessages(conversationId: String): Flow<List<String>>
    
    suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>)
    
    fun observeAllUnreadCounts(userId: String): Flow<Map<String, Int>>
    
    fun observeAllUnreceivedMessages(userId: String): Flow<Map<String, List<String>>>
    
    suspend fun markMessagesAsReceived(conversationId: String, messageIds: List<String>)
}

