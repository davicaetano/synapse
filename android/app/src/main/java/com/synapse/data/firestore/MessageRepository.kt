package com.synapse.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.synapse.domain.conversation.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun listenMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        val ref = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAtMs", Query.Direction.ASCENDING)
        val reg = ref.addSnapshotListener { snap, _ ->
            val msgs = snap?.documents?.mapNotNull { doc ->
                val text = doc.getString("text") ?: return@mapNotNull null
                val senderId = doc.getString("senderId") ?: return@mapNotNull null
                val createdAt = doc.getLong("createdAtMs") ?: 0L
                Message(id = doc.id, text = text, senderId = senderId, createdAtMs = createdAt)
            } ?: emptyList()
            trySend(msgs)
        }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(conversationId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val msg = mapOf(
            "text" to text,
            "senderId" to uid,
            "createdAtMs" to System.currentTimeMillis()
        )
        val convRef = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
        convRef.add(msg).await()
        // upsert conversation summary
        firestore.collection("conversations").document(conversationId)
            .set(
                mapOf(
                    "updatedAtMs" to System.currentTimeMillis(),
                    "lastMessageText" to text
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }
}


