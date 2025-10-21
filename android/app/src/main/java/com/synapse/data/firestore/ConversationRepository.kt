package com.synapse.data.firestore

import com.synapse.data.firebase.FirebaseDataSource
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val firebaseDataSource: FirebaseDataSource
) {

    suspend fun sendMessage(conversationId: String, text: String) {
        firebaseDataSource.sendMessage(conversationId, text)
    }

    suspend fun getOrCreateDirectConversation(otherUserId: String): String? {
        return firebaseDataSource.getOrCreateDirectConversation(otherUserId)
    }

    fun listenConversation(conversationId: String): Flow<Conversation> =
        firebaseDataSource.listenConversation(conversationId)

    fun listenUserConversations(userId: String): Flow<List<ConversationSummary>> =
        firebaseDataSource.listenConversations(userId)



}


