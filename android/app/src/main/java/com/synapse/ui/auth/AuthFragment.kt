package com.synapse.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.synapse.MainActivityViewModel
import com.synapse.ui.theme.SynapseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthFragment : Fragment() {
    private val mainVm: MainActivityViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SynapseTheme {
                    AuthScreen(
                        onSignIn = {
                            // Trigger Google Sign In from MainActivity
                            (requireActivity() as? com.synapse.MainActivity)?.triggerGoogleSignIn()
                        }
                    )
                }
            }
        }
    }
}

