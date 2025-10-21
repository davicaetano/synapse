package com.synapse.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.firestore.ConversationRepository
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import com.synapse.domain.user.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""

    init {
        // Mark conversation as read when opened
        viewModelScope.launch {
            try {
                convRepo.markConversationAsRead(conversationId)
            } catch (e: Exception) {
                // Log error but don't crash the app
                android.util.Log.e("ConversationViewModel", "Failed to mark conversation as read", e)
            }
        }
    }

    val conversation: StateFlow<Conversation> = convRepo.listenConversation(conversationId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            Conversation(
                summary = ConversationSummary(
                    id = conversationId,
                    lastMessageText = null,
                    updatedAtMs = 0L,
                    members = emptyList(),
                    convType = ConversationType.DIRECT
                ),
                messages = emptyList()
            )
        )

    val uiState: StateFlow<ConversationUIState> = convRepo.listenConversation(conversationId)
        .map { conv ->
            ConversationUIState(
                conversationId = conv.summary.id,
                title = "title",
                messages = conv.messages.map { it.toUiMessage() }
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ConversationUIState(conversationId, title = "", messages = emptyList())
        )

    private fun Message.toUiMessage(): ConversationUIMessage {
        return ConversationUIMessage(
            id = id,
            text = text,
            isMine = isMine,
            displayTime = formatTime(createdAtMs)
        )
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return ""
        val date = java.util.Date(ms)
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return fmt.format(date)
    }

    fun send(text: String) {
        viewModelScope.launch { convRepo.sendMessage(conversationId, text) }
    }
}


