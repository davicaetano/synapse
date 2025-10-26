package com.synapse.ui.conversation

import com.synapse.data.mapper.toDomain
import com.synapse.data.source.firestore.entity.ConversationEntity
import com.synapse.data.source.firestore.entity.UserEntity
import com.synapse.data.source.realtime.entity.PresenceEntity
import com.synapse.domain.conversation.ConversationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Mapper functions to transform raw data into ConversationUIState
 * Separated from ViewModel for testability and reusability
 */

/**
 * Compare two ConversationEntity objects for UI-relevant changes.
 * Ignores deep equality of Timestamp objects to prevent unnecessary recompositions.
 * 
 * Returns true if conversations are considered equal for UI purposes.
 */
fun areConversationsEqual(a: ConversationEntity?, b: ConversationEntity?): Boolean {
    // Both null = equal
    if (a == null && b == null) {
        android.util.Log.d("ConvMapper", "✅ Both null = EQUAL")
        return true
    }
    
    // One null = not equal
    if (a == null || b == null) {
        android.util.Log.d("ConvMapper", "❌ One null = DIFFERENT")
        return false
    }
    
    // Compare fields that matter for UI
    val isEqual = a.id == b.id &&
           a.convType == b.convType &&
           a.lastMessageText == b.lastMessageText &&
           a.updatedAtMs == b.updatedAtMs &&
           a.groupName == b.groupName &&
           a.createdBy == b.createdBy &&
           a.memberIds.sorted() == b.memberIds.sorted()
    
    android.util.Log.d("ConvMapper", if (isEqual) "✅ EQUAL (will NOT emit)" else "❌ DIFFERENT (will emit)")
    android.util.Log.d("ConvMapper", "  updatedAtMs: ${a.updatedAtMs} vs ${b.updatedAtMs}")
    android.util.Log.d("ConvMapper", "  lastMessageText: '${a.lastMessageText}' vs '${b.lastMessageText}'")
    
    return isEqual
    
    // NOTE: We intentionally ignore memberStatus comparison here because:
    // 1. Timestamp objects create new instances even with same values
    // 2. memberStatus changes are handled by separate flows (presence, typing)
    // 3. Only structural changes (members, name, last message) should trigger UI rebuild
}

/**
 * Build ConversationUIState from raw data.
 */
fun buildConversationUIState(
    userId: String,
    conversationId: String,
    conversation: ConversationEntity?,
    users: List<UserEntity>,
    presence: Map<String, PresenceEntity>,
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
            // Don't calculate status here - will be calculated in UI for real-time updates
            name to null
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

fun formatTime(ms: Long): String {
    if (ms <= 0) return ""
    val date = Date(ms)
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(date)
}

