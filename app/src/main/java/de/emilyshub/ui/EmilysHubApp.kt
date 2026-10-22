package de.emilyshub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.ui.settings.SettingsScreen

@Composable
fun EmilysHubApp() {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(onBack = { settingsOpen = false })
    } else {
        // Heute is the start screen: back from any other area returns there before leaving the app.
        BackHandler(enabled = current != TopLevelDestination.Today) { current = TopLevelDestination.Today }
        TopLevelScaffold(
            current = current,
            onSelect = { current = it },
            onOpenSettings = { settingsOpen = true },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopLevelScaffold(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(current.label), modifier = Modifier.semantics { heading() })
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { onSelect(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { contentPadding ->
        // Each later slice swaps its branch for the real screen.
        val placeholder = when (current) {
            TopLevelDestination.Today -> R.string.placeholder_today
            TopLevelDestination.Routines -> R.string.placeholder_routines
            TopLevelDestination.Todos -> R.string.placeholder_todos
            TopLevelDestination.Wishlist -> R.string.placeholder_wishlist
            TopLevelDestination.Notes -> R.string.placeholder_notes
        }
        PlaceholderScreen(stringResource(placeholder), contentPadding)
    }
}
