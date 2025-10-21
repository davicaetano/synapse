package com.synapse.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.firestore.ConversationRepository
import com.synapse.domain.conversation.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""

    val conversation: StateFlow<Conversation> = convRepo.listenConversation(conversationId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            Conversation(
                summary = com.synapse.domain.conversation.ConversationSummary(conversationId, null, null, 0L, emptyList()),
                messages = emptyList()
            )
        )

    fun send(text: String) {
        viewModelScope.launch { convRepo.sendMessage(conversationId, text) }
    }

    fun isMine(senderId: String): Boolean = auth.currentUser?.uid == senderId
}


