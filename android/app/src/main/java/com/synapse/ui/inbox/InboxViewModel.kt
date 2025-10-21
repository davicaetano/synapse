package com.synapse.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.firestore.ConversationRepository
import com.synapse.data.firestore.UserRepository
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val conversationsRepo: ConversationRepository,
    private val usersRepo: UserRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    data class InboxItem(
        val id: String,
        val title: String,
        val lastMessageText: String?,
        val updatedAtMs: Long,
        val displayTime: String
    )

    fun observeInbox(userId: String): StateFlow<List<InboxItem>> {
        // Usar o novo método que já inclui dados completos dos usuários
        val conversationsFlow = conversationsRepo.listenUserConversationsWithUsers(userId)

        return conversationsFlow.map { convs: List<ConversationSummary> ->
            convs.map { c ->
                val peerUser = c.members.firstOrNull { it.id != userId }
                val title = when (c.convType) {
                    ConversationType.SELF -> "AI Assistant"
                    ConversationType.DIRECT -> peerUser?.displayName ?: "Unknown User"
                    ConversationType.GROUP -> {
                        val otherMembers = c.members.filter { it.id != userId }
                        if (otherMembers.size <= 3) {
                            otherMembers.joinToString(", ") { it.displayName ?: it.id }
                        } else {
                            "${otherMembers.take(2).joinToString(", ") { it.displayName ?: it.id }} + ${otherMembers.size - 2} more"
                        }
                    }
                }

                InboxItem(
                    id = c.id,
                    title = title,
                    lastMessageText = c.lastMessageText,
                    updatedAtMs = c.updatedAtMs,
                    displayTime = formatTime(c.updatedAtMs)
                )
            }.sortedByDescending { it.updatedAtMs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun observeInboxForCurrentUser(): StateFlow<List<InboxItem>> {
        val uid = auth.currentUser?.uid
        return if (uid == null) {
            // empty StateFlow when not logged
            kotlinx.coroutines.flow.flowOf(emptyList<InboxItem>())
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
