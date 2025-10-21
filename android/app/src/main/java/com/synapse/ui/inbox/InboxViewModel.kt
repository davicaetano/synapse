package com.synapse.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.firestore.ConversationRepository
import com.synapse.data.firestore.UserRepository
import com.synapse.domain.conversation.ConversationSummary
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
        val conversationsFlow = conversationsRepo.listenUserConversations(userId)
        val usersMapFlow = usersRepo.listenUsers().map { list -> list.associateBy({ it.id }, { it.displayName ?: it.id }) }

        return combine(conversationsFlow, usersMapFlow) { convs: List<ConversationSummary>, usersMap: Map<String, String> ->
            convs.map { c ->
                val peerId = c.memberIds.firstOrNull { it != userId }
                val title = c.title ?: (peerId?.let { usersMap[it] } ?: c.id)
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
