package com.synapse.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SynapseTheme {
                    SettingsScreen(
                        onNavigateBack = {
                            findNavController().popBackStack()
                        },
                        onOpenDevSettings = {
                            findNavController().navigate(
                                com.synapse.R.id.action_settings_to_devSettings
                            )
                        }
                    )
                }
            }
        }
    }
}

