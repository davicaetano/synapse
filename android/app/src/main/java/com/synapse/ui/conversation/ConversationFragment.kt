package com.synapse.ui.conversation

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ConversationFragment : Fragment() {
    
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
        Log.v("DAVIDAVIDAVI", "ConversationFragment")
        return ComposeView(requireContext()).apply {
            setContent {
                SynapseTheme {
                    ConversationScreen(
                        onNavigateBack = {
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }
}

