package com.synapse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synapse.auth.AuthState
import com.synapse.MainActivityViewModel
import com.synapse.ui.inbox.InboxScreen
import com.synapse.ui.conversation.ConversationScreen

object Routes {
    const val Inbox = "inbox"
    const val Conversation = "conversation/{conversationId}"
}

@Composable
fun AppNavHost(
    mainVm: MainActivityViewModel,
    navController: NavHostController = rememberNavController()
) {
    val start = if (mainVm.currentAuthState() is AuthState.SignedIn) Routes.Inbox else Routes.Inbox
    NavHost(navController = navController, startDestination = start) {
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


