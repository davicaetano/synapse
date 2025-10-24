package com.synapse.ui.devsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.data.local.DevPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevSettingsViewModel @Inject constructor(
    private val devPreferences: DevPreferences
) : ViewModel() {
    
    val uiState: StateFlow<DevSettingsUIState> = combine(
        devPreferences.urlMode,
        devPreferences.customUrl,
        devPreferences.showBatchButtons
    ) { urlMode, customUrl, showBatchButtons ->
        Triple(urlMode, customUrl, showBatchButtons)
    }.combine(
        combine(
            devPreferences.forceAIError,
            devPreferences.showAIErrorToasts,
            devPreferences.showAIProcessingTime
        ) { forceAIError, showAIErrorToasts, showAIProcessingTime ->
            Triple(forceAIError, showAIErrorToasts, showAIProcessingTime)
        }
    ) { first, second ->
        DevSettingsUIState(
            urlMode = first.first,
            customUrl = first.second,
            showBatchButtons = first.third,
            forceAIError = second.first,
            showAIErrorToasts = second.second,
            showAIProcessingTime = second.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevSettingsUIState()
    )
    
    fun setUrlMode(mode: String) {
        viewModelScope.launch {
            devPreferences.setUrlMode(mode)
        }
    }
    
    fun setCustomUrl(url: String) {
        viewModelScope.launch {
            devPreferences.setCustomUrl(url)
        }
    }
    
    fun toggleBatchButtons(show: Boolean) {
        viewModelScope.launch {
            devPreferences.setShowBatchButtons(show)
        }
    }
    
    fun toggleForceAIError(force: Boolean) {
        viewModelScope.launch {
            devPreferences.setForceAIError(force)
        }
    }
    
    fun toggleShowAIErrorToasts(show: Boolean) {
        viewModelScope.launch {
            devPreferences.setShowAIErrorToasts(show)
        }
    }
    
    fun toggleShowAIProcessingTime(show: Boolean) {
        viewModelScope.launch {
            devPreferences.setShowAIProcessingTime(show)
        }
    }
}

