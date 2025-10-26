package com.synapse.ui.conversation.messagedetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.synapse.data.mapper.toDomain
import com.synapse.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val convRepo: ConversationRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    
    private val conversationId: String = savedStateHandle.get<String>("conversationId") ?: ""
    private val messageId: String = savedStateHandle.get<String>("messageId") ?: ""
    
    val uiState: StateFlow<MessageDetailUIState> = run {
        val userId = auth.currentUser?.uid ?: ""
        
        // Combine conversation + message + users to calculate individual member statuses
        convRepo.observeConversation(conversationId)
            .flatMapLatest { conversation ->
                if (conversation == null) {
                    flowOf(MessageDetailUIState(isLoading = true))
                } else {
                    // Get message from Firestore
                    convRepo.listenMessagesFromFirestore(conversationId)
                        .flatMapLatest { messages ->
                            val message = messages.find { it.id == messageId }
                            
                            if (message == null) {
                                flowOf(MessageDetailUIState(isLoading = false))
                            } else {
                                // Get active member IDs (exclude deleted and bots)
                                val activeMemberIds = conversation.members
                                    .filterValues { !it.isDeleted && !it.isBot }
                                    .keys
                                    .toList()
                                
                                // Get users to map IDs to User objects
                                convRepo.observeUsers(activeMemberIds)
                                    .map { userEntities ->
                                        val usersMap = userEntities.map { it.toDomain(null, it.id == userId) }
                                            .associateBy { it.id }
                                        
                                        val sender = usersMap[message.senderId]
                                        val serverTimestamp = message.serverTimestamp?.toDate()?.time ?: 0L
                                        val members = conversation.members
                                        
                                        // Calculate status for each member (except sender)
                                        val memberStatuses = activeMemberIds
                                            .filter { it != message.senderId }  // Exclude sender
                                            .mapNotNull { memberId ->
                                                val user = usersMap[memberId] ?: return@mapNotNull null
                                                val member = members[memberId]
                                                
                                                val status = calculateMemberStatus(
                                                    serverTimestamp = serverTimestamp,
                                                    lastReceivedAt = member?.lastReceivedAt?.toDate()?.time,
                                                    lastSeenAt = member?.lastSeenAt?.toDate()?.time
                                                )
                                                
                                                MemberDeliveryStatus(user = user, status = status)
                                            }
                                        
                                        MessageDetailUIState(
                                            messageId = message.id,
                                            text = message.text,
                                            senderId = message.senderId,
                                            senderName = sender?.displayName ?: "Unknown",
                                            sentAt = message.localTimestamp.toDate().time,
                                            serverTimestamp = serverTimestamp,
                                            memberStatuses = memberStatuses,
                                            isLoading = false
                                        )
                                    }
                            }
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, MessageDetailUIState(isLoading = true))
    }
    
    private fun calculateMemberStatus(
        serverTimestamp: Long,
        lastReceivedAt: Long?,
        lastSeenAt: Long?
    ): DeliveryStatus {
        return when {
            // No server timestamp yet = PENDING
            serverTimestamp <= 0 -> DeliveryStatus.PENDING
            
            // Read = lastSeenAt >= serverTimestamp
            lastSeenAt != null && lastSeenAt >= serverTimestamp -> DeliveryStatus.READ
            
            // Delivered = lastReceivedAt >= serverTimestamp
            lastReceivedAt != null && lastReceivedAt >= serverTimestamp -> DeliveryStatus.DELIVERED
            
            // Sent to server but not received by user yet
            else -> DeliveryStatus.SENT
        }
    }
    
    companion object {
        private const val TAG = "MessageDetailVM"
    }
}

