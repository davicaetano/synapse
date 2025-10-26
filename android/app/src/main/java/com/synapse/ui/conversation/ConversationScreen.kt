package com.synapse.ui.conversation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.source.firestore.entity.Member
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import com.synapse.domain.conversation.MessageStatus
import com.synapse.domain.conversation.recalculateStatus
import com.synapse.domain.user.User
import com.synapse.ui.components.GroupAvatar
import com.synapse.ui.components.UserAvatar
import com.synapse.util.getPresenceStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: ConversationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onOpenGroupSettings: () -> Unit = {},
    onOpenMessageDetail: (String) -> Unit = {},
    onOpenRefineSummary: (String) -> Unit = {},
    onOpenSummarizeInput: () -> Unit = {}
) {
    val ui: ConversationUIState by vm.uiState.collectAsStateWithLifecycle()
    
    // Use paged messages (Room + Paging3)
    val pagedMessages = vm.messagesPaged.collectAsLazyPagingItems<Message>()
    
    // Members for real-time checkmark updates
    val members by vm.membersFlow.collectAsStateWithLifecycle()
    
    // AI active job count (for spinner on AI button)
    val activeAIJobCount by vm.activeAIJobCount.collectAsStateWithLifecycle()
    
    // Dev settings
    val showBatchButtons by vm.showBatchButtons.collectAsStateWithLifecycle()
    
    // Lazy loading states
    val isLoadingOlderMessages by vm.isLoadingOlderMessages.collectAsStateWithLifecycle()
    val hasReachedEnd by vm.hasReachedEnd.collectAsStateWithLifecycle()
    
    // Message selection state
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    
    // Smart Search state
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Log Paging3 usage
    LaunchedEffect(pagedMessages.itemCount) {
        Log.d("ConversationScreen", "🔥 Using PAGING3: itemCount=${pagedMessages.itemCount}")
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete message?") },
            text = { Text("This message will be deleted for everyone in the conversation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedMessageId?.let { 
                            vm.deleteMessage(it)
                            selectedMessageId = null
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ConversationScreen(
        ui = ui,
        pagedMessages = pagedMessages,
        members = members,
        selectedMessageId = selectedMessageId,
        activeAIJobCount = activeAIJobCount,
        showBatchButtons = showBatchButtons,
        isLoadingOlderMessages = isLoadingOlderMessages,
        hasReachedEnd = hasReachedEnd,
        searchState = searchState,
        showSearchDialog = showSearchDialog,
        searchQuery = searchQuery,
        onSendClick = { text: String -> vm.send(text) },
        onTextChanged = { text: String -> vm.onTextChanged(text) },
        onSend20Messages = { vm.send20Messages() },
        onSend100Messages = { vm.send100Messages() },
        onSend500Messages = { vm.send500Messages() },
        onBackClick = onNavigateBack,
        onOpenGroupSettings = onOpenGroupSettings,
        onMessageClick = { messageId -> selectedMessageId = messageId },
        onClearSelection = { selectedMessageId = null },
        onOpenMessageDetail = { 
            selectedMessageId?.let { onOpenMessageDetail(it) }
        },
        onDeleteMessage = {
            showDeleteDialog = true  // Show confirmation dialog instead of deleting directly
        },
        onGenerateSummary = onOpenSummarizeInput,  // Navigate to summarize input screen
        onOpenRefineSummary = onOpenRefineSummary,
        onOpenSearch = { showSearchDialog = true },
        onSearchQueryChange = { searchQuery = it },
        onSearchSubmit = { query ->
            vm.performSearch(query)
            showSearchDialog = false
            searchQuery = ""  // Clear search field for next search
        },
        onSearchDialogDismiss = { 
            showSearchDialog = false
            searchQuery = ""  // Clear search field when canceling
        },
        onSearchClose = { vm.closeSearch() },
        onSearchNextResult = { vm.navigateToNextResult() },
        onSearchPreviousResult = { vm.navigateToPreviousResult() },
        onLoadOlderMessages = { vm.loadOlderMessages() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    ui: ConversationUIState,
    pagedMessages: LazyPagingItems<Message>,
    members: Map<String, Member>,
    selectedMessageId: String?,
    activeAIJobCount: Int = 0,
    showBatchButtons: Boolean = false,
    isLoadingOlderMessages: Boolean = false,
    hasReachedEnd: Boolean = false,
    searchState: SearchState = SearchState(),
    showSearchDialog: Boolean = false,
    searchQuery: String = "",
    onSendClick: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    onTextChanged: (text: String) -> Unit = {},
    onSend20Messages: () -> Unit = {},
    onSend100Messages: () -> Unit = {},
    onSend500Messages: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onOpenGroupSettings: () -> Unit = {},
    onMessageClick: (String) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onOpenMessageDetail: () -> Unit = {},
    onDeleteMessage: () -> Unit = {},
    onGenerateSummary: () -> Unit = {},
    onOpenRefineSummary: (String) -> Unit = {},
    onOpenSummarizeInput: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    onSearchDialogDismiss: () -> Unit = {},
    onSearchClose: () -> Unit = {},
    onSearchNextResult: () -> Unit = {},
    onSearchPreviousResult: () -> Unit = {},
    onLoadOlderMessages: () -> Unit = {}
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    // Use lastMessageId from UIState - only changes when a NEW message arrives
    // Does NOT change when loading old messages (scrolling up)
    val first = pagedMessages.itemSnapshotList.items.firstOrNull()?.id
    LaunchedEffect(first) {
        if (first != null) {
            scope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }
    
    // Auto-scroll to highlighted search result (WhatsApp-style)
    LaunchedEffect(searchState.currentIndex, searchState.results.size) {
        if (searchState.results.isNotEmpty() && searchState.currentIndex >= 0) {
            val highlightedMessageId = searchState.results[searchState.currentIndex]
            
            // Find the index of the highlighted message in paged list
            val messageIndex = (0 until pagedMessages.itemCount).firstOrNull { index ->
                pagedMessages[index]?.id == highlightedMessageId
            }
            
            messageIndex?.let {
                scope.launch {
                    listState.animateScrollToItem(it)
                }
            }
        }
    }
    
    // Manual lazy loading: detect when scrolled to top (oldest messages)
    // and load more messages from Firebase
    val isAtTop = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            // With reverseLayout=true, last visible item = oldest message (at top)
            lastVisibleItem?.index == pagedMessages.itemCount - 1
        }
    }
    
    // Load older messages when reaching top
    LaunchedEffect(isAtTop.value) {
        if (isAtTop.value && 
            pagedMessages.itemCount > 0 && 
            !isLoadingOlderMessages && 
            !hasReachedEnd
        ) {
            Log.d("ConversationScreen", "📍 Reached top, loading older messages...")
            onLoadOlderMessages()
        }
    }

    // Search Dialog (WhatsApp-style)
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = onSearchDialogDismiss,
            title = { Text("Smart Search") },
            text = {
                Column {
                    Text(
                        text = "Ask a natural language question about this conversation:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text("Search query") },
                        placeholder = { Text("What did we decide about the deadline?") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSearchSubmit(searchQuery) },
                    enabled = searchQuery.isNotBlank()
                ) {
                    Text("Search")
                }
            },
            dismissButton = {
                TextButton(onClick = onSearchDialogDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            // Switch TopAppBar based on search state (WhatsApp-style)
            if (searchState.isActive) {
                SearchTopAppBar(
                    query = searchState.query,
                    currentIndex = searchState.currentIndex,
                    totalResults = searchState.results.size,
                    isSearching = searchState.isSearching,
                    onClose = onSearchClose,
                    onNext = onSearchNextResult,
                    onPrevious = onSearchPreviousResult
                )
            } else {
                ConversationTopAppBar(
                    title = ui.title,
                    subtitle = ui.subtitle,
                    convType = ui.convType,
                    members = ui.members,
                    otherUserPhotoUrl = ui.otherUserPhotoUrl,
                    otherUserOnline = ui.otherUserOnline,
                    isUserAdmin = ui.isUserAdmin,
                    typingText = ui.typingText,
                    hasSelection = selectedMessageId != null,
                    activeAIJobCount = activeAIJobCount,
                    showBatchButtons = showBatchButtons,
                    onOpenSearch = onOpenSearch,
                onBackClick = if (selectedMessageId != null) onClearSelection else onBackClick,
                onOpenGroupSettings = onOpenGroupSettings,
                onOpenMessageDetail = onOpenMessageDetail,
                onDeleteMessage = onDeleteMessage,
                onGenerateSummary = onGenerateSummary,
                onSend20Messages = onSend20Messages,
                onSend100Messages = onSend100Messages,
                onSend500Messages = onSend500Messages,
                    currentUserIsConnected = ui.isConnected
                )
            }
        },
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .systemBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)  // Subtle texture difference
            ) {
                // Loading state when no messages (since bot always sends welcome message, empty = loading)
                if (pagedMessages.itemCount == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                LazyColumn(
                    reverseLayout = true,
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 8.dp
                    )
                ) {

                    // Paging3 - loads 50 at a time
                    items(count = pagedMessages.itemCount) { index ->
                    pagedMessages[index]?.let { m ->
                        // Determine message type and rendering
                        when (m.type) {
                            "ai_summary", "ai_error", "ai_action_items" -> {
                                // AI messages: special full-width layout with bot avatar and name
                                // ALWAYS show name and avatar for AI messages (even if consecutive)
                                val isHighlighted = searchState.results.isNotEmpty() && 
                                                    searchState.results.getOrNull(searchState.currentIndex) == m.id
                                
                                // Recalculate status with current members for real-time checkmarks
                                val currentStatus = m.recalculateStatus(members)
                                
                                MessageBubble(
                                    text = m.text,
                                    displayTime = formatTime(m.createdAtMs),
                                    isMine = false,  // Always render on left side
                                    isReadByEveryone = false,
                                    senderName = "Synapse AI Agent",  // Always show name for AI
                                    senderPhotoUrl = null,  // Bot avatar (will show default avatar with "S")
                                    status = currentStatus,
                                    isSelected = m.id == selectedMessageId,
                                    onClick = { onMessageClick(m.id) },
                                    isAIMessage = true,  // Special flag for full-width layout
                                    isHighlighted = isHighlighted
                                )
                            }
                            else -> {
                                // Regular messages: text, bot welcome message
                                // For group messages, find sender info from members list
                                val isGroupChat = ui.convType == ConversationType.GROUP
                                val sender = if (isGroupChat && !m.isMine) {
                                    ui.members.find { it.id == m.senderId }
                                } else null
                                
                                // Show name/avatar only if different from previous message sender
                                val previousMessage = if (index < pagedMessages.itemCount - 1) {
                                    pagedMessages[index + 1]  // reverseLayout = true, so next index is previous message
                                } else null
                                val showSenderInfo = previousMessage?.senderId != m.senderId
                                
                                // Check if this message is highlighted by search
                                val isHighlighted = searchState.results.isNotEmpty() && 
                                                    searchState.results.getOrNull(searchState.currentIndex) == m.id
                                
                                // Recalculate status with current members for real-time checkmarks
                                val currentStatus = m.recalculateStatus(members)
                                
                                MessageBubble(
                                    text = m.text,
                                    displayTime = formatTime(m.createdAtMs),
                                    isMine = m.isMine,
                                    isReadByEveryone = m.isReadByEveryone,
                                    senderName = if (showSenderInfo) sender?.displayName else null,
                                    senderPhotoUrl = if (showSenderInfo) sender?.photoUrl else null,
                                    status = currentStatus,
                                    isSelected = m.id == selectedMessageId,
                                    onClick = { onMessageClick(m.id) },
                                    needsAvatarSpace = isGroupChat && !m.isMine,  // Reserve space for avatar in groups
                                    isHighlighted = isHighlighted
                                )
                            }
                        }
                    }
                }
                }  // Close LazyColumn
            }  // Close Box

            // Input row with elevated background (hidden during search)
            if (!searchState.isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        onTextChanged(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    )
                )

                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendClick(input)
                            input = ""
                        }
                    },
                    modifier = Modifier.padding(start = 4.dp),
                    enabled = input.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (input.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                }
            }
            }  // Close if (!searchState.isActive)
        }
    }
}

