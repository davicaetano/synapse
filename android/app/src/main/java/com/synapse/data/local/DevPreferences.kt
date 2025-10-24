package com.synapse.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.devDataStore: DataStore<Preferences> by preferencesDataStore(name = "dev_settings")

/**
 * Dev configuration preferences using DataStore
 * Stores API base URL and dev feature flags
 */
@Singleton
class DevPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.devDataStore
    
    companion object {
        // URL modes
        const val URL_MODE_PRODUCTION = "production"
        const val URL_MODE_LOCAL = "local"
        const val URL_MODE_CUSTOM = "custom"
        
        // Keys
        private val URL_MODE = stringPreferencesKey("url_mode")
        private val CUSTOM_URL = stringPreferencesKey("custom_url")
        private val SHOW_BATCH_BUTTONS = booleanPreferencesKey("show_batch_buttons")
        private val FORCE_AI_ERROR = booleanPreferencesKey("force_ai_error")
        private val SHOW_AI_ERROR_TOASTS = booleanPreferencesKey("show_ai_error_toasts")
        
        // Default URLs
        const val PRODUCTION_URL = "https://synapse-ai-api-4wbf.onrender.com/api/"
        const val LOCAL_URL = "http://192.168.1.156:8000/api/"
    }
    
    /**
     * Current URL mode flow
     */
    val urlMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[URL_MODE] ?: URL_MODE_PRODUCTION
    }
    
    /**
     * Custom URL flow
     */
    val customUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[CUSTOM_URL] ?: ""
    }
    
    /**
     * Show batch message buttons flag
     */
    val showBatchButtons: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_BATCH_BUTTONS] ?: false
    }
    
    /**
     * Force AI error flag (for testing error handling)
     */
    val forceAIError: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FORCE_AI_ERROR] ?: false
    }
    
    /**
     * Show AI error toasts flag (for debugging)
     */
    val showAIErrorToasts: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_AI_ERROR_TOASTS] ?: false
    }
    
    /**
     * Get current base URL based on selected mode
     */
    val baseUrl: Flow<String> = dataStore.data.map { prefs ->
        when (prefs[URL_MODE] ?: URL_MODE_PRODUCTION) {
            URL_MODE_PRODUCTION -> PRODUCTION_URL
            URL_MODE_LOCAL -> LOCAL_URL
            URL_MODE_CUSTOM -> prefs[CUSTOM_URL] ?: PRODUCTION_URL
            else -> PRODUCTION_URL
        }
    }
    
    /**
     * Set URL mode
     */
    suspend fun setUrlMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[URL_MODE] = mode
        }
    }
    
    /**
     * Set custom URL
     */
    suspend fun setCustomUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[CUSTOM_URL] = url
        }
    }
    
    /**
     * Toggle batch message buttons
     */
    suspend fun setShowBatchButtons(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_BATCH_BUTTONS] = show
        }
    }
    
    /**
     * Toggle force AI error (for testing)
     */
    suspend fun setForceAIError(force: Boolean) {
        dataStore.edit { prefs ->
            prefs[FORCE_AI_ERROR] = force
        }
    }
    
    /**
     * Toggle show AI error toasts (for debugging)
     */
    suspend fun setShowAIErrorToasts(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_AI_ERROR_TOASTS] = show
        }
    }
}

