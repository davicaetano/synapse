package com.synapse.data.source.firestore

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.synapse.data.source.firestore.entity.UserEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource for accessing the 'users' Firestore collection.
 * 
 * RESPONSIBILITY: Raw CRUD operations on users only.
 * - No business logic
 * - No combining with presence data (that's in Realtime DB)
 * - Returns raw entities (not domain models)
 */
@Singleton
class FirestoreUserDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    // ============================================================
    // READ OPERATIONS
    // ============================================================
    
    /**
     * Listen to all users in real-time.
     * Returns raw Firestore data without presence information.
     */
    fun listenAllUsers(): Flow<List<UserEntity>> = callbackFlow {
        val ref = firestore.collection("users")
        
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to all users, sending empty list", error)
                trySend(emptyList())  // Keep flow alive, will update when error resolves
                return@addSnapshotListener
            }
            
            val users = snapshot?.documents?.mapNotNull { doc ->
                try {
                    UserEntity(
                        id = doc.id,
                        displayName = doc.getString("displayName"),
                        email = doc.getString("email"),
                        photoUrl = doc.getString("photoUrl"),
                        isSystemBot = doc.getBoolean("isSystemBot") ?: false
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user ${doc.id}", e)
                    null
                }
            } ?: emptyList()
            
            trySend(users)
        }
        
        awaitClose { registration.remove() }
    }
    
    /**
     * Listen to a single user by ID.
     * More efficient than listening to all users and filtering.
     */
    fun listenUser(userId: String): Flow<UserEntity?> = callbackFlow {
        val registration = firestore.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to user $userId", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val user = snapshot?.let { doc ->
                    try {
                        UserEntity(
                            id = doc.id,
                            displayName = doc.getString("displayName"),
                            email = doc.getString("email"),
                            photoUrl = doc.getString("photoUrl"),
                            isSystemBot = doc.getBoolean("isSystemBot") ?: false
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user ${doc.id}", e)
                        null
                    }
                }
                
                trySend(user)
            }
        
        awaitClose { registration.remove() }
    }
    
    /**
     * Listen to specific users by their IDs.
     * Useful for fetching only conversation members.
     * 
     * Note: Firestore whereIn() has a limit of 10 IDs per query.
     * If you need more than 10, you'll need to batch the queries.
     */
    fun listenUsersByIds(userIds: List<String>): Flow<List<UserEntity>> = callbackFlow {
        if (userIds.isEmpty()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        
        // Handle Firestore's 10 ID limit for whereIn queries
        val batches = userIds.chunked(10)
        val registrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        
        // Use StateFlow for thread-safe accumulation of results from multiple batches
        val usersState = MutableStateFlow<Map<String, UserEntity>>(emptyMap())
        
        batches.forEachIndexed { batchIndex, batch ->
            val ref = firestore.collection("users")
                .whereIn(FieldPath.documentId(), batch)
            
            val registration = ref.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to user batch $batchIndex", error)
                    // Don't update state on error - keep current data
                    return@addSnapshotListener
                }
                
                // Parse users from this batch
                val batchUsers = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        UserEntity(
                            id = doc.id,
                            displayName = doc.getString("displayName"),
                            email = doc.getString("email"),
                            photoUrl = doc.getString("photoUrl"),
                            isSystemBot = doc.getBoolean("isSystemBot") ?: false
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                
                // Thread-safe update: merge this batch into the state
                usersState.update { currentMap ->
                    currentMap + batchUsers.associateBy { it.id }
                }
            }
            
            registrations.add(registration)
        }
        
        // Collect from StateFlow and emit to the callbackFlow
        usersState
            .drop(1)
            .map { it.values.toList() }
            .distinctUntilChanged()
            .collect { users ->
                trySend(users)
            }
        
        awaitClose {
            registrations.forEach { it.remove() }
        }
    }
    
    // ============================================================
    // WRITE OPERATIONS
    // ============================================================
    
    /**
     * Create or update the current authenticated user's profile.
     * Uses Firebase Auth data as the source of truth.
     */
    suspend fun upsertCurrentUser() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Log.e(TAG, "Cannot upsert user: not authenticated")
            return
        }
        
        val userData = mapOf(
            "displayName" to (firebaseUser.displayName ?: firebaseUser.email ?: firebaseUser.uid),
            "email" to (firebaseUser.email ?: ""),
            "photoUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
            "updatedAtMs" to System.currentTimeMillis()
        )
        
        try {
            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(userData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "User ${firebaseUser.uid} upserted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting current user", e)
        }
    }
    
    /**
     * Update a user's profile information.
     * Only updates the fields that are provided (non-null).
     */
    suspend fun updateUserProfile(
        userId: String,
        displayName: String? = null,
        photoUrl: String? = null
    ) {
        val updates = mutableMapOf<String, Any>(
            "updatedAtMs" to System.currentTimeMillis()
        )
        
        if (displayName != null) {
            updates["displayName"] = displayName
        }
        
        if (photoUrl != null) {
            updates["photoUrl"] = photoUrl
        }
        
        try {
            firestore.collection("users")
                .document(userId)
                .update(updates)
                .await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user profile for $userId", e)
        }
    }
    
    companion object {
        private const val TAG = "FirestoreUserDS"
    }
}