// ============================================================
// TOP APP BAR (adapts based on conversation type)
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationTopAppBar(
    title: String,
    subtitle: String?,
    convType: ConversationType,
    members: List<User>,
    otherUserPhotoUrl: String?,
    otherUserOnline: Boolean?,
    isUserAdmin: Boolean,
    typingText: String?,
    hasSelection: Boolean,
    activeAIJobCount: Int,
    showBatchButtons: Boolean,
    onBackClick: () -> Unit,
    onOpenGroupSettings: () -> Unit,
    onOpenMessageDetail: () -> Unit,
    onDeleteMessage: () -> Unit,
    onGenerateSummary: () -> Unit,
    onOpenSearch: () -> Unit,
    onSend20Messages: () -> Unit,
    onSend100Messages: () -> Unit,
    onSend500Messages: () -> Unit,
    currentUserIsConnected: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // For DIRECT conversations, calculate status from members Flow (updates automatically)
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val otherUser = if (convType == ConversationType.DIRECT) {
        members.firstOrNull { it.id != currentUserId }
    } else null
    
    // Debug: Log when otherUser changes
    LaunchedEffect(otherUser?.isOnline, otherUser?.lastSeenMs) {
        Log.d("ConversationTopBar", "👤 Other user updated: isOnline=${otherUser?.isOnline}, lastSeenMs=${otherUser?.lastSeenMs}")
    }
    
    // Calculate dynamic subtitle for DIRECT (GROUP uses static subtitle)
    // members comes from Flow -> when it changes, Compose recomposes automatically
    val dynamicSubtitle = when (convType) {
        ConversationType.DIRECT -> {
            getPresenceStatus(
                isOnline = otherUser?.isOnline ?: false,
                lastSeenMs = otherUser?.lastSeenMs
            )
        }
        else -> subtitle // GROUP/SELF use static subtitle
    }
    
    Log.d("ConversationTopBar", "🔄 Recomposing: dynamicSubtitle=$dynamicSubtitle")
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show avatar for DIRECT and GROUP
                when (convType) {
                    ConversationType.DIRECT -> {
                        // Only show presence if current user is connected
                        UserAvatar(
                            photoUrl = otherUserPhotoUrl,
                            displayName = title,
                            size = 36.dp,
                            showPresence = currentUserIsConnected,
                            isOnline = otherUserOnline ?: false
                        )
                    }
                    ConversationType.GROUP -> {
                        GroupAvatar(
                            groupName = title,
                            size = 36.dp
                        )
                    }
                    else -> {} // SELF - no avatar
                }

                if (convType != ConversationType.SELF) {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Title + subtitle/typing
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    // Only show subtitle/typing if current user is connected
                    // No point showing other user's status if you can't communicate
                    if (currentUserIsConnected) {
                        // Prioritize typing indicator over subtitle
                        val displayText = typingText ?: dynamicSubtitle
                        if (displayText != null) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = if (typingText != null) FontStyle.Italic else FontStyle.Normal
                                ),
                                color = when {
                                    typingText != null -> MaterialTheme.colorScheme.primary
                                    displayText == "online" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            // Show Delete + Info buttons when message is selected
            if (hasSelection) {
                IconButton(onClick = onDeleteMessage) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete message"
                    )
                }
                IconButton(onClick = onOpenMessageDetail) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Message info"
                    )
                }
            } else {
                // AI Summarize button (always enabled, can request multiple summaries)
                IconButton(
                    onClick = onGenerateSummary,
                    enabled = true  // Always enabled
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Generate AI Summary",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        // Show spinner overlay when AI jobs are running
                        if (activeAIJobCount > 0) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.Center),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
                
                // Smart Search button (WhatsApp-style semantic search)
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Smart Search"
                    )
                }
                
                // Menu button with dropdown
                Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Group info (only for groups)
                    if (convType == ConversationType.GROUP) {
                        DropdownMenuItem(
                            text = { Text("Group info") },
                            onClick = {
                                showMenu = false
                                onOpenGroupSettings()
                            }
                        )
                        HorizontalDivider()
                    }
                    
                    // Debug: Batch message buttons (only shown if enabled in Dev Settings)
                    if (showBatchButtons) {
                        DropdownMenuItem(
                            text = { Text("Send 20 messages (test)") },
                            onClick = {
                                showMenu = false
                                onSend20Messages()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send 100 messages (test)") },
                            onClick = {
                                showMenu = false
                                onSend100Messages()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send 500 messages (test)") },
                            onClick = {
                                showMenu = false
                                onSend500Messages()
                            }
                        )
                    }
                }
                }  // Close Box
            }  // Close else
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ============================================================
// MESSAGE BUBBLE (shows sender name for groups)
// ============================================================

