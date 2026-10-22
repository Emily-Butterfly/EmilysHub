package de.emilyshub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Follows the system's light/dark setting; an in-app override comes with the settings screen. */
@Composable
fun EmilysHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LilaColorScheme else FliederColorScheme,
        content = content,
    )
}
