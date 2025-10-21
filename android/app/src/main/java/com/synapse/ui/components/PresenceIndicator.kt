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
    if (isOnline) {
        Box(
            modifier = modifier
                .size(12.dp)
                .background(Color(0xFF4CAF50), CircleShape) // Green
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