@Composable
private fun MessageBubble(
    text: String,
    displayTime: String,
    isMine: Boolean,
    isReadByEveryone: Boolean = false,
    senderName: String? = null,
    senderPhotoUrl: String? = null,
    status: MessageStatus = MessageStatus.DELIVERED,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    isAIMessage: Boolean = false,  // Special flag for AI messages (full-width layout)
    isHighlighted: Boolean = false,  // WhatsApp-style search highlight
    needsAvatarSpace: Boolean = false  // Reserve space for avatar even when not shown
) {
    // Message bubble colors
    val bubbleBg = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleFg = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Rounded corners with pointer
    // RoundedCornerShape(topLeft, topRight, bottomRight, bottomLeft)
    val shape = if (isMine)
        RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)  // Top-right pointed
    else
        RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)  // Top-left pointed

    // Full-width selectable background (WhatsApp-style)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isHighlighted -> Color(0xFFFFEB3B).copy(alpha = 0.4f)  // Yellow highlight for search
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)  // Instant selection on click
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Avatar for group messages (large, aligned to top-left)
        // ALWAYS reserve space in group chats (for alignment), show avatar only when senderName != null
        if (!isMine && needsAvatarSpace) {
            if (senderName != null) {
                // Show avatar with name
                if (senderPhotoUrl != null) {
                    UserAvatar(
                        photoUrl = senderPhotoUrl,
                        displayName = senderName,
                        size = 32.dp,
                        showPresence = false,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                } else {
                    // Default avatar with first letter
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = senderName.first().uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            } else {
                // Empty space to maintain alignment for consecutive messages from same sender
                Spacer(modifier = Modifier.width(40.dp))  // 32dp avatar + 8dp padding
            }
        } else if (!isMine && isAIMessage) {
            // AI messages: always show avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            // Show sender name for group messages
            if (!isMine && senderName != null) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                    fontSize = 11.sp
                )
            }

        // Message bubble
        Column(
            modifier = Modifier
                .then(
                    if (isAIMessage) {
                        // AI messages: full-width (go to screen edge)
                        Modifier.fillMaxWidth()
                    } else {
                        // Regular messages: constrained width
                        Modifier.widthIn(min = 80.dp, max = 280.dp)
                    }
                )
                .clip(shape)
                .background(bubbleBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // AI messages: Expandable text with "Show more/less"
            if (isAIMessage) {
                var isExpanded by remember { mutableStateOf(false) }
                val maxCharsCollapsed = 300
                val shouldTruncate = text.length > maxCharsCollapsed
                
                // Message text (truncated or full)
                Text(
                    text = if (shouldTruncate && !isExpanded) {
                        text.take(maxCharsCollapsed) + "..."
                    } else {
                        text
                    },
                    color = bubbleFg,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // "Show more/less" button
                if (shouldTruncate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isExpanded) "Show less ▲" else "Show more ▼",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 4.dp)
                    )
                }
            } else {
                // Regular messages: normal text
                Text(
                    text = text,
                    color = bubbleFg,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Time + status indicators (WhatsApp-style)
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayTime,
                    color = bubbleFg.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )

                // Show status indicators only for your messages
                if (isMine) {
                    Spacer(modifier = Modifier.width(4.dp))

                    when (status) {
                        MessageStatus.PENDING -> {
                            // ⏱️ Clock icon - message pending (offline/not sent)
                            Row {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Sending",
                                    tint = bubbleFg.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        MessageStatus.SENT -> {
                            // ✓ Single gray check - sent to server (aligned right like double checks)
                            Row{
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Sent",
                                    tint = bubbleFg.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        MessageStatus.DELIVERED -> {
                            // ✓✓ Double gray checks - delivered to device
                            Box {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = bubbleFg.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Delivered",
                                    tint = bubbleFg.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(12.dp)
                                )
                            }
                        }
                        MessageStatus.READ -> {
                            // ✓✓ Double blue checks - read by all
                            // Using a visible blue that works on both light/dark backgrounds
                            val readBlue = Color(0xFF2196F3)  // Material Blue 500
                            Box {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = readBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Delivered",
                                    tint = readBlue,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }  // Close Column (message bubble)
        }  // Close Column (sender name + bubble)
    }  // Close Row (avatar + content)
}

