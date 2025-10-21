package com.synapse.ui.userpicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synapse.domain.user.User

@Composable
fun UserPickerScreen(
    onPick: (User) -> Unit,
    vm: UserPickerViewModel = hiltViewModel()
) {
    val users by vm.users.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(users) { u ->
                Text(text = u.displayName ?: u.id, modifier = Modifier.clickable { onPick(u) })
            }
        }
    }
}


