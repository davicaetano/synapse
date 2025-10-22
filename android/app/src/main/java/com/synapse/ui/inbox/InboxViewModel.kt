package com.synapse.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.repository.ConversationRepository
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val conversationsRepo: ConversationRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    fun observeInbox(userId: String): StateFlow<InboxUIState> {
        // Use the new method that already includes complete user data + presence
        val conversationsFlow = conversationsRepo.observeConversationsWithUsers(userId)

        return conversationsFlow
            .onEach { convs ->
                // Background task: mark last message as received for all conversations
                convs.forEach { c ->
                    viewModelScope.launch {
                        conversationsRepo.markLastMessageAsReceived(c.id)
                    }
                }
            }
            .mapLatest { convs: List<ConversationSummary> ->
                // Calculate unread counts in parallel for better performance
                val unreadCounts = coroutineScope {
                    convs.map { c ->
                        async {
                            c.id to conversationsRepo.getUnreadMessageCount(c.id)
                        }
                    }.awaitAll().toMap()
                }
                
                val items = convs.mapNotNull { c ->  // Use mapNotNull to filter out invalid conversations
                    val unreadCount = unreadCounts[c.id] ?: 0
                val title = when (c.convType) {
                    ConversationType.SELF -> "AI Assistant"
                    ConversationType.DIRECT -> {
                        val peerUser = c.members.firstOrNull { it.id != userId }
                        peerUser?.displayName ?: "Unknown User"
                    }
                    ConversationType.GROUP -> {
                        // Use group name if set, otherwise generate from members
                        c.groupName?.ifBlank { null } ?: run {
                            val otherMembers = c.members.filter { it.id != userId }
                            when {
                                otherMembers.isEmpty() -> "Group (just you)"
                                else -> "Group"
                            }
                        }
                    }
                }

                when (c.convType) {
                    ConversationType.SELF -> InboxItem.SelfConversation(
                        id = c.id,
                        title = title,
                        lastMessageText = c.lastMessageText,
                        updatedAtMs = c.updatedAtMs,
                        displayTime = formatTime(c.updatedAtMs),
                        convType = c.convType,
                        unreadCount = unreadCount
                    )
                    ConversationType.DIRECT -> {
                        // SAFE: Only create item if other user exists
                        val otherUser = c.members.firstOrNull { it.id != userId }
                        if (otherUser != null) {
                            InboxItem.OneOnOneConversation(
                                id = c.id,
                                title = title,
                                lastMessageText = c.lastMessageText,
                                updatedAtMs = c.updatedAtMs,
                                displayTime = formatTime(c.updatedAtMs),
                                convType = c.convType,
                                otherUser = otherUser,
                                unreadCount = unreadCount
                            )
                        } else {
                            null  // Skip this conversation if other user not found
                        }
                    }
                    ConversationType.GROUP -> InboxItem.GroupConversation(
                        id = c.id,
                        title = title,
                        lastMessageText = c.lastMessageText,
                        updatedAtMs = c.updatedAtMs,
                        displayTime = formatTime(c.updatedAtMs),
                        convType = c.convType,
                        members = c.members,
                        groupName = c.groupName,
                        unreadCount = unreadCount
                    )
                }
            }.sortedByDescending { it.updatedAtMs }

            InboxUIState(
                items = items,
                isLoading = false,
                error = null
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InboxUIState(isLoading = true))
    }

    fun observeInboxForCurrentUser(): StateFlow<InboxUIState> {
        val uid = auth.currentUser?.uid
        return if (uid == null) {
            // empty StateFlow when not logged
            flowOf(InboxUIState())
                .stateIn(viewModelScope, SharingStarted.Eagerly, InboxUIState())
        } else {
            observeInbox(uid)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return ""
    val date = java.util.Date(ms)
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return fmt.format(date)
}
