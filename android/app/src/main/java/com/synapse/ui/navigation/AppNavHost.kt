package com.synapse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.synapse.data.auth.AuthState
import com.synapse.MainActivityViewModel
import com.synapse.ui.inbox.InboxScreen
import com.synapse.ui.conversation.ConversationScreen
import com.synapse.ui.auth.AuthScreen
import com.synapse.ui.userpicker.UserPickerScreen
import com.synapse.ui.creategroup.CreateGroupScreen

object Routes {
    const val Auth = "auth"
    const val Inbox = "inbox"
    const val Conversation = "conversation/{conversationId}"
    const val UserPicker = "userPicker"
    const val CreateGroup = "createGroup"
}

@Composable
fun AppNavHost(
    mainVm: MainActivityViewModel,
    startGoogleSignIn: () -> Unit,
    navController: NavHostController = rememberNavController()
) {

    val authState: AuthState by mainVm.authState.collectAsStateWithLifecycle()
    val start = if (authState is AuthState.SignedIn) Routes.Inbox else Routes.Auth
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.Auth) {
            AuthScreen(onSignIn = { startGoogleSignIn() })
        }
        composable(Routes.Inbox) {
            InboxScreen(onOpenConversation = { id ->
                when (id) {
                    "userPicker" -> navController.navigate(Routes.UserPicker)
                    "createGroup" -> navController.navigate(Routes.CreateGroup)
                    else -> navController.navigate("conversation/$id")
                }
            })
        }
        composable(Routes.UserPicker) {
            val pickerVm: com.synapse.ui.userpicker.UserPickerViewModel = hiltViewModel()
            val scope = rememberCoroutineScope()
            UserPickerScreen(
                onPickUser = { user ->
                    scope.launch {
                        val convId = pickerVm.createConversation(user)
                        if (convId != null) {
                            navController.popBackStack()
                            navController.navigate("conversation/$convId")
                        }
                    }
                },
                onCreateGroup = {
                    navController.popBackStack()  // Close UserPicker first
                    navController.navigate(Routes.CreateGroup)
                },
                onClose = { navController.popBackStack() }
            )
        }
        composable(Routes.Conversation) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ConversationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CreateGroup) {
            CreateGroupScreen(
                onGroupCreated = { conversationId ->
                    navController.popBackStack()
                    navController.navigate("conversation/$conversationId")
                },
                onClose = { navController.popBackStack() }
            )
        }
    }
}

