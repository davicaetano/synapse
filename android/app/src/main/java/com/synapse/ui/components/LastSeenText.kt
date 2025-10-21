package com.synapse.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

@Composable
fun LastSeenText(
    lastSeenMs: Long?,
    modifier: Modifier = Modifier
) {
    if (lastSeenMs != null) {
        val text = formatLastSeen(lastSeenMs)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = modifier
        )
    }
}

private fun formatLastSeen(lastSeenMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - lastSeenMs
    
    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "Last seen just now"
        diffMs < TimeUnit.HOURS.toMillis(1) -> {
            val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            "Last seen ${mins}m ago"
        }
        diffMs < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
            "Last seen ${hours}h ago"
        }
        diffMs < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)
            "Last seen ${days}d ago"
        }
        else -> "Last seen long ago"
    }
}

