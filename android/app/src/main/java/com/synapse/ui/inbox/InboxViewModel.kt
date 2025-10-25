package com.synapse.ui.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.network.NetworkConnectivityMonitor
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import com.synapse.data.source.firestore.entity.ConversationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    // StateFlow created ONCE when ViewModel is created
    val uiState: StateFlow<InboxUIState> = run {
        val userId = auth.currentUser?.uid ?: ""

        val updatedMemberState: Flow<List<ConversationEntity>> = conversationsRepo.observeConversations(userId, false)
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

                    Log.d("InboxVM", "🔵 Loop check - 2: convId=${conv.id.takeLast(6)}, mostRecentOtherSent=$mostRecentOtherSentAt, myLastReceived=$myLastReceivedAt")

                    if (mostRecentOtherSentAt != null && mostRecentOtherSentAt > myLastReceivedAt) {
                        Log.d("InboxVM", "🔴 UPDATING lastReceivedAt for ${conv.id.takeLast(6)}")
                        val ts = Timestamp(mostRecentOtherSentAt / 1000, ((mostRecentOtherSentAt % 1000) * 1000000).toInt())
                        viewModelScope.launch {
                            conversationsRepo.updateMemberLastReceivedAt(conv.id, ts)
                        }
                    }
                }
            }.onStart { emit(emptyList()) }

        // STEP 1: Get raw conversations + update lastReceivedAt (with guard to prevent loop)
        val conversationsFlow = conversationsRepo.observeConversations(userId, true)
        
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
                    
                    Log.d("InboxVM", "📊 Conv ${conv.id.takeLast(6)}: myLastSeenAt=$myLastSeenAtMs, hasUnread=$hasUnread")
                }

                Log.d("InboxVM", "📊 Unread counts: $countsByConv")
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
            typingAndCountsFlow,
            updatedMemberState
        ) { conversations, users, presence, (typing, unreadCounts, isConnected), updatedMemberState ->
            buildInboxUIState(userId, conversations, users, presence, typing, unreadCounts, isConnected)
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily, // Keep listeners active - no reload when navigating back
            InboxUIState(isLoading = true)
        )
    }
}
