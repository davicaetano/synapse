package com.synapse.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import com.synapse.domain.user.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val typingRepo: TypingRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    
    // Job to handle typing timeout (auto-remove typing after 3 seconds of inactivity)
    private var typingTimeoutJob: Job? = null

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
    
    override fun onCleared() {
        super.onCleared()
        // Remove typing indicator when leaving conversation
        viewModelScope.launch {
            typingRepo.removeTyping(conversationId)
        }
    }

    val conversation: StateFlow<Conversation> = convRepo.observeConversationWithMessages(conversationId)
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

    val uiState: StateFlow<ConversationUIState> = conversation
        .combine(typingRepo.observeTypingTextInConversation(conversationId)) { conv, typingText ->
            val currentUserId = auth.currentUser?.uid
            val summary = conv.summary
            
            // Build title and subtitle based on conversation type
            val (title, subtitle) = when (summary.convType) {
                ConversationType.SELF -> {
                    "AI Assistant" to null
                }
                ConversationType.DIRECT -> {
                    val otherUser = summary.members.firstOrNull { it.id != currentUserId }
                    val name = otherUser?.displayName ?: "Unknown"
                    val status = when {
                        otherUser?.isOnline == true -> "online"
                        otherUser?.lastSeenMs != null -> formatLastSeen(otherUser.lastSeenMs)
                        else -> null
                    }
                    name to status
                }
                ConversationType.GROUP -> {
                    val name = summary.groupName ?: "Group"
                    val memberCount = "${summary.members.size} member${if (summary.members.size == 1) "" else "s"}"
                    name to memberCount
                }
            }
            
            // Build user map for sender names (for groups)
            val usersMap = summary.members.associateBy { it.id }
            
            ConversationUIState(
                conversationId = summary.id,
                title = title,
                subtitle = subtitle,
                messages = conv.messages.map { it.toUiMessage(summary.convType, usersMap) },
                convType = summary.convType,
                members = summary.members,
                isUserAdmin = currentUserId?.let { summary.isUserAdmin(it) } ?: false,
                otherUserOnline = if (summary.convType == ConversationType.DIRECT) {
                    summary.members.firstOrNull { it.id != currentUserId }?.isOnline
                } else null,
                otherUserPhotoUrl = if (summary.convType == ConversationType.DIRECT) {
                    summary.members.firstOrNull { it.id != currentUserId }?.photoUrl
                } else null,
                typingText = typingText
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ConversationUIState(
                conversationId = conversationId,
                title = "",
                messages = emptyList(),
                convType = ConversationType.DIRECT
            )
        )

    private fun Message.toUiMessage(
        convType: ConversationType,
        usersMap: Map<String, User>
    ): ConversationUIMessage {
        // For group messages, include sender name
        val sender = if (convType == ConversationType.GROUP && !isMine) {
            usersMap[senderId]
        } else {
            null
        }
        
        return ConversationUIMessage(
            id = id,
            text = text,
            isMine = isMine,
            displayTime = formatTime(createdAtMs),
            isReadByEveryone = isReadByEveryone,
            senderName = sender?.displayName,
            senderPhotoUrl = sender?.photoUrl,
            status = status  // Pass through message status
        )
    }
    
    private fun formatLastSeen(lastSeenMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - lastSeenMs
        
        return when {
            diffMs < java.util.concurrent.TimeUnit.MINUTES.toMillis(1) -> "online"
            diffMs < java.util.concurrent.TimeUnit.HOURS.toMillis(1) -> {
                val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diffMs)
                "last seen ${mins}m ago"
            }
            diffMs < java.util.concurrent.TimeUnit.DAYS.toMillis(1) -> {
                val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diffMs)
                "last seen ${hours}h ago"
            }
            else -> "last seen recently"
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return ""
        val date = java.util.Date(ms)
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return fmt.format(date)
    }

    /**
     * Called when user types text in the input field.
     * Implements debounce logic:
     * - Sets typing indicator after 500ms of continuous typing
     * - Removes typing indicator after 3 seconds of inactivity
     */
    fun onTextChanged(text: String) {
        
        // Cancel previous timeout job
        typingTimeoutJob?.cancel()
        
        if (text.isNotBlank()) {
            // User is typing - set typing indicator
            viewModelScope.launch {
                typingRepo.setTyping(conversationId)
            }
            
            // Start timeout to remove typing indicator after 3 seconds
            typingTimeoutJob = viewModelScope.launch {
                delay(3000)
                typingRepo.removeTyping(conversationId)
            }
        } else {
            // Input is empty - remove typing indicator immediately
            viewModelScope.launch {
                typingRepo.removeTyping(conversationId)
            }
        }
    }
    
    fun send(text: String) {
        // Remove typing indicator when sending message
        viewModelScope.launch {
            typingRepo.removeTyping(conversationId)
            convRepo.sendMessage(conversationId, text)
        }
    }
}


