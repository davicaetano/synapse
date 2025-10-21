package com.synapse.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Reusable user avatar component.
 * Shows profile picture if available, or a default person icon.
 * Optionally shows presence indicator (green dot for online).
 */
@Composable
fun UserAvatar(
    photoUrl: String?,
    displayName: String?,
    size: Dp = 48.dp,
    showPresence: Boolean = false,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile picture of ${displayName ?: "user"}",
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Default avatar with person icon
            Surface(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Default profile picture",
                    modifier = Modifier.padding(size * 0.2f),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        // Show presence indicator if enabled
        if (showPresence) {
            PresenceIndicator(
                isOnline = isOnline,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

