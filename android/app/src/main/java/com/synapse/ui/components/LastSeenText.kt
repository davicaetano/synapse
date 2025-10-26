package com.synapse.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun LastSeenText(
    lastSeenMs: Long?,
    modifier: Modifier = Modifier
) {
    if (lastSeenMs != null) {
        Text(
            text = "Last seen ${com.synapse.util.formatLastSeen(lastSeenMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = modifier
        )
    }
}

