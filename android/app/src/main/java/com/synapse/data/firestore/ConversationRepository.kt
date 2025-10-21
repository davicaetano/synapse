package com.synapse.data.firestore

import com.synapse.data.firebase.FirebaseDataSource
import com.synapse.data.firebase.PresenceDataSource
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ConversationRepository @Inject constructor(
    private val firebaseDataSource: FirebaseDataSource,
    private val presenceDataSource: PresenceDataSource
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

    fun listenUserConversationsWithUsers(userId: String): Flow<List<ConversationSummary>> {
        // Combina conversas do Firestore com presença do Realtime DB
        return firebaseDataSource.listenConversations(userId)
            .flatMapLatest { conversations ->
                if (conversations.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // Coletar todos os IDs únicos de membros
                    val allMemberIds = conversations
                        .flatMap { it.memberIds }
                        .distinct()
                    
                    // Combinar conversas com dados de presença
                    combine(
                        flowOf(conversations),
                        presenceDataSource.listenMultiplePresence(allMemberIds)
                    ) { convs, presenceMap ->
                        convs.map { conv ->
                            // Atualizar cada membro com dados de presença
                            val membersWithPresence = conv.members.map { member ->
                                val presence = presenceMap[member.id]
                                member.copy(
                                    isOnline = presence?.online ?: false,
                                    lastSeenMs = presence?.lastSeenMs
                                )
                            }
                            conv.copy(members = membersWithPresence)
                        }
                    }
                }
            }
    }

    fun listenUserConversationsWithUsersByType(userId: String, convType: ConversationType): Flow<List<ConversationSummary>> {
        // Combina conversas filtradas por tipo com presença do Realtime DB
        return firebaseDataSource.listenConversationsByType(userId, convType)
            .flatMapLatest { conversations ->
                if (conversations.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val allMemberIds = conversations
                        .flatMap { it.memberIds }
                        .distinct()
                    
                    combine(
                        flowOf(conversations),
                        presenceDataSource.listenMultiplePresence(allMemberIds)
                    ) { convs, presenceMap ->
                        convs.map { conv ->
                            val membersWithPresence = conv.members.map { member ->
                                val presence = presenceMap[member.id]
                                member.copy(
                                    isOnline = presence?.online ?: false,
                                    lastSeenMs = presence?.lastSeenMs
                                )
                            }
                            conv.copy(members = membersWithPresence)
                        }
                    }
                }
            }
    }

    suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>) {
        firebaseDataSource.markMessagesAsRead(conversationId, messageIds)
    }

    suspend fun markConversationAsRead(conversationId: String) {
        firebaseDataSource.markConversationAsRead(conversationId)
    }

}


