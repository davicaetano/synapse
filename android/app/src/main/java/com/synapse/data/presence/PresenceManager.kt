package com.synapse.data.presence

import com.synapse.data.source.realtime.RealtimePresenceDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for user presence operations.
 * 
 * DEPRECATED: This is a temporary adapter to maintain compatibility.
 * New code should use RealtimePresenceDataSource directly.
 * 
 * This will be removed in Phase 2 of the refactoring when we update MainActivity.
 */
@Singleton
class PresenceManager @Inject constructor(
    private val presenceDataSource: RealtimePresenceDataSource
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun markOnline() {
        scope.launch {
            presenceDataSource.markOnline()
        }
    }
    
    fun markOffline() {
        scope.launch {
            presenceDataSource.markOffline()
        }
    }
}

