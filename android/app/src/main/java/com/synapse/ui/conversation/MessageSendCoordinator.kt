package com.synapse.ui.conversation

import android.util.Log
import com.synapse.data.repository.ConversationRepository
import com.synapse.data.repository.TypingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinator for all message sending operations.
 * Centralizes logic for sending messages with side-effects (typing indicators, lastMessageSentAt updates).
 * 
 * Singleton to maintain test message counter across ViewModel recreations.
 */
@Singleton
class MessageSendCoordinator @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val typingRepo: TypingRepository
) {
    // Global counter for batch test messages (persists across ViewModel lifecycle)
    private var testMessageCounter = 0
    
    companion object {
        private const val TAG = "MessageSendCoordinator"
    }
    
    /**
     * Send a single message with all side-effects:
     * - Remove typing indicator
     * - Send message to Firestore
     * - Update lastMessageSentAt timestamp
     */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        memberIds: List<String>
    ) {
        typingRepo.removeTyping(conversationId)
        conversationRepo.sendMessage(conversationId, text, memberIds)
        conversationRepo.updateMemberLastMessageSentAtNow(conversationId)
    }
    
    /**
     * Send multiple test messages in batch for performance testing.
     * Uses Firestore batch write for efficiency.
     * 
     * @param count Number of messages to send (20, 100, or 500)
     */
    suspend fun sendBatchMessages(
        conversationId: String,
        count: Int,
        memberIds: List<String>
    ) {
        Log.d(TAG, "Sending $count messages via batch...")
        
        val startNum = testMessageCounter + 1
        val messages = (startNum until startNum + count).map { i -> "Test message #$i" }
        
        conversationRepo.sendMessagesBatch(conversationId, messages, memberIds)
        
        testMessageCounter += count
        Log.d(TAG, "✅ Sent $count messages. Total counter: $testMessageCounter")
        
        // Update lastMessageSentAt after batch
        conversationRepo.updateMemberLastMessageSentAtNow(conversationId)
    }
    
    /**
     * Get current test message counter (for debugging/logging)
     */
    fun getTestMessageCounter(): Int = testMessageCounter
}

