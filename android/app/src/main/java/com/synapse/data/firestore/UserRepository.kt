package com.synapse.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.synapse.domain.user.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun listenUsers(): Flow<List<User>> = callbackFlow {
        val uid = auth.currentUser?.uid
        val ref = firestore.collection("users")
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "listenUsers snapshot error", err)
            }
            val list = snap?.documents?.map { d ->
                User(id = d.id, displayName = d.getString("displayName"))
            }
//                ?.filter { it.id != uid }
                ?: emptyList()
            Log.d(TAG, "listenUsers found ${list.size} users (excluding self)")
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
        firestore.collection("users").document(u.uid).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        Log.d(TAG, "upsertCurrentUser saved user ${u.uid}")
    }

    companion object { private const val TAG = "UserRepository" }
}


