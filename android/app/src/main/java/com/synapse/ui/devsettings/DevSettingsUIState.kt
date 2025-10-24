package com.synapse.ui.devsettings

data class DevSettingsUIState(
    val urlMode: String = "production",
    val customUrl: String = "",
    val showBatchButtons: Boolean = false,
    val forceAIError: Boolean = false,
    val showAIErrorToasts: Boolean = false
)

