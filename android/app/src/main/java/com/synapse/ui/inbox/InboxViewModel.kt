package com.synapse.ui.inbox

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.network.NetworkConnectivityMonitor
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import com.synapse.data.source.firestore.entity.ConversationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
                    val myMember = conv.members[userId] ?: return@forEach  // Skip if I'm not in members
                    val myLastReceivedAt = myMember.lastReceivedAt

                    // Get all other members (exclude myself, bots, and deleted)
                    val otherMembers = conv.members
                        .filterKeys { it != userId }
                        .filterValues { !it.isBot && !it.isDeleted }

                    // Find the most recent lastMessageSentAt from OTHER members
                    val mostRecentOtherSentAt = otherMembers.values
                        .map { it.lastMessageSentAt }
                        .maxOrNull()

                    Log.d("InboxVM", "🔵 Loop check - 2: convId=${conv.id.takeLast(6)}, mostRecentOtherSent=$mostRecentOtherSentAt, myLastReceived=$myLastReceivedAt")

                    if (mostRecentOtherSentAt != null && mostRecentOtherSentAt > myLastReceivedAt) {
                        Log.d("InboxVM", "🔴 UPDATING lastReceivedAt for ${conv.id.takeLast(6)}")
                        viewModelScope.launch {
                            conversationsRepo.updateMemberLastReceivedAt(conv.id, mostRecentOtherSentAt)
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
                    .flatMap { it.members.keys }  // members é Map<String, Member>
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
        
        // STEP 6: Calculate real unread counts from Firebase
        // Counts messages sent by OTHER users (not deleted) after user's lastSeenAt
        val unreadCountsFlow = conversationsFlow
            .map { conversations ->
                val countsByConv = mutableMapOf<String, Int>()
                
                // Fetch all counts in parallel
                coroutineScope {
                    conversations.map { conv ->
                        async {
                            val lastSeenTimestamp = conv.members[userId]?.lastSeenAt
                            val myLastSeenAtMs = lastSeenTimestamp?.toDate()?.time ?: 0L
                            val currentTimeMs = System.currentTimeMillis()
                            
                            Log.d("InboxVM", "🔍 Conv ${conv.id.takeLast(6)}:")
                            Log.d("InboxVM", "   - Firebase Timestamp: $lastSeenTimestamp")
                            Log.d("InboxVM", "   - Converted to Ms: $myLastSeenAtMs")
                            Log.d("InboxVM", "   - Current time Ms: $currentTimeMs")
                            Log.d("InboxVM", "   - Diff: ${myLastSeenAtMs - currentTimeMs}ms (negative = past, positive = FUTURE!)")
                            
                            val unreadCount = conversationsRepo.getUnreadCount(
                                conversationId = conv.id,
                                userId = userId,
                                lastSeenAtMs = myLastSeenAtMs
                            )
                            
                            Log.d("InboxVM", "📊 Conv ${conv.id.takeLast(6)}: unreadCount=$unreadCount")
                            
                            if (unreadCount > 0) {
                                conv.id to unreadCount
                            } else {
                                null
                            }
                        }
                    }.mapNotNull { it.await() }
                        .forEach { (convId, count) ->
                            countsByConv[convId] = count
                        }
                }
                
                Log.d("InboxVM", "📊 Total unread counts: $countsByConv")
                countsByConv as Map<String, Int>
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
