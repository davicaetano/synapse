package com.synapse.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.MessageStatus
import com.synapse.ui.components.GroupAvatar
import com.synapse.ui.components.UserAvatar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(ms: Long): String {
    if (ms <= 0) return ""
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date(ms))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: ConversationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onOpenGroupSettings: () -> Unit = {},
    onOpenMessageDetail: (String) -> Unit = {}
) {
    val ui: ConversationUIState by vm.uiState.collectAsStateWithLifecycle()
    
    // Use paged messages (Room + Paging3)
    val pagedMessages = vm.messagesPaged.collectAsLazyPagingItems<com.synapse.domain.conversation.Message>()
    
    // Message selection state
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    
    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Log Paging3 usage
    LaunchedEffect(pagedMessages.itemCount) {
        android.util.Log.d("ConversationScreen", "🔥 Using PAGING3: itemCount=${pagedMessages.itemCount}")
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { androidx.compose.material3.Text("Delete message?") },
            text = { androidx.compose.material3.Text("This message will be deleted for everyone in the conversation.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        selectedMessageId?.let { 
                            vm.deleteMessage(it)
                            selectedMessageId = null
                        }
                        showDeleteDialog = false
                    }
                ) {
                    androidx.compose.material3.Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    ConversationScreen(
        ui = ui,
        pagedMessages = pagedMessages,
        selectedMessageId = selectedMessageId,
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    ui: ConversationUIState,
    pagedMessages: androidx.paging.compose.LazyPagingItems<com.synapse.domain.conversation.Message>,
    selectedMessageId: String?,
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
    onDeleteMessage: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    // Use lastMessageId from UIState - only changes when a NEW message arrives
    // Does NOT change when loading old messages (scrolling up)
    LaunchedEffect(ui.lastMessageId) {
        if (ui.lastMessageId != null) {
            scope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        topBar = {
            ConversationTopAppBar(
                title = ui.title,
                subtitle = ui.subtitle,
                convType = ui.convType,
                otherUserPhotoUrl = ui.otherUserPhotoUrl,
                otherUserOnline = ui.otherUserOnline,
                isUserAdmin = ui.isUserAdmin,
                typingText = ui.typingText,
                hasSelection = selectedMessageId != null,
                onBackClick = if (selectedMessageId != null) onClearSelection else onBackClick,
                onOpenGroupSettings = onOpenGroupSettings,
                onOpenMessageDetail = onOpenMessageDetail,
                onDeleteMessage = onDeleteMessage,
                onSend20Messages = onSend20Messages,
                onSend100Messages = onSend100Messages,
                onSend500Messages = onSend500Messages,
                currentUserIsConnected = ui.isConnected
            )
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
                // Empty state when no messages
                if (pagedMessages.itemCount == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start the conversation!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
                
                LazyColumn(
                    reverseLayout = true,
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 8.dp,
                        bottom = 8.dp
                    )
                ) {
                    // Paging3 - loads 50 at a time
                    items(count = pagedMessages.itemCount) { index ->
                    pagedMessages[index]?.let { m ->
                        // For group messages, find sender info from members list
                        val sender = if (ui.convType == ConversationType.GROUP && !m.isMine) {
                            ui.members.find { it.id == m.senderId }
                        } else null
                        
                        // Show name/avatar only if different from previous message sender
                        val previousMessage = if (index < pagedMessages.itemCount - 1) {
                            pagedMessages[index + 1]  // reverseLayout = true, so next index is previous message
                        } else null
                        val showSenderInfo = previousMessage?.senderId != m.senderId
                        
                        MessageBubble(
                            text = m.text,
                            displayTime = formatTime(m.createdAtMs),
                            isMine = m.isMine,
                            isReadByEveryone = m.isReadByEveryone,
                            senderName = if (showSenderInfo) sender?.displayName else null,
                            senderPhotoUrl = if (showSenderInfo) sender?.photoUrl else null,
                            status = m.status,
                            isSelected = m.id == selectedMessageId,
                            onClick = { onMessageClick(m.id) }
                        )
                    }
                }
                }  // Close LazyColumn
            }  // Close Box

            // Input row with elevated background
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
    otherUserPhotoUrl: String?,
    otherUserOnline: Boolean?,
    isUserAdmin: Boolean,
    typingText: String?,
    hasSelection: Boolean,
    onBackClick: () -> Unit,
    onOpenGroupSettings: () -> Unit,
    onOpenMessageDetail: () -> Unit,
    onDeleteMessage: () -> Unit,
    onSend20Messages: () -> Unit,
    onSend100Messages: () -> Unit,
    onSend500Messages: () -> Unit,
    currentUserIsConnected: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
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
                        val displayText = typingText ?: subtitle
                        if (displayText != null) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = if (typingText != null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
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
    status: com.synapse.domain.conversation.MessageStatus = com.synapse.domain.conversation.MessageStatus.DELIVERED,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
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
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)  // Instant selection on click
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Avatar for group messages (large, aligned to top-left)
        if (!isMine) {
            if (senderPhotoUrl != null) {
                UserAvatar(
                    photoUrl = senderPhotoUrl,
                    displayName = senderName,
                    size = 32.dp,
                    showPresence = false,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                // Empty space to maintain alignment when no avatar shown
                Spacer(modifier = Modifier.width(40.dp))  // 32dp avatar + 8dp padding
            }
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
                .widthIn(min = 80.dp, max = 280.dp)  // Dynamic width with limits
                .clip(shape)
                .background(bubbleBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Message text
            Text(
                text = text,
                color = bubbleFg,
                style = MaterialTheme.typography.bodyMedium
            )

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

