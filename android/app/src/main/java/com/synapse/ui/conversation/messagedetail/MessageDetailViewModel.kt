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
        
        // Observe conversation to get memberStatus and members
        convRepo.observeConversation(userId, conversationId)
            .flatMapLatest { conversation ->
                if (conversation == null) {
                    flowOf(MessageDetailUIState(isLoading = true))
                } else {
                    // Get message from Firestore (via Room would be better but needs single message query)
                    convRepo.listenMessagesFromFirestore(conversationId)
                        .map { messages ->
                            val message = messages.find { it.id == messageId }
                            
                            if (message == null) {
                                MessageDetailUIState(isLoading = false)
                            } else {
                                // Get sender info
                                val senderEntity = conversation.memberIds
                                    .find { it == message.senderId }
                                
                                // Calculate who delivered/read based on memberStatus
                                val memberStatus = conversation.memberStatus
                                val serverTimestamp = message.serverTimestamp ?: 0L
                                
                                // Delivered = lastReceivedAt >= serverTimestamp
                                val deliveredUserIds = conversation.memberIds
                                    .filter { memberId ->
                                        if (memberId == message.senderId) return@filter false
                                        val lastReceivedMs = memberStatus[memberId]?.lastReceivedAt?.toDate()?.time
                                        lastReceivedMs != null && lastReceivedMs >= serverTimestamp
                                    }
                                
                                // Read = lastSeenAt >= serverTimestamp
                                val readUserIds = conversation.memberIds
                                    .filter { memberId ->
                                        if (memberId == message.senderId) return@filter false
                                        val lastSeenMs = memberStatus[memberId]?.lastSeenAt?.toDate()?.time
                                        lastSeenMs != null && lastSeenMs >= serverTimestamp
                                    }
                                
                                MessageDetailUIState(
                                    messageId = message.id,
                                    text = message.text,
                                    senderId = message.senderId,
                                    senderName = "", // Will be filled by combining with users
                                    sentAt = message.createdAtMs,
                                    serverTimestamp = serverTimestamp,
                                    deliveredTo = emptyList(), // Will be filled
                                    readBy = emptyList(), // Will be filled
                                    isLoading = false
                                )
                            }
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, MessageDetailUIState(isLoading = true))
    }
    
    // Observe users to fill names
    val users: StateFlow<List<com.synapse.domain.user.User>> = run {
        val userId = auth.currentUser?.uid ?: ""
        
        convRepo.observeConversation(userId, conversationId)
            .flatMapLatest { conversation ->
                if (conversation == null || conversation.memberIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    convRepo.observeUsers(conversation.memberIds)
                        .map { userEntities ->
                            userEntities.map { it.toDomain(presence = null, isMyself = it.id == userId) }
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }
    
    companion object {
        private const val TAG = "MessageDetailVM"
    }
}

