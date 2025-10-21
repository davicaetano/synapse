package com.synapse.data.firestore

import com.synapse.data.firebase.FirebaseDataSource
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
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

    suspend fun createSelfConversation(): String? =
        firebaseDataSource.createSelfConversation()

    suspend fun createGroupConversation(memberIds: List<String>): String? =
        firebaseDataSource.createGroupConversation(memberIds)

    suspend fun addUserToGroupConversation(conversationId: String, userId: String) =
        firebaseDataSource.addUserToGroupConversation(conversationId, userId)

    suspend fun removeUserFromGroupConversation(conversationId: String, userId: String) =
        firebaseDataSource.removeUserFromGroupConversation(conversationId, userId)

    fun listenUserConversationsByType(userId: String, convType: ConversationType): Flow<List<ConversationSummary>> =
        firebaseDataSource.listenConversationsByType(userId, convType)

}


