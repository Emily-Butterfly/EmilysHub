package de.emilyshub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Flieder when light, Lila when dark. There is no default for [darkTheme]: it comes from the
 * "Design" setting, which only follows the system when set to "System".
 */
@Composable
fun EmilysHubTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LilaColorScheme else FliederColorScheme,
        content = content,
    )
}
