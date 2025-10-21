package com.synapse.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.Message
import com.synapse.domain.user.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseDataSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    suspend fun sendMessage(conversationId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val msg = mapOf(
            "text" to text,
            "senderId" to uid,
            "createdAtMs" to System.currentTimeMillis()
        )
        val convRef = firestore.collection("conversations").document(conversationId)
        convRef.collection("messages").add(msg).await()
        convRef.set(
            mapOf(
                "updatedAtMs" to System.currentTimeMillis(),
                "lastMessageText" to text
            ),
            SetOptions.merge()
        ).await()
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

    fun listenConversation(conversationId: String): Flow<Conversation> = callbackFlow {
        // Listen summary
        val convRef = firestore.collection("conversations").document(conversationId)
        val regSummary = convRef.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "listenConversation summary error", err)
            }
            // We will emit when also messages arrive; so do nothing here directly
        }
        // Listen messages
        val myId = auth.currentUser?.uid
        val regMsgs = convRef.collection("messages").orderBy("createdAtMs")
            .addSnapshotListener { msgsSnap, msgsErr ->
                if (msgsErr != null) {
                    Log.e(TAG, "listenConversation messages error", msgsErr)
                }
                // Read current summary snapshot
                convRef.get().addOnSuccessListener { d ->
                    val summary = ConversationSummary(
                        id = conversationId,
                        lastMessageText = d.getString("lastMessageText"),
                        updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                        memberIds = (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    )
                    val messages = msgsSnap?.documents?.mapNotNull { doc ->
                        val text = doc.getString("text") ?: return@mapNotNull null
                        val senderId = doc.getString("senderId") ?: return@mapNotNull null
                        val createdAt = doc.getLong("createdAtMs") ?: 0L
                        Message(
                            id = doc.id,
                            text = text,
                            senderId = senderId,
                            createdAtMs = createdAt,
                            isMine = (myId != null && senderId == myId)
                        )
                    } ?: emptyList()
                    trySend(Conversation(summary = summary, messages = messages))
                }
            }
        awaitClose { regSummary.remove(); regMsgs.remove() }
    }

    fun listenConversations(userId: String): Flow<List<ConversationSummary>> = callbackFlow {
        val ref = firestore.collection("conversations")
            .whereArrayContains("memberIds", userId)
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) Log.e(TAG, "listenConversations error", err)
            val list = snap?.documents?.map { d ->
                ConversationSummary(
                    id = d.id,
                    lastMessageText = d.getString("lastMessageText"),
                    updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                    memberIds = (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                )
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun listenUsers(): Flow<List<User>> = callbackFlow {
        val ref = firestore.collection("users")
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) Log.e(TAG, "listenUsers error", err)
            val list = snap?.documents?.map { d ->
                User(id = d.id, displayName = d.getString("displayName"))
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun upsertCurrentUser() {
        val u = auth.currentUser ?: return
        val data = hashMapOf<String, Any>(
            "displayName" to (u.displayName ?: (u.email ?: u.uid)),
            "email" to (u.email ?: ""),
            "updatedAtMs" to System.currentTimeMillis()
        )
        firestore.collection("users").document(u.uid)
            .set(data, SetOptions.merge())
            .await()
    }
    companion object { private const val TAG = "FirebaseDataSource" }
}


