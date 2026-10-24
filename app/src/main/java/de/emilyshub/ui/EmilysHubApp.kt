package de.emilyshub.ui

import android.app.UiModeManager
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import de.emilyshub.places.CurrentLocation
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.Places
import de.emilyshub.routines.Routines
import de.emilyshub.security.AppLock
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.tags.Tags
import de.emilyshub.ui.lock.LockScreen
import de.emilyshub.ui.places.PlaceEditorScreen
import de.emilyshub.ui.places.PlaceEditorTarget
import de.emilyshub.ui.places.PlacesScreen
import de.emilyshub.ui.reminders.ReminderPermissions
import de.emilyshub.ui.routines.ArchiveScreen
import de.emilyshub.ui.routines.RoutineEditorScreen
import de.emilyshub.ui.routines.RoutineEditorTarget
import de.emilyshub.ui.routines.RoutinesScreen
import de.emilyshub.ui.settings.SettingsScreen
import de.emilyshub.ui.status.StatusScreen
import de.emilyshub.ui.tags.TagsScreen
import de.emilyshub.ui.theme.EmilysHubTheme
import de.emilyshub.ui.theme.isDark
import de.emilyshub.ui.today.TodayScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The whole UI. Nothing is drawn before the settings are known, not even the theme, and nothing but
 * the lock screen while the app is locked (SPEC.md §9).
 */
@Composable
fun EmilysHubApp(
    settings: AppSettings,
    lock: AppLock,
    authenticator: DeviceAuthenticator,
    routines: Routines,
    tags: Tags,
    places: Places,
    wifi: CurrentWifi,
    location: CurrentLocation,
    onProbe: () -> Unit,
) {
    LaunchedEffect(settings, lock) { lock.setEnabled(settings.load().appLockEnabled) }
    val current = settings.current ?: return

    val activity = LocalActivity.current as ComponentActivity
    val darkTheme = isDark(current.design)
    SystemFollowsDesign(activity, current.design, darkTheme)

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
                    routines = routines,
                    tags = tags,
                    places = places,
                    wifi = wifi,
                    location = location,
                    onProbe = onProbe,
                    settings = current,
                    canAuthenticate = authenticator::canAuthenticate,
                    onDesignChange = { design -> scope.launch { settings.setDesign(design) } },
                    onAppLockChange = { enabled ->
                        scope.launch {
                            settings.setAppLockEnabled(enabled)
                            lock.setEnabled(enabled)
                        }
                    },
                    onRemindersAsked = { scope.launch { settings.setRemindersAsked() } },
                )
            }
        }
    }
}

/**
 * What Android draws around and before the app's own frames follows the in-app design too: the
 * status and navigation bar icons, and from Android 12 on the window background and the starting
 * window shown on launch and return. Android keeps the latter choice itself, so it already applies
 * on the next cold start, before the settings are loaded.
 */
