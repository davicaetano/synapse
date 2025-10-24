package com.synapse.ui.conversation.messagedetail

import com.synapse.domain.user.User

data class MessageDetailUIState(
    val messageId: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val sentAt: Long = 0L,
    val serverTimestamp: Long? = null,
    val memberStatuses: List<MemberDeliveryStatus> = emptyList(),
    val isLoading: Boolean = false
)

data class MemberDeliveryStatus(
    val user: User,
    val status: DeliveryStatus
)

enum class DeliveryStatus {
    PENDING,      // ⏱️ Not sent yet (no serverTimestamp)
    SENT,         // ✓ Sent to server but not received by user
    DELIVERED,    // ✓✓ Received by user (lastReceivedAt >= serverTimestamp)
    READ          // ✓✓ (blue) Read by user (lastSeenAt >= serverTimestamp)
}

