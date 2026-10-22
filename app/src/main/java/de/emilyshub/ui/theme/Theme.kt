package de.emilyshub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Flieder when light, Lila when dark; [darkTheme] follows the system unless the "Design" setting says otherwise. */
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
