package com.synapse.domain.conversation

/**
 * Message delivery status (WhatsApp-style).
 * 
 * Flow:
 * PENDING → SENT → DELIVERED → READ
 * 
 * Detection:
 * - PENDING: serverTimestamp is null (never reached server)
 * - SENT: serverTimestamp exists, but receivedBy is empty
 * - DELIVERED: receivedBy has members
 * - READ: readBy includes all other members
 */
enum class MessageStatus {
    /** ⏱️ Message hasn't reached the server yet (offline/pending) */
    PENDING,
    
    /** ✓ Single gray check - Message reached server */
    SENT,
    
    /** ✓✓ Double gray checks - Message delivered to recipient's device */
    DELIVERED,
    
    /** ✓✓ Double blue checks - Message read by all recipients */
    READ
}

