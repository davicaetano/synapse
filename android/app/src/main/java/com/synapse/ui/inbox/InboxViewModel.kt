package com.synapse.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.firestore.ConversationRepository
import com.synapse.domain.conversation.ConversationSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repo: ConversationRepository
) : ViewModel() {
    val conversations: StateFlow<List<ConversationSummary>> =
        repo.listenConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createOrTouch(conversationId: String) {
        viewModelScope.launch { repo.ensureConversation(conversationId) }
    }
}


