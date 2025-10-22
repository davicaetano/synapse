package com.synapse.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.source.firestore.FirestoreUserDataSource
import com.synapse.data.source.realtime.RealtimePresenceDataSource
import com.synapse.domain.user.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing typing indicators across conversations.
 * 
 * RESPONSIBILITIES:
 * - Expose typing operations (set/remove typing)
 * - Combine typing data with user information
 * - Filter out current user from typing lists
 * - Provide clean domain models for UI
 */
@Singleton
class TypingRepository @Inject constructor(
    private val presenceDataSource: RealtimePresenceDataSource,
    private val userDataSource: FirestoreUserDataSource,
    private val auth: FirebaseAuth
) {
    
    /**
     * Mark current user as typing in a conversation.
     * Should be called when user starts typing.
     */
    suspend fun setTyping(conversationId: String) {
        presenceDataSource.setTyping(conversationId)
    }
    
    /**
     * Remove typing indicator for current user.
     * Should be called when:
     * - User stops typing (after 3 seconds of inactivity)
     * - User sends message
     * - User leaves conversation screen
     */
    suspend fun removeTyping(conversationId: String) {
        presenceDataSource.removeTyping(conversationId)
    }
    
    /**
     * Observe who is typing in a conversation.
     * Returns a list of User objects for users currently typing (excluding self).
     * 
     * Use this in ConversationScreen to show "John is typing..." in TopAppBar.
     */
    fun observeTypingUsersInConversation(conversationId: String): Flow<List<User>> {
        val currentUserId = auth.currentUser?.uid
        
        return presenceDataSource.listenTypingInConversation(conversationId)
            .map { typingMap ->
                // Filter out current user and extract user IDs
                typingMap.keys.filter { it != currentUserId }
            }
            .combine(userDataSource.listenAllUsers()) { typingUserIds, allUserEntities ->
                // Map user IDs to User objects (convert entities to domain)
                typingUserIds.mapNotNull { userId ->
                    allUserEntities.find { it.id == userId }?.toDomain()
                }
            }
            .onStart { 
                emit(emptyList()) // Emit immediately to prevent blocking combine()
            }
    }
    
    /**
     * Observe typing status across multiple conversations.
     * Returns a Map: conversationId -> List of Users typing (excluding self).
     * 
     * Use this in InboxScreen to show "typing..." instead of last message.
     */
    fun observeTypingInMultipleConversations(conversationIds: List<String>): Flow<Map<String, List<User>>> {
        val currentUserId = auth.currentUser?.uid
        
        return presenceDataSource.listenTypingInMultipleConversations(conversationIds)
            .combine(userDataSource.listenAllUsers()) { typingMapByConversation, allUserEntities ->
                typingMapByConversation.mapValues { (_, typingMap) ->
                    // For each conversation, filter out current user and map to User objects
                    val typingUserIds = typingMap.keys.filter { it != currentUserId }
                    typingUserIds.mapNotNull { userId ->
                        allUserEntities.find { it.id == userId }?.toDomain()
                    }
                }
            }
            .onStart { 
                emit(emptyMap()) // Emit immediately to prevent blocking combine()
            }
    }
    
    /**
     * Get a simplified string for typing status in a conversation.
     * Returns:
     * - null if nobody is typing
     * - "John is typing..." if one person
     * - "John and Mary are typing..." if two people
     * - "John, Mary and 2 others are typing..." if more than 2
     * 
     * Use this in Inbox for simple display.
     */
    fun observeTypingTextInConversation(conversationId: String): Flow<String?> {
        return observeTypingUsersInConversation(conversationId)
            .map { typingUsers ->
                when {
                    typingUsers.isEmpty() -> null
                    typingUsers.size == 1 -> "${typingUsers[0].displayName} is typing..."
                    typingUsers.size == 2 -> "${typingUsers[0].displayName} and ${typingUsers[1].displayName} are typing..."
                    else -> {
                        val first = typingUsers[0].displayName
                        val second = typingUsers[1].displayName
                        val othersCount = typingUsers.size - 2
                        "$first, $second and $othersCount ${if (othersCount == 1) "other" else "others"} are typing..."
                    }
                }.also { text ->
                }
            }
            .onStart { 
                emit(null) // Emit immediately to prevent blocking combine()
            }
    }
}

