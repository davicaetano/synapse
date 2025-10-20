package com.synapse.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.synapse.domain.conversation.ConversationSummary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
}


