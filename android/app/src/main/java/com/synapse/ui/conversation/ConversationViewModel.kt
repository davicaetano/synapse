package com.synapse.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    val uiState: StateFlow<ConversationUIState> = run {
        val userId = auth.currentUser?.uid ?: ""
        
        // STEP 1: Get the conversation entity
        val conversationFlow = convRepo.observeConversation(userId, conversationId)
        
        // STEP 2: Get messages for this conversation
        val messagesFlow = convRepo.observeMessages(conversationId)
        
        // STEP 3: Extract member IDs (stable)
        val memberIdsFlow = conversationFlow
            .map { conv -> conv?.memberIds?.sorted() ?: emptyList() }
            .distinctUntilChanged()
        
        // STEP 4: Observe users (only recreates when members change)
        val usersFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                if (memberIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    convRepo.observeUsers(memberIds)
                }
            }
        
        // STEP 5: Observe presence (only recreates when members change)
        val presenceFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                if (memberIds.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    convRepo.observePresence(memberIds)
                }
            }
        
        // STEP 6: Observe typing
        val typingFlow = typingRepo.observeTypingTextInConversation(conversationId)
        
        // STEP 7: Combine everything and build UI state
        combine(
            conversationFlow,
            messagesFlow,
            usersFlow,
            presenceFlow,
            typingFlow
        ) { conversation, messages, users, presence, typingText ->
            buildConversationUIState(
                userId = userId,
                conversation = conversation,
                messages = messages,
                users = users,
                presence = presence,
                typingText = typingText
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily, // Keep listeners active - messages stay loaded
            ConversationUIState(
                conversationId = conversationId,
                title = "",
                messages = emptyList(),
                convType = ConversationType.DIRECT
            )
        )
    }
    
    /**
     * Build ConversationUIState from raw data.
     * Separated for clarity and testability.
     */
    private fun buildConversationUIState(
        userId: String,
        conversation: com.synapse.data.source.firestore.entity.ConversationEntity?,
        messages: List<com.synapse.data.source.firestore.entity.MessageEntity>,
        users: List<com.synapse.data.source.firestore.entity.UserEntity>,
        presence: Map<String, com.synapse.data.source.realtime.entity.PresenceEntity>,
        typingText: String?
    ): ConversationUIState {
        if (conversation == null) {
            // Conversation not found or loading
            return ConversationUIState(
                conversationId = conversationId,
                title = "Loading...",
                messages = emptyList(),
                convType = ConversationType.DIRECT
            )
        }
        
        // Build user map
        val usersMap = users.associateBy { it.id }
        
        // Build members with presence
        val members = conversation.memberIds.mapNotNull { memberId ->
            val userEntity = usersMap[memberId]
            val presenceEntity = presence[memberId]
            userEntity?.toDomain(
                presence = presenceEntity,
                isMyself = (memberId == userId)
            )
        }
        
        // Build messages with domain model
        val domainMessages = messages.map { msgEntity ->
            msgEntity.toDomain(
                currentUserId = userId,
                memberCount = members.size
            )
        }
        
        // Build title and subtitle
        val convType = try {
            ConversationType.valueOf(conversation.convType)
        } catch (e: Exception) {
            ConversationType.DIRECT
        }
        
        val (title, subtitle) = when (convType) {
            ConversationType.SELF -> "AI Assistant" to null
            ConversationType.DIRECT -> {
                val otherUser = members.firstOrNull { it.id != userId }
                val name = otherUser?.displayName ?: "Unknown"
                val status = when {
                    otherUser?.isOnline == true -> "online"
                    otherUser?.lastSeenMs != null -> formatLastSeen(otherUser.lastSeenMs)
                    else -> null
                }
                name to status
            }
            ConversationType.GROUP -> {
                val name = conversation.groupName ?: "Group"
                val memberCount = "${members.size} member${if (members.size == 1) "" else "s"}"
                name to memberCount
            }
        }
        
        // Build user map for sender names
        val membersMap = members.associateBy { it.id }
        
        return ConversationUIState(
            conversationId = conversation.id,
            title = title,
            subtitle = subtitle,
            messages = domainMessages.map { it.toUiMessage(convType, membersMap) },
            convType = convType,
            members = members,
            isUserAdmin = conversation.createdBy == userId,
            otherUserOnline = if (convType == ConversationType.DIRECT) {
                members.firstOrNull { it.id != userId }?.isOnline
            } else null,
            otherUserPhotoUrl = if (convType == ConversationType.DIRECT) {
                members.firstOrNull { it.id != userId }?.photoUrl
            } else null,
            typingText = typingText
        )
    }

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


