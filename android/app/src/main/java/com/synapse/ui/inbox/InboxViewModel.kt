package com.synapse.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.network.NetworkConnectivityMonitor
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import com.synapse.domain.conversation.ConversationType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val conversationsRepo: ConversationRepository,
    private val typingRepo: TypingRepository,
    private val auth: FirebaseAuth,
    private val networkMonitor: NetworkConnectivityMonitor
) : ViewModel() {
    
    // Background: Mark messages as received (independent side effect)
    init {
        val userId = auth.currentUser?.uid ?: ""
        conversationsRepo.observeAllUnreceivedMessages(userId).distinctUntilChanged()
            .onEach { unreceivedByConv ->
                unreceivedByConv.forEach { (convId, messageIds) ->
                    viewModelScope.launch {
                        conversationsRepo.markMessagesAsReceived(convId, messageIds)
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    // StateFlow created ONCE when ViewModel is created
    val uiState: StateFlow<InboxUIState> = run {
        val userId = auth.currentUser?.uid ?: ""

        // STEP 1: Get raw conversations
        val conversationsFlow = conversationsRepo.observeConversations(userId)
        
        // STEP 2: Extract member IDs (stable - only changes when conversations change)
        val memberIdsFlow = conversationsFlow
            .map { conversations ->
                conversations
                    .flatMap { it.memberIds }
                    .distinct()
                    .sorted()
            }
            .distinctUntilChanged() // Only update when IDs actually change
        
        // STEP 3: Observe users (recreates ONLY when memberIds change)
        val usersFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                conversationsRepo.observeUsers(memberIds)
            }
        
        // STEP 4: Observe presence (recreates ONLY when memberIds change)
        val presenceFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                conversationsRepo.observePresence(memberIds)
            }
        
        // STEP 5: Observe typing (only for conversation IDs we have)
        val typingFlow = conversationsFlow
            .map { convs -> convs.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { conversationIds ->
                if (conversationIds.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    typingRepo.observeTypingInMultipleConversations(conversationIds)
                }
            }
        
        // STEP 6: Observe unread counts for all conversations (1 listener!)
        val unreadCountsFlow = conversationsRepo.observeAllUnreadCounts(userId)
            .onStart { emit(emptyMap()) }  // ✅ Emit immediately - don't wait for Firestore!
        
        // STEP 7: Observe network connectivity
        val isConnectedFlow = networkMonitor.isConnected
        
        // STEP 8: Combine typing + unreadCounts + isConnected (to avoid 6-flow limit)
        val typingAndCountsFlow = combine(
            typingFlow,
            unreadCountsFlow,
            isConnectedFlow
        ) { typing, unreadCounts, isConnected ->
            Triple(typing, unreadCounts, isConnected)
        }
        
        // STEP 9: Combine everything and build UI state
        combine(
            conversationsFlow,
            usersFlow,
            presenceFlow,
            typingAndCountsFlow
        ) { conversations, users, presence, (typing, unreadCounts, isConnected) ->
            buildInboxUIState(userId, conversations, users, presence, typing, unreadCounts, isConnected)
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily, // Keep listeners active - no reload when navigating back
            InboxUIState(isLoading = true)
        )
    }
    
    /**
     * Build InboxUIState from raw data.
     * Separated for clarity and testability.
     */
    private fun buildInboxUIState(
        userId: String,
        conversations: List<com.synapse.data.source.firestore.entity.ConversationEntity>,
        users: List<com.synapse.data.source.firestore.entity.UserEntity>,
        presence: Map<String, com.synapse.data.source.realtime.entity.PresenceEntity>,
        typing: Map<String, List<com.synapse.domain.user.User>>,
        unreadCounts: Map<String, Int>,
        isConnected: Boolean
    ): InboxUIState {
        if (conversations.isEmpty()) {
            return InboxUIState(items = emptyList(), isLoading = false, isConnected = isConnected)
        }
        
        // Build user map
        val usersMap = users.associateBy { it.id }
        
        // Transform to UI items (unreadCounts comes from reactive Flow)
        val items = conversations.mapNotNull { conv ->
            buildInboxItem(userId, conv, usersMap, presence, typing, unreadCounts)
        }.sortedByDescending { it.updatedAtMs }
        
        return InboxUIState(items = items, isLoading = false, isConnected = isConnected)
    }
    
    /**
     * Build a single InboxItem from raw data.
     * Separated for clarity.
     */
    private fun buildInboxItem(
        userId: String,
        conv: com.synapse.data.source.firestore.entity.ConversationEntity,
        usersMap: Map<String, com.synapse.data.source.firestore.entity.UserEntity>,
        presence: Map<String, com.synapse.data.source.realtime.entity.PresenceEntity>,
        typing: Map<String, List<com.synapse.domain.user.User>>,
        unreadCounts: Map<String, Int>
    ): InboxItem? {
        val unreadCount = unreadCounts[conv.id] ?: 0
        val typingUsers = typing[conv.id] ?: emptyList()
        val typingText = when {
            typingUsers.isEmpty() -> null
            typingUsers.size == 1 -> "typing..."
            else -> "${typingUsers.size} people are typing..."
        }
        
        // Build members with presence
        val members = conv.memberIds.mapNotNull { memberId ->
            val userEntity = usersMap[memberId]
            val presenceEntity = presence[memberId]
            userEntity?.toDomain(
                presence = presenceEntity,
                isMyself = (memberId == userId)
            )
        }
        
        val title = when (conv.convType) {
            ConversationType.SELF.name -> "AI Assistant"
            ConversationType.DIRECT.name -> {
                members.firstOrNull { it.id != userId }?.displayName ?: "Unknown User"
            }
            ConversationType.GROUP.name -> {
                conv.groupName?.ifBlank { null } ?: "Group"
            }
            else -> "Unknown"
        }
        
        return when (conv.convType) {
            ConversationType.SELF.name -> InboxItem.SelfConversation(
                id = conv.id,
                title = title,
                lastMessageText = conv.lastMessageText,
                updatedAtMs = conv.updatedAtMs,
                displayTime = formatTime(conv.updatedAtMs),
                convType = ConversationType.SELF,
                unreadCount = unreadCount,
                typingText = typingText
            )
            ConversationType.DIRECT.name -> {
                val otherUser = members.firstOrNull { it.id != userId }
                if (otherUser != null) {
                    InboxItem.OneOnOneConversation(
                        id = conv.id,
                        title = title,
                        lastMessageText = conv.lastMessageText,
                        updatedAtMs = conv.updatedAtMs,
                        displayTime = formatTime(conv.updatedAtMs),
                        convType = ConversationType.DIRECT,
                        otherUser = otherUser,
                        unreadCount = unreadCount,
                        typingText = typingText
                    )
                } else {
                    null // Skip if other user not found
                }
            }
            ConversationType.GROUP.name -> InboxItem.GroupConversation(
                id = conv.id,
                title = title,
                lastMessageText = conv.lastMessageText,
                updatedAtMs = conv.updatedAtMs,
                displayTime = formatTime(conv.updatedAtMs),
                convType = ConversationType.GROUP,
                members = members,
                groupName = conv.groupName,
                unreadCount = unreadCount,
                typingText = typingText
            )
            else -> null
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return ""
    val date = java.util.Date(ms)
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return fmt.format(date)
}
