package com.synapse.ui.userpicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserPickerFragment : Fragment() {
    private val viewModel: UserPickerViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val scope = rememberCoroutineScope()
                
                SynapseTheme {
                    UserPickerScreen(
                        onPickUser = { user ->
                            scope.launch {
                                val convId = viewModel.createConversation(user)
                                if (convId != null) {
                                    findNavController().navigate(
                                        com.synapse.R.id.action_userPicker_to_conversation,
                                        bundleOf("conversationId" to convId)
                                    )
                                }
                            }
                        },
                        onCreateGroup = {
                            findNavController().navigate(
                                com.synapse.R.id.action_userPicker_to_createGroup
                            )
                        },
                        onClose = {
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }
}

