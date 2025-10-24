package com.synapse.ui.conversation.refine

/**
 * UI state for Refine Summary screen
 * 
 * @param isLoading Whether initial data (previous summary) is loading
 * @param previousSummaryText The text of the summary being refined
 */
data class RefineSummaryUIState(
    val isLoading: Boolean = true,
    val previousSummaryText: String? = null
)

