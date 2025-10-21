package com.synapse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Avatar for group conversations.
 * Shows group photo if available, or initials/icon as fallback.
 */
@Composable
fun GroupAvatar(
    groupName: String?,
    groupPhotoUrl: String? = null,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(generateGroupColor(groupName)),
        contentAlignment = Alignment.Center
    ) {
        if (groupPhotoUrl != null) {
            // TODO: Show group photo when implemented
            // For now, fall through to icon
        }
        
        // Always show group icon (clearer visual indicator)
        Icon(
            imageVector = Icons.Filled.Group,
            contentDescription = "Group",
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Generate a consistent color for a group name.
 * Same group name always gets the same color.
 */
private fun generateGroupColor(groupName: String?): Color {
    val colors = listOf(
        Color(0xFF1976D2), // Blue
        Color(0xFFD32F2F), // Red
        Color(0xFF388E3C), // Green
        Color(0xFFF57C00), // Orange
        Color(0xFF7B1FA2), // Purple
        Color(0xFF0097A7), // Cyan
        Color(0xFFC2185B), // Pink
        Color(0xFF5D4037), // Brown
    )
    
    val hash = groupName?.hashCode() ?: 0
    val index = Math.abs(hash) % colors.size
    return colors[index]
}

