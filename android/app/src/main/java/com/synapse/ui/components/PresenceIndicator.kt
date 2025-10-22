package com.synapse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PresenceIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isOnline) {
        Color(0xFF4CAF50) // Green when online
    } else {
        Color(0xFFE53935) // Red when offline
    }
    
    Box(
        modifier = modifier
            .size(12.dp)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape)
    )
}

