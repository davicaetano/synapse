package com.synapse.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.MessageStatus
import com.synapse.ui.components.GroupAvatar
import com.synapse.ui.components.UserAvatar
import com.synapse.ui.theme.SynapseTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: ConversationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val ui: ConversationUIState by vm.uiState.collectAsStateWithLifecycle()

    ConversationScreen(
        ui = ui,
        onSendClick = { text: String -> vm.send(text) },
        onTextChanged = { text: String -> vm.onTextChanged(text) },
        onSend20Messages = { vm.send20Messages() },
        onBackClick = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    ui: ConversationUIState,
    onSendClick: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    onTextChanged: (text: String) -> Unit = {},
    onSend20Messages: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(ui.messages.size) {
        if (ui.messages.isNotEmpty()) {
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
                onBackClick = onBackClick,
                onAddMemberClick = { /* TODO: Add member to group */ },
                onSend20Messages = onSend20Messages,
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
            LazyColumn(
                reverseLayout = true,
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = 8.dp
                )
            ) {
                items(ui.messages.reversed()) { m ->
                    MessageBubble(
                        text = m.text,
                        displayTime = m.displayTime,
                        isMine = m.isMine,
                        isReadByEveryone = m.isReadByEveryone,
                        senderName = m.senderName,
                        status = m.status
                    )
                }
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
    onBackClick: () -> Unit,
    onAddMemberClick: () -> Unit,
    onSend20Messages: () -> Unit,
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
            // Show "Add member" for group admins
            if (convType == ConversationType.GROUP && isUserAdmin) {
                IconButton(onClick = onAddMemberClick) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = "Add member"
                    )
                }
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
                    DropdownMenuItem(
                        text = { Text("Send 20 messages (test)") },
                        onClick = {
                            showMenu = false
                            onSend20Messages()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors()
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
    status: com.synapse.domain.conversation.MessageStatus = com.synapse.domain.conversation.MessageStatus.DELIVERED
) {
    // Material You colors - adapts to theme
    val bg = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Rounded corners with pointer
    val shape = if (isMine)
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    else
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),  // Don't stick to edges
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        // Show sender name for group messages (only for others' messages)
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
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Message text
            Text(
                text = text,
                color = fg,
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
                    color = fg.copy(alpha = 0.6f),
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
                                    tint = fg.copy(alpha = 0.5f),
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
                                    tint = fg.copy(alpha = 0.7f),
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
                                    tint = fg.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Delivered",
                                    tint = fg.copy(alpha = 0.7f),
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
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationScreenPreview() {
    SynapseTheme {
        Column {
            ConversationScreen(
                modifier = Modifier.weight(1.0f),
                ui = ConversationUIState(
                    conversationId = "123",
                    title = "Alice",
                    subtitle = "online",
                    convType = com.synapse.domain.conversation.ConversationType.DIRECT,
                    messages = listOf(
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true
                        ),
                        ConversationUIMessage(
                            id = "m2",
                            text = "Hi!",
                            isMine = false,
                            displayTime = "09:13",
                            isReadByEveryone = true
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.PENDING
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = false,
                            status = MessageStatus.SENT
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = false,
                            status = MessageStatus.DELIVERED
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Hello!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),
                        ConversationUIMessage(
                            id = "m1",
                            text = "Last!",
                            isMine = true,
                            displayTime = "09:12",
                            isReadByEveryone = true,
                            status = MessageStatus.READ
                        ),

                    )
                ),
                onSendClick = {}
            )
            Column(
                modifier = Modifier
                    .defaultMinSize(minHeight = 50.dp)
                    .weight(1.0f)
            ) {
                Text(text = "abc")
            }
        }
    }
}


