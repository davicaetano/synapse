package com.synapse.data.source.room

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.synapse.data.source.IMessageDataSource
import com.synapse.data.source.firestore.FirestoreMessageDataSource
import com.synapse.data.source.firestore.entity.MessageEntity
import com.synapse.data.source.room.dao.MessageDao
import com.synapse.data.source.room.entity.MessageRoomEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message DataSource that uses Room as cache + Firebase as remote.
 * 
 * STRATEGY:
 * - All READS come from Room (instant, ~50ms for 2500 messages)
 * - All WRITES go to Firebase (Room syncs automatically via listeners)
 * - Single global sync job keeps Room updated from Firebase
 * 
 * This provides instant UI updates while keeping data in sync with Firebase.
 */
@Singleton
class RoomMessageDataSource @Inject constructor(
    private val messageDao: MessageDao,
    private val firestoreDataSource: FirestoreMessageDataSource
) : IMessageDataSource {
    
    // Singleton scope for background sync (survives ViewModel lifecycle)
    private val dataSourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Track active sync jobs per conversation (prevents duplicates)
    private val activeSyncJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    /**
     * Ensure sync is running for a specific conversation.
     */
    private fun ensureConversationSync(conversationId: String) {
        if (activeSyncJobs.containsKey(conversationId)) return
        
        val syncJob = dataSourceScope.launch {
            firestoreDataSource.listenMessages(conversationId).collect { firestoreMessages ->
                val roomMessages = firestoreMessages.map { msg ->
                    MessageRoomEntity.fromEntity(msg, conversationId)
                }
                messageDao.upsertMessages(roomMessages)
                Log.d(TAG, "✅ [ROOM] Synced ${roomMessages.size} messages for $conversationId")
            }
        }
        
        activeSyncJobs[conversationId] = syncJob
    }
    
    /**
     * Listen to messages with Room as single source of truth.
     * 
     * FLOW:
     * 1. Emit from Room IMMEDIATELY (instant UI, may be empty or cached)
     * 2. Launch background job: Firebase → Room (continuous sync)
     */
    override fun listenMessages(conversationId: String): Flow<List<MessageEntity>> = callbackFlow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ [ROOM] listenMessages START: $conversationId")
        
        // Launch 1: Room → UI (FIRST! Instant emission!)
        var firstEmission = true
        val emitJob = launch {
            messageDao.observeMessages(conversationId)
                .distinctUntilChanged()  // ✅ Only emit when data actually changes!
                .collect { roomMessages ->
                    val entities = roomMessages.map { it.toEntity() }
                    
                    if (firstEmission) {
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(TAG, "⏱️ [ROOM] listenMessages FIRST EMIT: $conversationId - ${entities.size} msgs in ${elapsed}ms (from cache)")
                        firstEmission = false
                    }
                    
                    send(entities)
                }
        }
        
        // Launch 2: Firebase → Room sync (background, doesn't block UI!)
        val syncJob = launch {
            firestoreDataSource.listenMessages(conversationId).collect { firestoreMessages ->
                val roomMessages = firestoreMessages.map { msg ->
                    MessageRoomEntity.fromEntity(msg, conversationId)
                }
                messageDao.upsertMessages(roomMessages)
                Log.d(TAG, "✅ [ROOM] Synced ${roomMessages.size} messages from Firebase to Room")
            }
        }
        
        awaitClose {
            syncJob.cancel()
            emitJob.cancel()
            Log.d(TAG, "🔌 [ROOM] listenMessages CLOSED: $conversationId")
        }
    }
    
    /**
     * Observe messages with Paging3 support (efficient for large lists).
     * 
     * BENEFITS:
     * - Loads messages in chunks (50 at a time)
     * - Scroll up → automatically loads more
     * - UI always responsive (never loads all 2500 at once!)
     * 
     * NOTE: Does NOT automatically start Firebase sync.
     * Call startMessageSync() separately after UI is ready to avoid blocking.
     */
    fun observeMessagesPaged(conversationId: String): Flow<PagingData<MessageEntity>> {
        Log.d(TAG, "📄 [ROOM] observeMessagesPaged START: $conversationId")
        
        // Return Pager with Room as data source
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                prefetchDistance = 15,
                enablePlaceholders = false,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { messageDao.observeMessagesPaged(conversationId) }
        ).flow.map { pagingData ->
            pagingData.map { roomEntity -> roomEntity.toEntity() }
        }
    }
    
    /**
     * Start Firebase → Room sync for a conversation.
     * Should be called AFTER the UI has rendered to avoid blocking initial load.
     */
    fun startMessageSync(conversationId: String) {
        Log.d(TAG, "🔄 [ROOM] Starting message sync for: $conversationId")
        ensureConversationSync(conversationId)
    }
    
    /**
     * Send a message (writes to Firebase, syncs to Room automatically).
     */
    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        memberIds: List<String>
    ): String? {
        // Delegate to Firestore - Room will sync automatically via listener
        return firestoreDataSource.sendMessage(conversationId, text, memberIds)
    }
    
    /**
     * Send multiple messages in batch (writes to Firebase, syncs to Room automatically).
     */
    override suspend fun sendMessagesBatch(
        conversationId: String,
        messages: List<String>,
        memberIds: List<String>
    ) {
        // Delegate to Firestore - Room will sync automatically via listener
        firestoreDataSource.sendMessagesBatch(conversationId, messages, memberIds)
    }
    
    /**
     * Observe unread messages (delegates to Firestore for now).
     * TODO: Could optimize with Room query later.
     */
    override fun observeUnreadMessages(conversationId: String): Flow<List<String>> {
        return firestoreDataSource.observeUnreadMessages(conversationId)
    }
    
    /**
     * Mark messages as read (writes to Firebase, syncs to Room automatically).
     */
    override suspend fun markMessagesAsRead(conversationId: String, messageIds: List<String>) {
        firestoreDataSource.markMessagesAsRead(conversationId, messageIds)
    }
    
    /**
     * Observe all unread counts.
     * 
     * HYBRID STRATEGY:
     * - Syncs from Firebase to populate Room
     * - Returns Flow from Room (instant reads!)
     */
    override fun observeAllUnreadCounts(userId: String): Flow<Map<String, Int>> = callbackFlow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ [ROOM] observeAllUnreadCounts START")
        
        // Launch 1: Firebase → Room sync (for ALL conversations)
        val syncJob = launch {
            firestoreDataSource.observeAllUnreadCounts(userId).collect { firestoreCounts ->
                // Ensure sync is running for each conversation that has unread messages
                firestoreCounts.keys.forEach { convId ->
                    ensureConversationSync(convId)
                }
            }
        }
        
        // Launch 2: Room → UI (instant!)
        var firstEmission = true
        val emitJob = launch {
            messageDao.getUnreadMessagesByUser(userId).collect { unreadMessages ->
                val countsByConv = unreadMessages
                    .groupBy { it.conversationId }
                    .mapValues { (_, messages) -> messages.size }
                
                if (firstEmission) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⏱️ [ROOM] observeAllUnreadCounts FIRST EMIT: ${countsByConv.size} convs, ${countsByConv.values.sum()} total unread in ${elapsed}ms")
                    firstEmission = false
                }
                
                send(countsByConv)
            }
        }
        
        awaitClose {
            syncJob.cancel()
            emitJob.cancel()
        }
    }
    
    /**
     * Observe all unreceived messages.
     * 
     * HYBRID STRATEGY:
     * - Syncs from Firebase to populate Room
     * - Returns Flow from Room (instant reads!)
     */
    override fun observeAllUnreceivedMessages(userId: String): Flow<Map<String, List<String>>> = callbackFlow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏱️ [ROOM] observeAllUnreceivedMessages START")
        
        // Launch 1: Firebase → Room sync (for ALL conversations)
        val syncJob = launch {
            firestoreDataSource.observeAllUnreceivedMessages(userId).collect { firestoreMessages ->
                // Ensure sync is running for each conversation that has unreceived messages
                firestoreMessages.keys.forEach { convId ->
                    ensureConversationSync(convId)
                }
            }
        }
        
        // Launch 2: Room → UI (instant!)
        var firstEmission = true
        val emitJob = launch {
            messageDao.getUnreceivedMessagesByUser(userId).collect { unreceivedMessages ->
                val messagesByConv = unreceivedMessages
                    .groupBy { it.conversationId }
                    .mapValues { (_, messages) -> messages.map { it.id } }
                
                if (firstEmission) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⏱️ [ROOM] observeAllUnreceivedMessages FIRST EMIT: ${messagesByConv.size} convs, ${messagesByConv.values.sumOf { it.size }} msgs in ${elapsed}ms")
                    firstEmission = false
                }
                
                send(messagesByConv)
            }
        }
        
        awaitClose {
            syncJob.cancel()
            emitJob.cancel()
        }
    }
    
    /**
     * Mark messages as received (writes to Firebase, syncs to Room automatically).
     */
    override suspend fun markMessagesAsReceived(conversationId: String, messageIds: List<String>) {
        firestoreDataSource.markMessagesAsReceived(conversationId, messageIds)
    }
    
    companion object {
        private const val TAG = "RoomMessageDS"
    }
}

