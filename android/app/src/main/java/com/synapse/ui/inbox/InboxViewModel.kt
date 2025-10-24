package com.synapse.ui.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.network.NetworkConnectivityMonitor
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import com.synapse.domain.conversation.ConversationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val conversationsRepo: ConversationRepository,
    private val typingRepo: TypingRepository,
    private val auth: FirebaseAuth,
    private val networkMonitor: NetworkConnectivityMonitor
) : ViewModel() {

    // Guard to prevent duplicate lastReceivedAt updates while Firestore processes
    private val lastReceivedUpdatePending = mutableSetOf<String>()

    // StateFlow created ONCE when ViewModel is created
    val uiState: StateFlow<InboxUIState> = run {
        val userId = auth.currentUser?.uid ?: ""

        // STEP 1: Get raw conversations + update lastReceivedAt (with guard to prevent loop)
        val conversationsFlow = conversationsRepo.observeConversations(userId)
            .distinctUntilChanged()
            .onEach { conversations ->
                // Update lastReceivedAt when new messages arrive
                conversations.forEach { conv ->
                    val myLastReceivedAt = conv.memberStatus[userId]?.lastReceivedAt?.toDate()?.time ?: 0L
                    
                    // Get all other members (exclude myself)
                    val otherMembers = conv.memberIds.filter { it != userId }
                    
                    // Find the most recent lastMessageSentAt from OTHER members
                    val mostRecentOtherSentAt = otherMembers.mapNotNull { memberId ->
                        conv.memberStatus[memberId]?.lastMessageSentAt?.toDate()?.time
                    }.maxOrNull()
                    
                    Log.d("InboxVM", "🔵 Loop check - 2: convId=${conv.id.takeLast(6)}, mostRecentOtherSent=$mostRecentOtherSentAt, myLastReceived=$myLastReceivedAt, pending=${conv.id in lastReceivedUpdatePending}")
                    
                    // Guard: only update if someone ELSE sent a message AFTER my lastReceivedAt AND not already pending
                    if (mostRecentOtherSentAt != null && mostRecentOtherSentAt > myLastReceivedAt && conv.id !in lastReceivedUpdatePending) {
                        lastReceivedUpdatePending.add(conv.id)
                        Log.d("InboxVM", "🔴 UPDATING lastReceivedAt for ${conv.id.takeLast(6)}")
                        val ts = Timestamp(mostRecentOtherSentAt / 1000, ((mostRecentOtherSentAt % 1000) * 1000000).toInt())
                        viewModelScope.launch {
                            conversationsRepo.updateMemberLastReceivedAt(conv.id, ts)
                            delay(1000)  // Wait for Firestore write to complete
                            lastReceivedUpdatePending.remove(conv.id)
                        }
                    }
                }
            }
        
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
        
        // STEP 6: Calculate unread indicator based on memberStatus
        // LOGIC: Has unread if ANY other member sent message after my lastSeenAt
        val unreadCountsFlow = conversationsFlow
            .map { conversations ->
                val countsByConv = mutableMapOf<String, Int>()
                
                conversations.forEach { conv ->
                    val myLastSeenAtMs = conv.memberStatus[userId]?.lastSeenAt?.toDate()?.time
                    
                    // Get all other members (exclude myself)
                    val otherMembers = conv.memberIds.filter { it != userId }
                    
                    // Check if any other member sent a message after I last saw
                    val hasUnread = if (myLastSeenAtMs == null) {
                        // Never opened this conversation
                        // Check if ANY other member has sent at least one message
                        otherMembers.any { memberId ->
                            val lastSentAt = conv.memberStatus[memberId]?.lastMessageSentAt?.toDate()?.time
                            lastSentAt != null  // They sent at least one message
                        }
                    } else {
                        // Check if any other member sent AFTER I last saw
                        otherMembers.any { memberId ->
                            val lastSentAt = conv.memberStatus[memberId]?.lastMessageSentAt?.toDate()?.time
                            lastSentAt != null && lastSentAt > myLastSeenAtMs
                        }
                    }
                    
                    // Show "1" as indicator (generic badge)
                    if (hasUnread) {
                        countsByConv[conv.id] = 1
                    }
                    
                    android.util.Log.d("InboxVM", "📊 Conv ${conv.id.takeLast(6)}: myLastSeenAt=$myLastSeenAtMs, hasUnread=$hasUnread")
                }
                
                android.util.Log.d("InboxVM", "📊 Unread counts: $countsByConv")
                countsByConv
            }
        
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
