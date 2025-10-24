package com.synapse.ui.conversation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.network.NetworkConnectivityMonitor
import com.synapse.data.repository.AIRepository
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import com.synapse.domain.user.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val typingRepo: TypingRepository,
    private val aiRepo: AIRepository,
    private val auth: FirebaseAuth,
    private val networkMonitor: NetworkConnectivityMonitor
) : ViewModel() {
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    
    // Job to handle typing timeout (auto-remove typing after 3 seconds of inactivity)
    private var typingTimeoutJob: Job? = null

    // Job for Firebase → Room sync (cancelled when ViewModel is cleared)
    private var messageSyncJob: Job? = null
    
    // Guard to prevent duplicate lastSeenAt updates while Firestore processes
    private var lastSeenUpdatePending = false
    
    // Global message counter for batch testing (increments with each batch sent)
    private var globalMessageCounter = 0
    
    // AI Summary generation state
    private val _isGeneratingSummary = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary
    
    private val _summaryError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError
    
    fun clearSummaryError() {
        _summaryError.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        // Remove typing indicator when leaving conversation
        viewModelScope.launch {
            typingRepo.removeTyping(conversationId)
        }
        // Cancel message sync job
        messageSyncJob?.cancel()
        Log.d(TAG, "🔌 Message sync cancelled for: $conversationId")
    }

    val uiState: StateFlow<ConversationUIState> = run {
        val userId = auth.currentUser?.uid ?: ""
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "🚀 Building uiState...")
        
        // STEP 1: Get the conversation entity + update lastSeenAt when needed
        val conversationFlow = convRepo.observeConversation(userId, conversationId)
            .distinctUntilChanged()
            .onEach { conversation ->
                // Update lastSeenAt if there are new messages (guard prevents loop)
                if (conversation != null) {
                    val myLastSeenAt = conversation.memberStatus[userId]?.lastSeenAt?.toDate()?.time ?: 0L
                    
                    // Get all other members (exclude myself)
                    val otherMembers = conversation.memberIds.filter { it != userId }
                    
                    // Find the most recent lastMessageSentAt from OTHER members
                    val mostRecentOtherSentAt = otherMembers.mapNotNull { memberId ->
                        conversation.memberStatus[memberId]?.lastMessageSentAt?.toDate()?.time
                    }.maxOrNull()
                    
                    Log.d(TAG, "🔵 Loop check - 1: mostRecentOtherSent=$mostRecentOtherSentAt, myLastSeenAt=$myLastSeenAt, pending=$lastSeenUpdatePending")
                    
                    // Guard: only update if someone ELSE sent a message AFTER I last saw AND not already pending
                    if (mostRecentOtherSentAt != null && mostRecentOtherSentAt > myLastSeenAt && !lastSeenUpdatePending) {
                        lastSeenUpdatePending = true
                        Log.d(TAG, "🔴 UPDATING lastSeenAt")
                        viewModelScope.launch {
                            convRepo.updateMemberLastSeenAtNow(conversationId)
                            delay(1000)  // Wait for Firestore write to complete and propagate
                            lastSeenUpdatePending = false
                        }
                    }
                }
                
                Log.d(TAG, "⏱️ conversationFlow emitted in ${System.currentTimeMillis() - startTime}ms")
            }
        
        // STEP 2: Extract member IDs (stable)
        val memberIdsFlow = conversationFlow
            .map { conv -> conv?.memberIds?.sorted() ?: emptyList() }
            .distinctUntilChanged()
        
        // STEP 3: Observe users (only recreates when members change)
        val usersFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                if (memberIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    convRepo.observeUsers(memberIds)
                }
            }
            .onEach { Log.d(TAG, "⏱️ usersFlow emitted ${it.size} users in ${System.currentTimeMillis() - startTime}ms") }
        
        // STEP 4: Observe presence (only recreates when members change)
        val presenceFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                if (memberIds.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    convRepo.observePresence(memberIds)
                }
            }
            .onEach { Log.d(TAG, "⏱️ presenceFlow emitted ${it.size} in ${System.currentTimeMillis() - startTime}ms") }
        
        // STEP 5: Observe typing
        val typingFlow = typingRepo.observeTypingTextInConversation(conversationId)
        
        // STEP 6: Observe network connectivity
        val isConnectedFlow = networkMonitor.isConnected
        
        // STEP 7: Combine typing + connectivity (to avoid 5-flow limit)
        val typingAndConnectivityFlow = combine(typingFlow, isConnectedFlow) { typing, connected ->
            typing to connected
        }
        
        // STEP 8: Combine everything and build UI state
        // NOTE: Messages are handled separately via Paging3 (messagesPaged)
        combine(
            conversationFlow,
            usersFlow,
            presenceFlow,
            typingAndConnectivityFlow
        ) { conversation, users, presence, typingAndConnectivity ->
            Log.d(TAG, "⏱️ combine() executed in ${System.currentTimeMillis() - startTime}ms")
            val (typingText, isConnected) = typingAndConnectivity
            buildConversationUIState(
                userId = userId,
                conversation = conversation,
                users = users,
                presence = presence,
                typingText = typingText,
                isConnected = isConnected
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
     * Paged messages flow using Room + Paging3.
     * Provides efficient pagination for large message lists.
     * 
     * Combines with conversationFlow to get memberStatus for status calculation.
     */
    val messagesPaged: Flow<PagingData<Message>> = run {
        val userId = auth.currentUser?.uid
        val conversationFlow = convRepo.observeConversation(userId ?: "", conversationId)
        
        // Use flatMapLatest to switch to new PagingData when conversation changes
        conversationFlow.flatMapLatest { conversation ->
            convRepo.observeMessagesPaged(conversationId)
                .map { pagingData ->
                    pagingData.map { messageEntity ->
                        messageEntity.toDomain(
                            currentUserId = userId,
                            memberCount = messageEntity.memberIdsAtCreation.size,
                            memberStatus = conversation?.memberStatus ?: emptyMap()  // Pass memberStatus for status calculation
                        )
                    }
                }
        }.cachedIn(viewModelScope)
    }
    
    // Background side effects
    init {
//        // Update lastSeenAt when entering conversation (with guard to prevent unnecessary writes)
//        val userId = auth.currentUser?.uid ?: ""
//        viewModelScope.launch {
//            // Wait for first conversation emission to check if update is needed
//            convRepo.observeConversation(userId, conversationId)
//                .take(1)
//                .collect { conversation ->
//                    if (conversation != null) {
//                        val myLastSeenAt = conversation.memberStatus[userId]?.lastSeenAt?.toDate()?.time ?: 0L
//
//                        // Get all other members (exclude myself)
//                        val otherMembers = conversation.memberIds.filter { it != userId }
//
//                        // Find the most recent lastMessageSentAt from OTHER members
//                        val mostRecentOtherSentAt = otherMembers.mapNotNull { memberId ->
//                            conversation.memberStatus[memberId]?.lastMessageSentAt?.toDate()?.time
//                        }.maxOrNull()
//                        android.util.Log.d("InboxVM", "🔵 Loop check - 3: convId=${conversation.id.takeLast(6)}, mostRecentOtherSent=$mostRecentOtherSentAt, myLastReceived=$myLastSeenAt")
//                        // Only update if someone ELSE sent a message AFTER I last saw
//                        if (mostRecentOtherSentAt != null && mostRecentOtherSentAt > myLastSeenAt) {
//                            Log.d(TAG, "📖 Updating lastSeenAt (has unread messages)")
//                            convRepo.updateMemberLastSeenAtNow(conversationId)
//                        } else {
//                            Log.d(TAG, "📖 Skip lastSeenAt update (no new messages)")
//                        }
//                    }
//                }
//        }
        
        // Start Firebase → Room sync AFTER first UI state emission (avoid blocking avatar/UI)
        uiState
            .onEach { state ->
                if (messageSyncJob == null && state.title.isNotBlank() && state.title != "Loading...") {
                    Log.d(TAG, "🚀 Starting message sync AFTER UI ready")
                    
                    // Start sync job (tied to viewModelScope - cancels when ViewModel dies)
                    messageSyncJob = viewModelScope.launch {
                        convRepo.listenMessagesFromFirestore(conversationId)
                            .collect { firestoreMessages ->
                            // Sync Firestore → Room
                            convRepo.upsertMessagesToRoom(firestoreMessages, conversationId)
                            Log.d(TAG, "✅ Synced ${firestoreMessages.size} messages from Firestore to Room")
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Build ConversationUIState from raw data.
     * Separated for clarity and testability.
     */
    private fun buildConversationUIState(
        userId: String,
        conversation: ConversationEntity?,
        users: List<UserEntity>,
        presence: Map<String, com.synapse.data.source.realtime.entity.PresenceEntity>,
        typingText: String?,
        isConnected: Boolean
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
        
        // NOTE: Messages are handled separately via Paging3 (messagesPaged)
        // uiState.messages will always be empty
        
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
        
        return ConversationUIState(
            conversationId = conversation.id,
            title = title,
            subtitle = subtitle,
            messages = emptyList(),  // Always empty - messages handled by Paging3
            convType = convType,
            members = members,
            isUserAdmin = conversation.createdBy == userId,
            otherUserOnline = if (convType == ConversationType.DIRECT) {
                members.firstOrNull { it.id != userId }?.isOnline
            } else null,
            otherUserPhotoUrl = if (convType == ConversationType.DIRECT) {
                members.firstOrNull { it.id != userId }?.photoUrl
            } else null,
            typingText = typingText,
            isConnected = isConnected,
            lastMessageId = null  // Not used anymore - Paging3 handles scroll detection
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
            
            // Get memberIds from current state (already loaded in memory - no extra Firestore read!)
            val memberIds = uiState.value.members.map { it.id }
            
            // Send message
            convRepo.sendMessage(conversationId, text, memberIds)
            
            // Update lastMessageSentAt (for badge logic)
            convRepo.updateMemberLastMessageSentAtNow(conversationId)
        }
    }
    
    /**
     * Send 20 test messages using Firestore batch write for performance testing
     */
    fun send20Messages() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "Sending 20 messages via batch...")
            
            // Get memberIds from current state (already loaded in memory - no extra Firestore read!)
            val memberIds = uiState.value.members.map { it.id }
            
            val startNum = globalMessageCounter + 1
            val messages = (startNum until startNum + 20).map { i -> "Test message #$i" }
            globalMessageCounter += 20
            
            convRepo.sendMessagesBatch(conversationId, messages, memberIds)
            
            // Update lastMessageSentAt (for badge logic)
            convRepo.updateMemberLastMessageSentAtNow(conversationId)
            
            Log.d("ConversationViewModel", "✅ 20 messages sent successfully (#$startNum-#$globalMessageCounter)")
        }
    }
    
    /**
     * Send 100 test messages using Firestore batch write for performance testing
     */
    fun send100Messages() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "Sending 100 messages via batch...")
            
            // Get memberIds from current state (already loaded in memory - no extra Firestore read!)
            val memberIds = uiState.value.members.map { it.id }
            
            val startNum = globalMessageCounter + 1
            val messages = (startNum until startNum + 100).map { i -> "Test message #$i" }
            globalMessageCounter += 100
            
            convRepo.sendMessagesBatch(conversationId, messages, memberIds)
            
            // Update lastMessageSentAt (for badge logic)
            convRepo.updateMemberLastMessageSentAtNow(conversationId)
            
            Log.d(TAG, "✅ 100 messages sent successfully (#$startNum-#$globalMessageCounter)")
        }
    }
    
    /**
     * Send 500 test messages using Firestore batch write for performance testing
     */
    fun send500Messages() {
        viewModelScope.launch {
            Log.d("ConversationViewModel", "Sending 500 messages via batch...")
            
            // Get memberIds from current state (already loaded in memory - no extra Firestore read!)
            val memberIds = uiState.value.members.map { it.id }
            
            val startNum = globalMessageCounter + 1
            val messages = (startNum until startNum + 500).map { i -> "Test message #$i" }
            globalMessageCounter += 500
            
            convRepo.sendMessagesBatch(conversationId, messages, memberIds)
            
            // Update lastMessageSentAt (for badge logic)
            convRepo.updateMemberLastMessageSentAtNow(conversationId)
            
            Log.d(TAG, "✅ 500 messages sent successfully (#$startNum-#$globalMessageCounter)")
        }
    }
    
    /**
     * Delete a message (soft delete).
     * The message will be marked as deleted in both Firestore and Room cache.
     */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                convRepo.deleteMessage(conversationId, messageId)
                Log.d(TAG, "✅ Message deleted: $messageId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to delete message: $messageId", e)
            }
        }
    }
    
    /**
     * Generate AI thread summary
     * Backend creates an AI_SUMMARY message in Firestore
     * Message will appear automatically via listener
     * 
     * @param customInstructions Optional custom instructions for focused summary
     * @return Result with success/failure
     */
    fun generateSummary(customInstructions: String? = null) {
        viewModelScope.launch {
            try {
                _isGeneratingSummary.value = true
                _summaryError.value = null  // Clear previous errors
                Log.d(TAG, "📊 Generating thread summary...")
                
                val result = aiRepo.summarizeThread(
                    conversationId = conversationId,
                    customInstructions = customInstructions
                )
                
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    Log.d(TAG, "✅ Summary generated: ${response?.message_id?.takeLast(6)}")
                    Log.d(TAG, "📈 Processed ${response?.message_count} messages in ${response?.processing_time_ms}ms")
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "❌ Failed to generate summary: $errorMsg")
                    _summaryError.value = errorMsg
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Network error"
                Log.e(TAG, "❌ Exception generating summary: $errorMsg", e)
                _summaryError.value = errorMsg
            } finally {
                _isGeneratingSummary.value = false
            }
        }
    }
    
    /**
     * Refine existing AI summary
     * Creates a new refined AI_SUMMARY message
     * 
     * @param previousSummaryId Message ID of the previous summary
     * @param refinementInstructions User's refinement instructions
     * @return Result with success/failure
     */
    fun refineSummary(previousSummaryId: String, refinementInstructions: String) {
        viewModelScope.launch {
            try {
                _summaryError.value = null  // Clear previous errors
                Log.d(TAG, "🔧 Refining summary: ${previousSummaryId.takeLast(6)}")
                
                val result = aiRepo.refineSummary(
                    conversationId = conversationId,
                    previousSummaryId = previousSummaryId,
                    refinementInstructions = refinementInstructions
                )
                
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    Log.d(TAG, "✅ Refined summary created: ${response?.message_id?.takeLast(6)}")
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "❌ Failed to refine summary: $errorMsg")
                    _summaryError.value = errorMsg
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Network error"
                Log.e(TAG, "❌ Exception refining summary: $errorMsg", e)
                _summaryError.value = errorMsg
            }
        }
    }
    
    companion object {
        private const val TAG = "ConversationVM"
    }
}