@Composable
private fun SystemFollowsDesign(activity: ComponentActivity, design: Design, darkTheme: Boolean) {
    DisposableEffect(activity, design, darkTheme) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // A change recreates the activity, like any dark mode switch; its screen state is kept.
            activity.getSystemService(UiModeManager::class.java).setApplicationNightMode(
                when (design) {
                    Design.System -> UiModeManager.MODE_NIGHT_AUTO
                    Design.Light -> UiModeManager.MODE_NIGHT_NO
                    Design.Dark -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
        onDispose {}
    }
}

@Composable
private fun AppShell(
    routines: Routines,
    tags: Tags,
    places: Places,
    wifi: CurrentWifi,
    location: CurrentLocation,
    onProbe: () -> Unit,
    settings: Settings,
    canAuthenticate: () -> Boolean,
    onDesignChange: (Design) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onRemindersAsked: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var routineEditor by rememberSaveable { mutableStateOf<RoutineEditorTarget?>(null) }
    var archiveOpen by rememberSaveable { mutableStateOf(false) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var statusOpen by rememberSaveable { mutableStateOf(false) }
    var placeEditor by rememberSaveable { mutableStateOf<PlaceEditorTarget?>(null) }
    // Here, so that it stays while a routine is open and while another tab shows.
    var routineTagFilter by rememberSaveable { mutableStateOf<Long?>(null) }

    val editor = routineEditor
    val placeTarget = placeEditor
    when {
        // Each opened from the one below it, it goes back there: the place editor, the places, the settings.
        placeTarget != null -> {
            BackHandler { placeEditor = null }
            PlaceEditorScreen(places, wifi, location, placeTarget, onClose = { placeEditor = null })
        }
        placesOpen -> {
            BackHandler { placesOpen = false }
            PlacesScreen(
                places = places,
                wifi = wifi,
                location = location,
                onOpen = { placeEditor = PlaceEditorTarget.Edit(it) },
                onCreate = { placeEditor = PlaceEditorTarget.New },
                onBack = { placesOpen = false },
            )
        }
        statusOpen -> {
            BackHandler { statusOpen = false }
            StatusScreen(routines, onProbe, onBack = { statusOpen = false })
        }
        tagsOpen -> {
            BackHandler { tagsOpen = false }
            TagsScreen(tags, onBack = { tagsOpen = false })
        }
        settingsOpen -> {
            BackHandler { settingsOpen = false }
            SettingsScreen(
                settings = settings,
                canAuthenticate = canAuthenticate,
                onDesignChange = onDesignChange,
                onAppLockChange = onAppLockChange,
                onOpenStatus = { statusOpen = true },
                onOpenPlaces = { placesOpen = true },
                onOpenTags = { tagsOpen = true },
                onBack = { settingsOpen = false },
            )
        }
        editor != null -> {
            BackHandler { routineEditor = null }
            RoutineEditorScreen(routines, tags, places, editor, onClose = { routineEditor = null })
        }
        archiveOpen -> {
            BackHandler { archiveOpen = false }
            ArchiveScreen(routines, onBack = { archiveOpen = false })
        }
        else -> {
            // Heute is the start screen: back from any other area returns there before leaving the app.
            BackHandler(enabled = current != TopLevelDestination.Today) { current = TopLevelDestination.Today }
            TopLevelScaffold(
                routines = routines,
                tags = tags,
                routineTagFilter = routineTagFilter,
                onRoutineTagFilterChange = { routineTagFilter = it },
                current = current,
                onSelect = { current = it },
                onOpenSettings = { settingsOpen = true },
                onOpenRoutine = { routineEditor = it },
                onOpenArchive = { archiveOpen = true },
                onOpenStatus = { statusOpen = true },
            )
            if (!settings.remindersAsked) AskOnceThereIsARoutine(routines, onRemindersAsked)
        }
    }
}

/** Asks for what reminders need (SPEC.md §11) once the first routine is there, not before. */
@Composable
private fun AskOnceThereIsARoutine(routines: Routines, onAsked: () -> Unit) {
    val anyRoutine by remember(routines) { routines.inUse().map { it.isNotEmpty() } }.collectAsState(initial = false)
    if (anyRoutine) ReminderPermissions(onFinished = onAsked)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopLevelScaffold(
    routines: Routines,
    tags: Tags,
    routineTagFilter: Long?,
    onRoutineTagFilterChange: (Long?) -> Unit,
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoutine: (RoutineEditorTarget) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenStatus: () -> Unit,
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
        // Each later slice swaps its placeholder for the real screen.
        when (current) {
            TopLevelDestination.Today -> TodayScreen(routines, contentPadding, onOpenStatus)
            TopLevelDestination.Routines -> RoutinesScreen(
                routines = routines,
                tags = tags,
                tagFilter = routineTagFilter,
                onTagFilterChange = onRoutineTagFilterChange,
                contentPadding = contentPadding,
                onOpen = { onOpenRoutine(RoutineEditorTarget.Edit(it)) },
                onCreate = { onOpenRoutine(RoutineEditorTarget.New) },
                onOpenArchive = onOpenArchive,
            )
            TopLevelDestination.Todos -> PlaceholderScreen(stringResource(R.string.placeholder_todos), contentPadding)
            TopLevelDestination.Wishlist -> PlaceholderScreen(stringResource(R.string.placeholder_wishlist), contentPadding)
            TopLevelDestination.Notes -> PlaceholderScreen(stringResource(R.string.placeholder_notes), contentPadding)
        }
    }
}
