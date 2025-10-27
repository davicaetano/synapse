package com.synapse.ui.conversation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.local.DevPreferences
import com.synapse.data.mapper.toDomain
import com.synapse.data.network.NetworkConnectivityMonitor
import com.synapse.data.repository.AIRepository
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import com.synapse.data.source.firestore.entity.Member
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val typingRepo: TypingRepository,
    private val aiRepo: AIRepository,
    private val auth: FirebaseAuth,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val devPreferences: DevPreferences,
    private val messageSendCoordinator: MessageSendCoordinator
) : ViewModel() {
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    
    // Job to handle typing timeout (auto-remove typing after 3 seconds of inactivity)
    private var typingTimeoutJob: Job? = null

    // Job for Firebase → Room sync (cancelled when ViewModel is cleared)
    private var messageSyncJob: Job? = null
    
    // AI active job count (for showing spinner on AI button)
    // Observes jobs running in ApplicationScope (survive ViewModel destruction)
    val activeAIJobCount: StateFlow<Int> = aiRepo.observeActiveJobCount(conversationId)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    // Dev settings - show batch message buttons
    val showBatchButtons: StateFlow<Boolean> = devPreferences.showBatchButtons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
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

        convRepo.observeConversation(conversationId, false)
            .distinctUntilChanged { convA, convB ->
                val otherMembersConvA = convA?.members?.filterKeys { it != userId && convA.members[it]?.isDeleted == false }
                val otherMembersConvB = convB?.members?.filterKeys { it != userId && convB.members[it]?.isDeleted == false }
                val mostRecentOtherSentAtA = otherMembersConvA?.values?.mapNotNull { member ->
                    member.lastMessageSentAt.toDate().time
                }?.maxOrNull()
                val mostRecentOtherSentAtB = otherMembersConvB?.values?.mapNotNull { member ->
                    member.lastMessageSentAt.toDate().time
                }?.maxOrNull()
                mostRecentOtherSentAtA == mostRecentOtherSentAtB
            }
            .onEach { conversation ->
                // Update lastSeenAt if there are new messages (guard prevents loop)
                if (conversation != null) {
                    val myLastSeenAt = conversation.members[userId]?.lastSeenAt?.toDate()?.time ?: 0L

                    // Get all other members (exclude myself and deleted members)
                    val otherMembers = conversation.members.filterKeys { it != userId && conversation.members[it]?.isDeleted == false }.keys

                    // Find the most recent lastMessageSentAt from OTHER members
                    val mostRecentOtherSentAt = otherMembers.mapNotNull { memberId ->
                        conversation.members[memberId]?.lastMessageSentAt?.toDate()?.time
                    }.maxOrNull()

                    Log.d(TAG, "🔵 Loop check - 1: mostRecentOtherSent=$mostRecentOtherSentAt, myLastSeenAt=$myLastSeenAt")

                    // Guard: only update if someone ELSE sent a message AFTER I last saw AND not already pending
                    if (mostRecentOtherSentAt != null && mostRecentOtherSentAt > myLastSeenAt) {
                        Log.d(TAG, "🔴 UPDATING lastSeenAt")
                        viewModelScope.launch {
                            convRepo.updateMemberLastSeenAtNow(conversationId)
                        }
                    }
                }
            }.launchIn(viewModelScope)

        // STEP 1: Get the conversation entity + update lastSeenAt when needed
        val conversationFlow = convRepo.observeConversation(conversationId)
        
        // STEP 2: Extract member IDs from memberIds array (already filtered by Cloud Function)
        val memberIdsFlow = conversationFlow
            .map { conv -> conv?.memberIds ?: emptyList() }
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
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "⏱️ usersFlow emitted users in ${System.currentTimeMillis() - startTime}ms - ${it}") }
        
        // STEP 4: Observe presence (only recreates when members change)
        val presenceFlow = memberIdsFlow
            .flatMapLatest { memberIds ->
                if (memberIds.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    convRepo.observePresence(memberIds)
                }
            }
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "⏱️ presenceFlow emitted in ${System.currentTimeMillis() - startTime}ms - ${it} ") }
        
        // STEP 5: Observe typing
        val typingFlow = typingRepo.observeTypingTextInConversation(conversationId)
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "⏱️ typingFlow emitted in ${System.currentTimeMillis() - startTime}ms - ${it} ") }
        
        // STEP 6: Observe network connectivity
        val isConnectedFlow = networkMonitor.isConnected
            .onEach { Log.d(TAG, "⏱️ isConnectedFlow emitted in ${System.currentTimeMillis() - startTime}ms - ${it} ") }
        
        // STEP 7: Combine typing + connectivity (to avoid 5-flow limit)
        val typingAndConnectivityFlow = combine(typingFlow, isConnectedFlow) { typing, connected ->
            typing to connected
        }
            .distinctUntilChanged()
            .onEach { Log.d(TAG, "⏱️ typingAndConnectivityFlow emitted in ${System.currentTimeMillis() - startTime}ms - ${it} ") }

        
        // STEP 8: Combine everything and build UI state
        // NOTE: Messages are handled separately via Paging3 (messagesPaged)
        combine(
            conversationFlow,
            usersFlow,
            presenceFlow,
            typingAndConnectivityFlow,
        ) { conversation, users, presence, typingAndConnectivity ->
            Log.d(TAG, "⏱️ combine() executed in ${System.currentTimeMillis() - startTime}ms")


            val (typingText, isConnected) = typingAndConnectivity
            val a = buildConversationUIState(
                userId = userId,
                conversationId = conversationId,
                conversation = conversation,
                users = users,
                presence = presence,
                typingText = typingText,
                isConnected = isConnected
            )


            if (messageSyncJob == null && a.title != "Unknown") {
                Log.d(TAG, "🚀 Starting INCREMENTAL message sync AFTER UI ready")

                if (conversation != null) {
                    val myLastSeenAt = conversation.members[userId]?.lastSeenAt?.toDate()?.time ?: 0L

                    // Get all other members (exclude myself and deleted members)
                    val otherMembers = conversation.members.filterKeys { it != userId && conversation.members[it]?.isDeleted == false }.keys

                    // Find the most recent lastMessageSentAt from OTHER members
                    val mostRecentOtherSentAt = otherMembers.mapNotNull { memberId ->
                        conversation.members[memberId]?.lastMessageSentAt?.toDate()?.time
                    }.maxOrNull()

                    Log.d(TAG, "🔵 Loop check - 1: mostRecentOtherSent=$mostRecentOtherSentAt, myLastSeenAt=$myLastSeenAt")

                    // Guard: only update if someone ELSE sent a message AFTER I last saw AND not already pending
                    if (mostRecentOtherSentAt != null && mostRecentOtherSentAt > myLastSeenAt) {
                        Log.d(TAG, "🔴 UPDATING lastSeenAt")
                        viewModelScope.launch {
                            convRepo.updateMemberLastSeenAtNow(conversationId)
                        }
                    }
                }
                // Start incremental sync (tied to viewModelScope - cancels when ViewModel dies)
                // This syncs ONLY new messages (after last Room timestamp), not all 100
                messageSyncJob = convRepo.startIncrementalSync(viewModelScope, conversationId)
            }
            Log.v(TAG, "ConversationUIState: ${a}")
            a
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily, // Keep listeners active - no reload when navigating back
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
     * Created ONCE - never recreated for optimal performance.
     * Checkmarks calculated in UI using memberStatusFlow (real-time updates).
     */
    val messagesPaged: Flow<PagingData<Message>> = run {
        val userId = auth.currentUser?.uid
        
        convRepo.observeMessagesPaged(conversationId)
            .map { pagingData ->
                pagingData.map { messageEntity ->
                    messageEntity.toDomain(
                        currentUserId = userId,
                        memberCount = messageEntity.memberIdsAtCreation.size,
                        members = emptyMap()  // Status calculated in UI with members flow
                    )
                }
            }
            .cachedIn(viewModelScope)
    }
    
    /**
     * MemberStatus flow for real-time checkmark updates.
     * Updates independently from messagesPaged for optimal performance.
     * UI recalculates status when this changes (Compose recomposes only affected items).
     */
    val membersFlow: StateFlow<Map<String, Member>> =
        convRepo.observeConversation(conversationId)
            .map { it?.members ?: emptyMap() }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    
    /**
     * Loading state for older messages fetch.
     * Prevents multiple simultaneous fetches.
     */
    private val _isLoadingOlderMessages = MutableStateFlow(false)
    val isLoadingOlderMessages: StateFlow<Boolean> = _isLoadingOlderMessages
    
    /**
     * Has reached end of message history (no more messages to fetch).
     */
    private val _hasReachedEnd = MutableStateFlow(false)
    val hasReachedEnd: StateFlow<Boolean> = _hasReachedEnd
    
    /**
     * Load older messages from Firestore when user scrolls to top.
     * 
     * Manual lazy loading approach:
     * - Fetches 200 older messages from Firebase
     * - Inserts into Room cache
     * - Paging3 automatically detects and displays them
     * - No RemoteMediator needed!
     */
    fun loadOlderMessages() {
        if (_isLoadingOlderMessages.value) {
            Log.d(TAG, "⏸️ Already loading older messages, skipping")
            return
        }
        
        if (_hasReachedEnd.value) {
            Log.d(TAG, "🏁 Already reached end of message history, skipping")
            return
        }
        
        viewModelScope.launch {
            _isLoadingOlderMessages.value = true
            try {
                Log.d(TAG, "📥 Loading older messages...")
                val fetchedCount = convRepo.fetchOlderMessages(conversationId)
                
                if (fetchedCount == 0) {
                    _hasReachedEnd.value = true
                    Log.d(TAG, "🏁 Reached end of message history")
                } else {
                    Log.d(TAG, "✅ Loaded $fetchedCount older messages")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading older messages", e)
            } finally {
                _isLoadingOlderMessages.value = false
            }
        }
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
        viewModelScope.launch {
            val memberIds = uiState.value.members.map { it.id }
            
            // 1. Send message to Firestore (existing flow)
            messageSendCoordinator.sendMessage(conversationId, text, memberIds)
            
            // 2. Trigger proactive assistant IF enabled (async, non-blocking)
            val proactiveEnabled = devPreferences.proactiveAssistantEnabled.stateIn(
                viewModelScope
            ).value
            
            if (proactiveEnabled) {
                Log.d(TAG, "🤖 Proactive Assistant enabled, triggering...")
                aiRepo.triggerProactiveAsync(conversationId)
            }
        }
    }
    
    /**
     * Send 20 test messages using Firestore batch write for performance testing
     */
    fun send20Messages() {
        viewModelScope.launch {
            val memberIds = uiState.value.members.map { it.id }
            messageSendCoordinator.sendBatchMessages(conversationId, 20, memberIds)
        }
    }
    
    /**
     * Send 100 test messages using Firestore batch write for performance testing
     */
    fun send100Messages() {
        viewModelScope.launch {
            val memberIds = uiState.value.members.map { it.id }
            messageSendCoordinator.sendBatchMessages(conversationId, 100, memberIds)
        }
    }
    
    /**
     * Send 500 test messages using Firestore batch write for performance testing
     */
    fun send500Messages() {
        viewModelScope.launch {
            val memberIds = uiState.value.members.map { it.id }
            messageSendCoordinator.sendBatchMessages(conversationId, 500, memberIds)
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
    
    // ============================================================
    // SMART SEARCH (WhatsApp-style)
    // ============================================================
    
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState
    
    /**
     * Perform semantic search in conversation
     * 
     * @param query Natural language search query
     */
    fun performSearch(query: String) {
        if (query.isBlank()) return
        
        Log.d(TAG, "🔍 Performing search: '$query'")
        _searchState.value = _searchState.value.copy(
            isActive = true,
            isSearching = true,
            query = query
        )
        
        viewModelScope.launch {
            try {
                val response = aiRepo.searchMessages(conversationId, query)
                Log.d(TAG, "✅ Search complete: ${response.message_ids.size} results in ${response.processing_time_ms}ms")
                
                _searchState.value = _searchState.value.copy(
                    isSearching = false,
                    results = response.message_ids,
                    currentIndex = if (response.message_ids.isNotEmpty()) 0 else -1
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Search failed: ${e.message}", e)
                _searchState.value = _searchState.value.copy(
                    isSearching = false,
                    results = emptyList(),
                    currentIndex = -1
                )
            }
        }
    }
    
    /**
     * Navigate to next search result
     */
    fun navigateToNextResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        
        val nextIndex = (state.currentIndex + 1) % state.results.size
        _searchState.value = state.copy(currentIndex = nextIndex)
        Log.d(TAG, "🔍 Next result: ${nextIndex + 1}/${state.results.size}")
    }
    
    /**
     * Navigate to previous search result
     */
    fun navigateToPreviousResult() {
        val state = _searchState.value
        if (state.results.isEmpty()) return
        
        val prevIndex = if (state.currentIndex == 0) {
            state.results.size - 1
        } else {
            state.currentIndex - 1
        }
        _searchState.value = state.copy(currentIndex = prevIndex)
        Log.d(TAG, "🔍 Previous result: ${prevIndex + 1}/${state.results.size}")
    }
    
    /**
     * Close search and clear results
     */
    fun closeSearch() {
        Log.d(TAG, "🔍 Closing search")
        _searchState.value = SearchState()
    }
    
    companion object {
        private const val TAG = "ConversationVM"
    }
}


