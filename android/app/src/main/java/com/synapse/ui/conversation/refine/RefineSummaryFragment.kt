package com.synapse.ui.conversation.refine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * RefineSummaryFragment
 * Fragment wrapper for RefineSummaryScreen (Compose)
 * 
 * Receives:
 * - conversationId: ID of the conversation
 * - previousSummaryId: ID of the summary message to refine
 */
@AndroidEntryPoint
class RefineSummaryFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            SynapseTheme {
                RefineSummaryScreen(
                    onNavigateBack = { findNavController().navigateUp() }
                )
            }
        }
    }
}

