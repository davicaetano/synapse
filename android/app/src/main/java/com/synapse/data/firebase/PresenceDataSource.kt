package com.synapse.data.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.synapse.domain.user.UserPresence
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceDataSource @Inject constructor(
    private val realtimeDb: DatabaseReference
) {

    // Listen to single user presence
    fun listenUserPresence(userId: String): Flow<UserPresence> = callbackFlow {
        val ref = realtimeDb.child("presence").child(userId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeenMs = snapshot.child("lastSeenMs").getValue(Long::class.java)

                trySend(UserPresence(online = online, lastSeenMs = lastSeenMs))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Presence listener cancelled for $userId", error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // Listen to multiple users presence (for inbox)
    fun listenMultiplePresence(userIds: List<String>): Flow<Map<String, UserPresence>> =
        callbackFlow {
            if (userIds.isEmpty()) {
                trySend(emptyMap())
                awaitClose {}
                return@callbackFlow
            }

            val listeners = mutableMapOf<String, ValueEventListener>()
            val presenceMap = mutableMapOf<String, UserPresence>()

            userIds.forEach { userId ->
                val ref = realtimeDb.child("presence").child(userId)

                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                        val lastSeenMs = snapshot.child("lastSeenMs").getValue(Long::class.java)

                        presenceMap[userId] = UserPresence(online = online, lastSeenMs = lastSeenMs)
                        trySend(presenceMap.toMap())
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Presence listener cancelled for $userId", error.toException())
                    }
                }

                listeners[userId] = listener
                ref.addValueEventListener(listener)
            }

            awaitClose {
                listeners.forEach { (userId, listener) ->
                    realtimeDb.child("presence").child(userId).removeEventListener(listener)
                }
            }
        }

    companion object {
        private const val TAG = "PresenceDataSource"
    }
}