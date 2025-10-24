package com.synapse.ui.conversation.summarize

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                    SummarizeInputScreen(
                        onBack = { findNavController().navigateUp() },
                        onGenerate = { customInstructions ->
                            // Start generation (sets isGeneratingSummary = true in ViewModel)
                            viewModel.generateSummary(customInstructions)
                            
                            // Navigate back immediately - user will see spinner in ConversationScreen
                            findNavController().navigateUp()
                        }
                    )
                }
            }
        }
    }
}

