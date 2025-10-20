package com.synapse.notifications

object NotificationIdProvider {
    fun messageIdForChat(chatId: String?): Int {
        if (chatId.isNullOrBlank()) return 1001
        return 1000 + (chatId.hashCode() and 0x7fffffff) % 900000
    }
}


