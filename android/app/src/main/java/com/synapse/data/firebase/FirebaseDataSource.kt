package com.synapse.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.synapse.domain.conversation.Conversation
import com.synapse.domain.conversation.ConversationSummary
import com.synapse.domain.conversation.ConversationType
import com.synapse.domain.conversation.Message
import com.synapse.domain.user.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.awaitClose
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
        val memberIds = listOf(myId, otherUserId).sorted()

        // Check if a conversation already exists for this pair (using memberIds)
        val existing = firestore.collection("conversations")
            .whereEqualTo("memberIds", memberIds)
            .limit(1)
            .get()
            .await()
        if (!existing.isEmpty) {
            return existing.documents.first().id
        }

        // Create with auto-generated ID to avoid collisions
        val data = mapOf(
            "memberIds" to memberIds,
            "convType" to ConversationType.DIRECT.name,
            "createdAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        val newDoc = firestore.collection("conversations").add(data).await()
        return newDoc.id
    }

    suspend fun createSelfConversation(): String? {
        val myId = auth.currentUser?.uid ?: return null

        // Check if self conversation already exists
        val existing = firestore.collection("conversations")
            .whereEqualTo("memberIds", listOf(myId))
            .whereEqualTo("convType", ConversationType.SELF.name)
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            return existing.documents.first().id
        }

        // Create self conversation
        val data = mapOf(
            "memberIds" to listOf(myId),
            "convType" to ConversationType.SELF.name,
            "createdAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        val newDoc = firestore.collection("conversations").add(data).await()
        return newDoc.id
    }

    suspend fun createGroupConversation(memberIds: List<String>): String? {
        if (memberIds.isEmpty()) return null

        val sortedMemberIds = memberIds.sorted()
        val groupName = "Group_${sortedMemberIds.joinToString("_")}"

        // Check if group conversation already exists
        val existing = firestore.collection("conversations")
            .whereEqualTo("memberIds", sortedMemberIds)
            .whereEqualTo("convType", ConversationType.GROUP.name)
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            return existing.documents.first().id
        }

        // Create group conversation
        val data = mapOf(
            "memberIds" to sortedMemberIds,
            "convType" to ConversationType.GROUP.name,
            "groupName" to groupName,
            "createdAtMs" to System.currentTimeMillis(),
            "updatedAtMs" to System.currentTimeMillis()
        )
        val newDoc = firestore.collection("conversations").add(data).await()
        return newDoc.id
    }

    suspend fun addUserToGroupConversation(conversationId: String, userId: String) {
        val convRef = firestore.collection("conversations").document(conversationId)

        // Add user to memberIds array
        convRef.update("memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            .await()

        // Update timestamp
        convRef.update("updatedAtMs", System.currentTimeMillis()).await()
    }

    suspend fun removeUserFromGroupConversation(conversationId: String, userId: String) {
        val convRef = firestore.collection("conversations").document(conversationId)

        // Remove user from memberIds array
        convRef.update("memberIds", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
            .await()

        // Update timestamp
        convRef.update("updatedAtMs", System.currentTimeMillis()).await()
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
                    val memberIds = (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                    // Buscar dados dos usuários para incluir no ConversationSummary
                    firestore.collection("users")
                        .whereIn("id", memberIds)
                        .get()
                        .addOnSuccessListener { usersSnapshot ->
                            val usersMap = usersSnapshot.documents.associate { userDoc ->
                                userDoc.id to User(
                                    id = userDoc.id,
                                    displayName = userDoc.getString("displayName"),
                                    photoUrl = userDoc.getString("photoUrl")
                                )
                            }

                            val members = memberIds.mapNotNull { userId -> usersMap[userId] }

                            val summary = ConversationSummary(
                                id = conversationId,
                                lastMessageText = d.getString("lastMessageText"),
                                updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                                members = members,
                                convType = try {
                                    ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                                } catch (e: IllegalArgumentException) {
                                    ConversationType.DIRECT // fallback para conversas existentes
                                }
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
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to load users for conversation", e)
                            // Fallback sem dados dos usuários
                            val summary = ConversationSummary(
                                id = conversationId,
                                lastMessageText = d.getString("lastMessageText"),
                                updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                                members = emptyList(),
                                convType = try {
                                    ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                                } catch (e: IllegalArgumentException) {
                                    ConversationType.DIRECT // fallback para conversas existentes
                                }
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
            }
        awaitClose { regSummary.remove(); regMsgs.remove() }
    }

    fun listenConversations(userId: String): Flow<List<ConversationSummary>> = callbackFlow {
        val ref = firestore.collection("conversations")
            .whereArrayContains("memberIds", userId)
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "listenConversations error", err)
                return@addSnapshotListener
            }

            val conversations = snap?.documents ?: emptyList()

            if (conversations.isEmpty()) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            // Coletar todos os memberIds únicos de todas as conversas
            val allMemberIds = conversations.flatMap { d ->
                (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            }.distinct()

            if (allMemberIds.isEmpty()) {
                val list = conversations.map { d ->
                    ConversationSummary(
                        id = d.id,
                        lastMessageText = d.getString("lastMessageText"),
                        updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                        members = emptyList(),
                        convType = try {
                            ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                        } catch (e: IllegalArgumentException) {
                            ConversationType.DIRECT
                        }
                    )
                }
                trySend(list)
                return@addSnapshotListener
            }

            // Buscar dados de todos os usuários necessários
            firestore.collection("users")
                .whereIn("id", allMemberIds)
                .get()
                .addOnSuccessListener { usersSnapshot ->
                    val usersMap = usersSnapshot.documents.associate { userDoc ->
                        userDoc.id to User(
                            id = userDoc.id,
                            displayName = userDoc.getString("displayName"),
                            photoUrl = userDoc.getString("photoUrl")
                        )
                    }

                    val list = conversations.map { d ->
                        val memberIds = (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        val members = memberIds.mapNotNull { userId -> usersMap[userId] }

                        ConversationSummary(
                            id = d.id,
                            lastMessageText = d.getString("lastMessageText"),
                            updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                            members = members,
                            convType = try {
                                ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                            } catch (e: IllegalArgumentException) {
                                ConversationType.DIRECT
                            }
                        )
                    }
                    trySend(list)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to load users for conversations", e)
                    // Fallback sem dados dos usuários
                    val list = conversations.map { d ->
                        ConversationSummary(
                            id = d.id,
                            lastMessageText = d.getString("lastMessageText"),
                            updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                            members = emptyList(),
                            convType = try {
                                ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                            } catch (e: IllegalArgumentException) {
                                ConversationType.DIRECT
                            }
                        )
                    }
                    trySend(list)
                }
        }
        awaitClose { reg.remove() }
    }

    fun listenConversationsByType(userId: String, convType: ConversationType): Flow<List<ConversationSummary>> = callbackFlow {
        val ref = firestore.collection("conversations")
            .whereArrayContains("memberIds", userId)
            .whereEqualTo("convType", convType.name)
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "listenConversationsByType error", err)
                return@addSnapshotListener
            }

            val conversations = snap?.documents ?: emptyList()

            if (conversations.isEmpty()) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            // Coletar todos os memberIds únicos de todas as conversas
            val allMemberIds = conversations.flatMap { d ->
                (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            }.distinct()

            if (allMemberIds.isEmpty()) {
                val list = conversations.map { d ->
                    ConversationSummary(
                        id = d.id,
                        lastMessageText = d.getString("lastMessageText"),
                        updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                        members = emptyList(),
                        convType = try {
                            ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                        } catch (e: IllegalArgumentException) {
                            ConversationType.DIRECT
                        }
                    )
                }
                trySend(list)
                return@addSnapshotListener
            }

            // Buscar dados de todos os usuários necessários
            firestore.collection("users")
                .whereIn("id", allMemberIds)
                .get()
                .addOnSuccessListener { usersSnapshot ->
                    val usersMap = usersSnapshot.documents.associate { userDoc ->
                        userDoc.id to User(
                            id = userDoc.id,
                            displayName = userDoc.getString("displayName"),
                            photoUrl = userDoc.getString("photoUrl")
                        )
                    }

                    val list = conversations.map { d ->
                        val memberIds = (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        val members = memberIds.mapNotNull { userId -> usersMap[userId] }

                        ConversationSummary(
                            id = d.id,
                            lastMessageText = d.getString("lastMessageText"),
                            updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                            members = members,
                            convType = try {
                                ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                            } catch (e: IllegalArgumentException) {
                                ConversationType.DIRECT
                            }
                        )
                    }
                    trySend(list)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to load users for conversations by type", e)
                    // Fallback sem dados dos usuários
                    val list = conversations.map { d ->
                        ConversationSummary(
                            id = d.id,
                            lastMessageText = d.getString("lastMessageText"),
                            updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
                            members = emptyList(),
                            convType = try {
                                ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
                            } catch (e: IllegalArgumentException) {
                                ConversationType.DIRECT
                            }
                        )
                    }
                    trySend(list)
                }
        }
        awaitClose { reg.remove() }
    }

    // Método auxiliar para converter dados do Firestore em ConversationSummary com dados completos dos usuários
    private fun createConversationSummaryFromDocument(d: com.google.firebase.firestore.DocumentSnapshot, usersMap: Map<String, User>): ConversationSummary {
        val memberIds = (d.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val members = memberIds.mapNotNull { userId -> usersMap[userId] }

        return ConversationSummary(
            id = d.id,
            lastMessageText = d.getString("lastMessageText"),
            updatedAtMs = d.getLong("updatedAtMs") ?: 0L,
            members = members,
            convType = try {
                ConversationType.valueOf(d.getString("convType") ?: ConversationType.DIRECT.name)
            } catch (e: IllegalArgumentException) {
                ConversationType.DIRECT // fallback para conversas existentes
            }
        )
    }

    // Método para combinar conversas com dados dos usuários
    fun listenConversationsWithUsers(userId: String): Flow<List<ConversationSummary>> {
        val conversationsFlow = listenConversations(userId)
        val usersFlow = listenUsers().map { users -> users.associateBy { it.id } }

        return combine(conversationsFlow, usersFlow) { conversations, usersMap ->
            conversations.map { conv ->
                val members = conv.memberIds.mapNotNull { userId -> usersMap[userId] }
                conv.copy(members = members)
            }
        }
    }

    // Método para combinar conversas filtradas por tipo com dados dos usuários
    fun listenConversationsWithUsersByType(userId: String, convType: ConversationType): Flow<List<ConversationSummary>> {
        val conversationsFlow = listenConversationsByType(userId, convType)
        val usersFlow = listenUsers().map { users -> users.associateBy { it.id } }

        return combine(conversationsFlow, usersFlow) { conversations, usersMap ->
            conversations.map { conv ->
                val members = conv.memberIds.mapNotNull { userId -> usersMap[userId] }
                conv.copy(members = members)
            }
        }
    }

    fun listenUsers(): Flow<List<User>> = callbackFlow {
        val ref = firestore.collection("users")
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) Log.e(TAG, "listenUsers error", err)
            val list = snap?.documents?.map { d ->
                User(
                    id = d.id,
                    displayName = d.getString("displayName"),
                    photoUrl = d.getString("photoUrl")
                )
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
            "photoUrl" to (u.photoUrl ?: ""),
            "updatedAtMs" to System.currentTimeMillis()
        )
        firestore.collection("users").document(u.uid)
            .set(data, SetOptions.merge())
            .await()
    }
    companion object { private const val TAG = "FirebaseDataSource" }
}


