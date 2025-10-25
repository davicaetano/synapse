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

/**
 * Search state for WhatsApp-style in-conversation search
 */
data class SearchState(
    val isActive: Boolean = false,           // Is search mode active?
    val isSearching: Boolean = false,        // Is API call in progress?
    val query: String = "",                  // Current search query
    val results: List<String> = emptyList(), // Message IDs of search results
    val currentIndex: Int = 0                // Current result index (0-based)
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
    val otherUserPhotoUrl: String? = null, // For DIRECT - other user's photo
    val typingText: String? = null,       // "John is typing..." or null
    val isConnected: Boolean = true,      // Network connectivity status
    val lastMessageId: String? = null,    // ID of the most recent message (for auto-scroll detection)
    val searchState: SearchState = SearchState() // WhatsApp-style search state
)
