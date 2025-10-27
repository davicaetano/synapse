package com.synapse.ui.conversation

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationFragment : Fragment() {
    
    private val viewModel: ConversationViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // NOTE: conversationId argument is automatically injected into ViewModel
        // via SavedStateHandle by Navigation Component.
        // 
        // Flow: InboxFragment passes bundleOf("conversationId" to id)
        //       → Navigation Component puts it in SavedStateHandle
        //       → ConversationViewModel reads from savedStateHandle.get("conversationId")
        //
        // This is the recommended pattern for Fragment Navigation + Hilt ViewModels.
        return ComposeView(requireContext()).apply {
            setContent {
                SynapseTheme {
                    ConversationScreen(
                        onNavigateBack = {
                            findNavController().popBackStack()
                        },
                        onOpenGroupSettings = {
                            val conversationId = arguments?.getString("conversationId") ?: ""
                            findNavController().navigate(
                                com.synapse.R.id.action_conversation_to_groupSettings,
                                bundleOf("conversationId" to conversationId)
                            )
                        },
                        onOpenMessageDetail = { messageId ->
                            val conversationId = arguments?.getString("conversationId") ?: ""
                            findNavController().navigate(
                                com.synapse.R.id.action_conversation_to_messageDetail,
                                bundleOf(
                                    "conversationId" to conversationId,
                                    "messageId" to messageId
                                )
                            )
                        },
                        onOpenRefineSummary = { previousSummaryId ->
                            val conversationId = arguments?.getString("conversationId") ?: ""
                            findNavController().navigate(
                                com.synapse.R.id.action_conversation_to_refineSummary,
                                bundleOf(
                                    "conversationId" to conversationId,
                                    "previousSummaryId" to previousSummaryId
                                )
                            )
                        },
                        onOpenSummarizeInput = {
                            val conversationId = arguments?.getString("conversationId") ?: ""
                            findNavController().navigate(
                                com.synapse.R.id.action_conversation_to_summarizeInput,
                                bundleOf("conversationId" to conversationId)
                            )
                        }
                    )
                }
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // Remove typing indicator when user leaves the conversation screen
        // This prevents typing indicator from staying active forever if user leaves before timeout
        lifecycleScope.launch {
            viewModel.stopTyping()
        }
        lifecycleScope.launch {
            viewModel.setLatSeen()
        }

    }
}

