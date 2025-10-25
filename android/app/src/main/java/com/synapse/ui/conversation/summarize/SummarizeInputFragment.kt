package com.synapse.ui.conversation.summarize

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment for AI summarization input screen
 * 
 * Allows user to enter custom instructions or generate default summary
 * Has its own simple ViewModel that just calls AIRepository
 */
@AndroidEntryPoint
class SummarizeInputFragment : Fragment() {
    
    // Get ViewModel with conversationId from arguments
    private val viewModel: SummarizeInputViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SynapseTheme {
                    // Collect dev setting: should Force Error option be shown?
                    val forceAIErrorEnabled by viewModel.forceAIError.collectAsState()
                    
                    SummarizeInputScreen(
                        onBack = { findNavController().navigateUp() },
                        onGenerate = { mode, customInstructions ->
                            // Start AI analysis based on selected mode
                            viewModel.generateAIAnalysis(mode, customInstructions)
                            
                            // Navigate back immediately - user will see spinner in ConversationScreen
                            findNavController().navigateUp()
                        },
                        forceAIErrorEnabled = forceAIErrorEnabled
                    )
                }
            }
        }
    }
}

