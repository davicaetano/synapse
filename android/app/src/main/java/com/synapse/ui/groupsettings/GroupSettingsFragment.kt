package com.synapse.ui.groupsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupSettingsFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SynapseTheme {
                    GroupSettingsScreen(
                        onNavigateBack = {
                            findNavController().popBackStack()
                        },
                        onAddMember = {
                            // Navigate to UserPicker to select member to add
                            findNavController().navigate(
                                com.synapse.R.id.action_groupSettings_to_userPicker
                            )
                        }
                    )
                }
            }
        }
    }
}

