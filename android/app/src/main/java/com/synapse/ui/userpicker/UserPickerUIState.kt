package com.synapse.ui.userpicker

import com.synapse.domain.user.User

// Representa diferentes tipos de itens que podem aparecer na lista do User Picker
sealed class UserPickerItem {
    data class UserItem(val user: User) : UserPickerItem()
    data object CreateGroupItem : UserPickerItem()
}
