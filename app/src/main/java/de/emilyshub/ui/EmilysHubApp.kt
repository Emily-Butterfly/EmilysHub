package de.emilyshub.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.data.AppSettings
import de.emilyshub.data.Design
import de.emilyshub.data.Settings
import de.emilyshub.security.AppLock
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.ui.lock.LockScreen
import de.emilyshub.ui.settings.SettingsScreen
import de.emilyshub.ui.theme.EmilysHubTheme
import kotlinx.coroutines.launch

/**
 * The whole UI. Nothing is drawn before the settings are known, not even the theme, and nothing but
 * the lock screen while the app is locked (SPEC.md §9).
 */
@Composable
fun EmilysHubApp(settings: AppSettings, lock: AppLock, authenticator: DeviceAuthenticator) {
    LaunchedEffect(settings, lock) { lock.setEnabled(settings.load().appLockEnabled) }
    val current = settings.current ?: return

    val activity = LocalActivity.current as ComponentActivity
    val darkTheme = when (current.design) {
        Design.System -> isSystemInDarkTheme()
        Design.Light -> false
        Design.Dark -> true
    }
    SystemBarsFollowTheme(activity, darkTheme)

    // Keeps the open area and screen while the content is gone behind the lock screen.
    val lockedAwayState = rememberSaveableStateHolder()
    // Outside the content, so that locking does not cancel a setting that is still being saved.
    val scope = rememberCoroutineScope()
    EmilysHubTheme(darkTheme = darkTheme) {
        if (lock.isLocked) {
            LockScreen(onUnlock = { authenticator.authenticate(activity, lock::unlock) })
        } else {
            lockedAwayState.SaveableStateProvider("content") {
                AppShell(
                    settings = current,
                    canAuthenticate = authenticator::canAuthenticate,
                    onDesignChange = { design -> scope.launch { settings.setDesign(design) } },
                    onAppLockChange = { enabled ->
                        scope.launch {
                            settings.setAppLockEnabled(enabled)
                            lock.setEnabled(enabled)
                        }
                    },
                )
            }
        }
    }
}

/** Light or dark status and navigation bar icons to match the in-app design, not only the system's. */
@Composable
private fun SystemBarsFollowTheme(activity: ComponentActivity, darkTheme: Boolean) {
    DisposableEffect(activity, darkTheme) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}

@Composable
private fun AppShell(
    settings: Settings,
    canAuthenticate: () -> Boolean,
    onDesignChange: (Design) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsScreen(
            settings = settings,
            canAuthenticate = canAuthenticate,
            onDesignChange = onDesignChange,
            onAppLockChange = onAppLockChange,
            onBack = { settingsOpen = false },
        )
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
