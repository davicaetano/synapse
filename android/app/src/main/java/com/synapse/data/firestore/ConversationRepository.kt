package com.synapse.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.synapse.domain.conversation.ConversationSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun listenConversations(): Flow<List<ConversationSummary>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = firestore.collection("conversations")
            .whereArrayContains("memberIds", uid)
            .orderBy("updatedAtMs", Query.Direction.DESCENDING)
        val reg = ref.addSnapshotListener { snap, _ ->
            val list = snap?.documents?.map { d ->
                ConversationSummary(
                    id = d.id,
                    title = d.getString("title"),
                    lastMessageText = d.getString("lastMessageText"),
                    updatedAtMs = d.getLong("updatedAtMs") ?: 0L
                )
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun ensureConversation(conversationId: String, title: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "updatedAtMs" to System.currentTimeMillis(),
            "memberIds" to FieldValue.arrayUnion(uid)
        )
        if (title != null) data["title"] = title
        firestore.collection("conversations").document(conversationId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun createDirectConversation(otherUserId: String): String? {
        val myId = auth.currentUser?.uid ?: return null
        val convId = listOf(myId, otherUserId).sorted().joinToString("_")
        firestore.collection("conversations").document(convId)
            .set(
                mapOf(
                    "memberIds" to listOf(myId, otherUserId),
                    "updatedAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        return convId
    }
}


