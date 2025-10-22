package com.synapse.ui.userpicker

import com.synapse.domain.user.User

sealed class UserPickerItem {
    data class UserItem(val user: User) : UserPickerItem()
    data object CreateGroupItem : UserPickerItem()
}
