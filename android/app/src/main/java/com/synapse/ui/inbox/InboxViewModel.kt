package com.synapse.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.repository.ConversationRepository
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val conversationsRepo: ConversationRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    fun observeInbox(userId: String): StateFlow<InboxUIState> {
        // Use the new method that already includes complete user data + presence
        val conversationsFlow = conversationsRepo.observeConversationsWithUsers(userId)

        return conversationsFlow.map { convs: List<ConversationSummary> ->
            val items = convs.map { c ->
                val title = when (c.convType) {
                    ConversationType.SELF -> "AI Assistant"
                    ConversationType.DIRECT -> {
                        val peerUser = c.members.firstOrNull { it.id != userId }
                        peerUser?.displayName ?: "Unknown User"
                    }
                    ConversationType.GROUP -> {
                        val otherMembers = c.members.filter { it.id != userId }
                        if (otherMembers.size <= 3) {
                            otherMembers.joinToString(", ") { it.displayName ?: it.id }
                        } else {
                            "${otherMembers.take(2).joinToString(", ") { it.displayName ?: it.id }} + ${otherMembers.size - 2} more"
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
                        convType = c.convType
                    )
                    ConversationType.DIRECT -> InboxItem.OneOnOneConversation(
                        id = c.id,
                        title = title,
                        lastMessageText = c.lastMessageText,
                        updatedAtMs = c.updatedAtMs,
                        displayTime = formatTime(c.updatedAtMs),
                        convType = c.convType,
                        otherUser = c.members.firstOrNull { it.id != userId }!!
                    )
                    ConversationType.GROUP -> InboxItem.GroupConversation(
                        id = c.id,
                        title = title,
                        lastMessageText = c.lastMessageText,
                        updatedAtMs = c.updatedAtMs,
                        displayTime = formatTime(c.updatedAtMs),
                        convType = c.convType,
                        members = c.members
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
