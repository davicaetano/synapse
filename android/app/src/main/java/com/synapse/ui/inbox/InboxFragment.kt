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
import com.synapse.data.presence.PresenceManager
import com.synapse.data.repository.UserRepository
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class InboxFragment : Fragment() {
    
    @Inject
    lateinit var presenceManager: PresenceManager
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private var presenceJob: Job? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
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
                                    findNavController().navigate(
                                        com.synapse.R.id.action_inbox_to_conversation,
                                        bundleOf("conversationId" to id)
                                    )
                                }
                            }
                        },
                        onOpenSettings = {
                            findNavController().navigate(
                                com.synapse.R.id.action_inbox_to_settings
                            )
                        }
                    )
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Mark user as online when entering inbox (after login)
        presenceManager.markOnline()
        
        // Force UserRepository to start observing presence (triggers reactive flow)
        presenceJob = CoroutineScope(Dispatchers.IO).launch {
            userRepository.observeUsersWithPresence().collect {
                // This collection forces the reactive flow to start
                // The actual data is used by ViewModels, not here
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Cancel presence observation
        presenceJob?.cancel()
    }
}

