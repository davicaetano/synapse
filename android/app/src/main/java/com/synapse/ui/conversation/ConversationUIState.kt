package com.synapse.ui.conversation

import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.MessageStatus
import com.synapse.domain.user.User

data class ConversationUIMessage(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val displayTime: String,
    val isReadByEveryone: Boolean = false,
    val senderName: String? = null,      // For group messages - who sent it
    val senderPhotoUrl: String? = null,  // For group messages - sender's photo
    val status: MessageStatus = MessageStatus.DELIVERED  // Message delivery status
)

data class ConversationUIState(
    val conversationId: String,
    val title: String,
    val subtitle: String? = null,         // "online", "3 members", etc
    val messages: List<ConversationUIMessage>,
    val convType: ConversationType,
    val members: List<User> = emptyList(),
    val isUserAdmin: Boolean = false,     // True if current user is group admin
    val otherUserOnline: Boolean? = null, // For DIRECT - other user's online status
    val otherUserPhotoUrl: String? = null // For DIRECT - other user's photo
)
