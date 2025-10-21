package com.synapse.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.util.Log
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
        // NOTE: ordering by updatedAtMs can require a composite index; avoid for MVP
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "listenConversations snapshot error", err)
            }
            val list = snap?.documents?.map { d ->
                ConversationSummary(
                    id = d.id,
                    title = d.getString("title"),
                    lastMessageText = d.getString("lastMessageText"),
                    updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                    memberIds = (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
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

    suspend fun getOrCreateDirectConversation(otherUserId: String): String? {
        val myId = auth.currentUser?.uid ?: return null
        val key = listOf(myId, otherUserId).sorted().joinToString("_")
        // Check if a conversation already exists for this pair
        val existing = firestore.collection("conversations")
            .whereEqualTo("participantsKey", key)
            .limit(1)
            .get()
            .await()
        if (!existing.isEmpty) {
            return existing.documents.first().id
        }
        // Create with auto-generated ID to avoid collisions
        val data = mapOf(
            "participantsKey" to key,
            "memberIds" to listOf(myId, otherUserId),
            "createdAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        val newDoc = firestore.collection("conversations").add(data).await()
        return newDoc.id
    }

    companion object { private const val TAG = "ConversationRepository" }
}


