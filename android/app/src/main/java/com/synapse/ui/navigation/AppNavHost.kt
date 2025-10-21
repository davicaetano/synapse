package com.synapse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synapse.auth.AuthState
import com.synapse.MainActivityViewModel
import com.synapse.ui.inbox.InboxScreen
import com.synapse.ui.conversation.ConversationScreen
import com.synapse.ui.auth.AuthScreen

object Routes {
    const val Auth = "auth"
    const val Inbox = "inbox"
    const val Conversation = "conversation/{conversationId}"
}

@Composable
fun AppNavHost(
    mainVm: MainActivityViewModel,
    navController: NavHostController = rememberNavController()
) {
    val authState: AuthState by mainVm.authState.collectAsStateWithLifecycle()
    val start = if (authState is AuthState.SignedIn) Routes.Inbox else Routes.Auth
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.Auth) {
            AuthScreen(onSignIn = { /* triggered by Activity button previously; keep simple */ })
        }
        composable(Routes.Inbox) {
            InboxScreen(onOpenConversation = { id ->
                navController.navigate("conversation/$id")
            })
        }
        composable(Routes.Conversation) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ConversationScreen(conversationId = conversationId)
        }
    }
}


