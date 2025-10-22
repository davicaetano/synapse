package com.synapse.ui.inbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.fragment.findNavController
import com.synapse.MainActivityViewModel
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InboxFragment : Fragment() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("FRAGMENT_LIFECYCLE", "🟢 InboxFragment onCreate() - Fragment created")
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        android.util.Log.d("FRAGMENT_LIFECYCLE", "🎨 InboxFragment onCreateView() - Creating view")
        return ComposeView(requireContext()).apply {
            setContent {
                android.util.Log.d("FRAGMENT_LIFECYCLE", "🎨 InboxFragment setContent() - Composing UI")
                SynapseTheme {
                    InboxScreen(
                        onOpenConversation = { id ->
                            when (id) {
                                "userPicker" -> {
                                    findNavController().navigate(
                                        com.synapse.R.id.action_inbox_to_userPicker
                                    )
                                }
                                "createGroup" -> {
                                    findNavController().navigate(
                                        com.synapse.R.id.action_inbox_to_createGroup
                                    )
                                }
                                else -> {
                                    android.util.Log.d("FRAGMENT_LIFECYCLE", "➡️ InboxFragment navigating to conversation: $id")
                                    findNavController().navigate(
                                        com.synapse.R.id.action_inbox_to_conversation,
                                        bundleOf("conversationId" to id)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        android.util.Log.d("FRAGMENT_LIFECYCLE", "▶️ InboxFragment onStart()")
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("FRAGMENT_LIFECYCLE", "▶️▶️ InboxFragment onResume()")
    }
    
    override fun onPause() {
        super.onPause()
        android.util.Log.d("FRAGMENT_LIFECYCLE", "⏸️ InboxFragment onPause()")
    }
    
    override fun onStop() {
        super.onStop()
        android.util.Log.d("FRAGMENT_LIFECYCLE", "⏹️ InboxFragment onStop()")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        android.util.Log.d("FRAGMENT_LIFECYCLE", "🔴 InboxFragment onDestroyView() - View destroyed")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("FRAGMENT_LIFECYCLE", "💀 InboxFragment onDestroy() - Fragment destroyed")
    }
}

