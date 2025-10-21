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
import kotlinx.coroutines.launch

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val conversationsRepo: ConversationRepository,
    private val usersRepo: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    data class InboxItem(
        val id: String,
        val title: String,
        val lastMessageText: String?,
        val updatedAtMs: Long
    )

    private val conversationsFlow = conversationsRepo.listenConversations()
    private val usersMapFlow = usersRepo.listenUsers().map { list -> list.associateBy({ it.id }, { it.displayName ?: it.id }) }

    val items: StateFlow<List<InboxItem>> = combine(conversationsFlow, usersMapFlow) { conversations, usersMap ->
        val myId = auth.currentUser?.uid
        conversations.map { c ->
            val peerId = c.memberIds.firstOrNull { it != myId }
            val resolvedTitle = c.title ?: (peerId?.let { usersMap[it] } ?: c.id)
            InboxItem(
                id = c.id,
                title = resolvedTitle,
                lastMessageText = c.lastMessageText,
                updatedAtMs = c.updatedAtMs
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createOrTouch(conversationId: String) {
        viewModelScope.launch { conversationsRepo.ensureConversation(conversationId) }
    }
}


