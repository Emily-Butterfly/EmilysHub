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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import de.emilyshub.backup.Backups
import de.emilyshub.data.AppSettings
import de.emilyshub.data.Design
import de.emilyshub.data.Settings
import de.emilyshub.notes.Notes
import de.emilyshub.places.CurrentLocation
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.Places
import de.emilyshub.routines.Routines
import de.emilyshub.security.AppLock
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.tags.Tags
import de.emilyshub.todos.TodoOrder
import de.emilyshub.todos.Todos
import de.emilyshub.ui.backups.BackupScreen
import de.emilyshub.ui.lock.LockScreen
import de.emilyshub.ui.notes.NoteEditorScreen
import de.emilyshub.ui.notes.NoteEditorTarget
import de.emilyshub.ui.notes.NoteTabsScreen
import de.emilyshub.ui.notes.NotesScreen
import de.emilyshub.ui.notes.NotesView
import de.emilyshub.ui.places.PlaceEditorScreen
import de.emilyshub.ui.places.PlaceEditorTarget
import de.emilyshub.ui.places.PlacesScreen
import de.emilyshub.ui.reminders.ReminderPermissions
import de.emilyshub.ui.routines.ArchiveScreen
import de.emilyshub.ui.routines.RoutineEditorScreen
import de.emilyshub.ui.routines.RoutineEditorTarget
import de.emilyshub.ui.routines.RoutinesScreen
import de.emilyshub.ui.settings.SettingsScreen
import de.emilyshub.ui.stats.RoutineStatisticsScreen
import de.emilyshub.ui.stats.StatisticsScreen
import de.emilyshub.ui.status.StatusScreen
import de.emilyshub.ui.status.rememberUse
import de.emilyshub.ui.tags.TagsScreen
import de.emilyshub.ui.theme.EmilysHubTheme
import de.emilyshub.ui.theme.isDark
import de.emilyshub.ui.today.TodayScreen
import de.emilyshub.ui.todos.TodoArchiveScreen
import de.emilyshub.ui.todos.TodoEditorScreen
import de.emilyshub.ui.todos.TodoEditorTarget
import de.emilyshub.ui.todos.TodosScreen
import de.emilyshub.ui.wishes.WishEditorScreen
import de.emilyshub.ui.wishes.WishEditorTarget
import de.emilyshub.ui.wishes.WishReviewScreen
import de.emilyshub.ui.wishes.WishlistScreen
import de.emilyshub.wishes.Wishes
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
    todos: Todos,
    wishes: Wishes,
    notes: Notes,
    tags: Tags,
    places: Places,
    wifi: CurrentWifi,
    location: CurrentLocation,
    backups: Backups,
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
                    todos = todos,
                    wishes = wishes,
                    notes = notes,
                    tags = tags,
                    places = places,
                    wifi = wifi,
                    location = location,
                    backups = backups,
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
                    onLookAroundChange = { enabled -> scope.launch { settings.setLookAroundEnabled(enabled) } },
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
    todos: Todos,
    wishes: Wishes,
    notes: Notes,
    tags: Tags,
    places: Places,
    wifi: CurrentWifi,
    location: CurrentLocation,
    backups: Backups,
    onProbe: () -> Unit,
    settings: Settings,
    canAuthenticate: () -> Boolean,
    onDesignChange: (Design) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onLookAroundChange: (Boolean) -> Unit,
    onRemindersAsked: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Today) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var routineEditor by rememberSaveable { mutableStateOf<RoutineEditorTarget?>(null) }
    var archiveOpen by rememberSaveable { mutableStateOf(false) }
    var statisticsOpen by rememberSaveable { mutableStateOf(false) }
    var statisticsRoutineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    var placesOpen by rememberSaveable { mutableStateOf(false) }
    var statusOpen by rememberSaveable { mutableStateOf(false) }
    var backupsOpen by rememberSaveable { mutableStateOf(false) }
    var todoEditor by rememberSaveable { mutableStateOf<TodoEditorTarget?>(null) }
    var todoArchiveOpen by rememberSaveable { mutableStateOf(false) }
    var wishEditor by rememberSaveable { mutableStateOf<WishEditorTarget?>(null) }
    var wishReviewOpen by rememberSaveable { mutableStateOf(false) }
    var wishTagFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var noteEditor by rememberSaveable { mutableStateOf<NoteEditorTarget?>(null) }
    var noteTabsOpen by rememberSaveable { mutableStateOf(false) }
    // Here, so that it stays while a note is open and while another area shows.
    var notesView by rememberSaveable { mutableStateOf(NotesView()) }
    // Here, so that they stay while a to-do is open and while another tab shows.
    var todoOrder by rememberSaveable { mutableStateOf(TodoOrder.ByDue) }
    var todoTagFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var placeEditor by rememberSaveable { mutableStateOf<PlaceEditorTarget?>(null) }
    // Here, so that it stays while a routine is open and while another tab shows.
    var routineTagFilter by rememberSaveable { mutableStateOf<Long?>(null) }

    val editor = routineEditor
    val placeTarget = placeEditor
    val todoTarget = todoEditor
    val wishTarget = wishEditor
    val noteTarget = noteEditor
    val statisticsRoutine = statisticsRoutineId
    when {
        todoTarget != null -> {
            BackHandler { todoEditor = null }
            TodoEditorScreen(todos, wishes, tags, places, todoTarget, onClose = { todoEditor = null })
        }
        todoArchiveOpen -> {
            BackHandler { todoArchiveOpen = false }
            TodoArchiveScreen(todos, onBack = { todoArchiveOpen = false })
        }
        // Opened from the list or the review, the editor goes back there.
        wishTarget != null -> {
            BackHandler { wishEditor = null }
            // A new editor for each wish, so that one opened from another starts afresh.
            key(wishTarget) {
                WishEditorScreen(
                    wishes = wishes,
                    todos = todos,
                    tags = tags,
                    target = wishTarget,
                    onClose = { wishEditor = null },
                    onOpenWish = { wishEditor = it },
                    onOpenTodo = {
                        wishEditor = null
                        todoEditor = TodoEditorTarget.Edit(it)
                    },
                )
            }
        }
        wishReviewOpen -> {
            BackHandler { wishReviewOpen = false }
            WishReviewScreen(wishes, onOpen = { wishEditor = WishEditorTarget.Edit(it) }, onBack = { wishReviewOpen = false })
        }
        noteTarget != null -> {
            BackHandler { noteEditor = null }
            NoteEditorScreen(notes, noteTarget, onClose = { noteEditor = null })
        }
        noteTabsOpen -> {
            BackHandler { noteTabsOpen = false }
            NoteTabsScreen(notes, onBack = { noteTabsOpen = false })
        }
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
            StatusScreen(routines, todos, onProbe, onBack = { statusOpen = false })
        }
        tagsOpen -> {
            BackHandler { tagsOpen = false }
            TagsScreen(tags, onBack = { tagsOpen = false })
        }
        backupsOpen -> {
            BackHandler { backupsOpen = false }
            BackupScreen(backups, onBack = { backupsOpen = false })
        }
        settingsOpen -> {
            BackHandler { settingsOpen = false }
            SettingsScreen(
                settings = settings,
                canAuthenticate = canAuthenticate,
                onDesignChange = onDesignChange,
                onAppLockChange = onAppLockChange,
                onLookAroundChange = onLookAroundChange,
                onOpenStatus = { statusOpen = true },
                onOpenPlaces = { placesOpen = true },
                onOpenTags = { tagsOpen = true },
                onOpenBackups = { backupsOpen = true },
                onBack = { settingsOpen = false },
            )
        }
        editor != null -> {
            BackHandler { routineEditor = null }
            RoutineEditorScreen(routines, wishes, tags, places, editor, onClose = { routineEditor = null })
        }
        archiveOpen -> {
            BackHandler { archiveOpen = false }
            ArchiveScreen(routines, onBack = { archiveOpen = false })
        }
        // A routine's statistics go back to those of all routines.
        statisticsRoutine != null -> {
            BackHandler { statisticsRoutineId = null }
            RoutineStatisticsScreen(routines, statisticsRoutine, onBack = { statisticsRoutineId = null })
        }
        statisticsOpen -> {
            BackHandler { statisticsOpen = false }
            StatisticsScreen(routines, onOpenRoutine = { statisticsRoutineId = it }, onBack = { statisticsOpen = false })
        }
        else -> {
            // Heute is the start screen: back from any other area returns there before leaving the app.
            BackHandler(enabled = current != TopLevelDestination.Today) { current = TopLevelDestination.Today }
            TopLevelScaffold(
                routines = routines,
                todos = todos,
                wishes = wishes,
                tags = tags,
                routineTagFilter = routineTagFilter,
                onRoutineTagFilterChange = { routineTagFilter = it },
                todoOrder = todoOrder,
                onTodoOrderChange = { todoOrder = it },
                todoTagFilter = todoTagFilter,
                onTodoTagFilterChange = { todoTagFilter = it },
                onOpenTodo = { todoEditor = it },
                onOpenTodoArchive = { todoArchiveOpen = true },
                wishTagFilter = wishTagFilter,
                onWishTagFilterChange = { wishTagFilter = it },
                onOpenWish = { wishEditor = it },
                onOpenWishReview = { wishReviewOpen = true },
                notes = notes,
                notesView = notesView,
                onNotesViewChange = { notesView = it },
                onOpenNote = { noteEditor = it },
                onOpenNoteTabs = { noteTabsOpen = true },
                current = current,
                onSelect = { current = it },
                onOpenSettings = { settingsOpen = true },
                onOpenRoutine = { routineEditor = it },
                onOpenArchive = { archiveOpen = true },
                onOpenStatistics = { statisticsOpen = true },
                onOpenStatus = { statusOpen = true },
            )
            if (!settings.remindersAsked) AskOnceSomethingReminds(routines, todos, onRemindersAsked)
        }
    }
}

/** Asks for what reminders need (SPEC.md §11) once something reminds, an active routine or a to-do that does, not before. */
@Composable
private fun AskOnceSomethingReminds(routines: Routines, todos: Todos, onAsked: () -> Unit) {
    if (rememberUse(routines, todos)?.reminds == true) ReminderPermissions(onFinished = onAsked)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopLevelScaffold(
    routines: Routines,
    todos: Todos,
    wishes: Wishes,
    tags: Tags,
    routineTagFilter: Long?,
    onRoutineTagFilterChange: (Long?) -> Unit,
    todoOrder: TodoOrder,
    onTodoOrderChange: (TodoOrder) -> Unit,
    todoTagFilter: Long?,
    onTodoTagFilterChange: (Long?) -> Unit,
    onOpenTodo: (TodoEditorTarget) -> Unit,
    onOpenTodoArchive: () -> Unit,
    wishTagFilter: Long?,
    onWishTagFilterChange: (Long?) -> Unit,
    onOpenWish: (WishEditorTarget) -> Unit,
    onOpenWishReview: () -> Unit,
    notes: Notes,
    notesView: NotesView,
    onNotesViewChange: (NotesView) -> Unit,
    onOpenNote: (NoteEditorTarget) -> Unit,
    onOpenNoteTabs: () -> Unit,
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoutine: (RoutineEditorTarget) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenStatistics: () -> Unit,
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
        when (current) {
            TopLevelDestination.Today -> TodayScreen(routines, todos, contentPadding, onOpenStatus)
            TopLevelDestination.Routines -> RoutinesScreen(
                routines = routines,
                tags = tags,
                tagFilter = routineTagFilter,
                onTagFilterChange = onRoutineTagFilterChange,
                contentPadding = contentPadding,
                onOpen = { onOpenRoutine(RoutineEditorTarget.Edit(it)) },
                onCreate = { onOpenRoutine(RoutineEditorTarget.New) },
                onOpenArchive = onOpenArchive,
                onOpenStatistics = onOpenStatistics,
            )
            TopLevelDestination.Todos -> TodosScreen(
                todos = todos,
                tags = tags,
                order = todoOrder,
                onOrderChange = onTodoOrderChange,
                tagFilter = todoTagFilter,
                onTagFilterChange = onTodoTagFilterChange,
                contentPadding = contentPadding,
                onOpen = { onOpenTodo(TodoEditorTarget.Edit(it)) },
                onCreate = { onOpenTodo(TodoEditorTarget.New) },
                onOpenArchive = onOpenTodoArchive,
            )
            TopLevelDestination.Wishlist -> WishlistScreen(
                wishes = wishes,
                todos = todos,
                tags = tags,
                tagFilter = wishTagFilter,
                onTagFilterChange = onWishTagFilterChange,
                contentPadding = contentPadding,
                onOpen = { onOpenWish(WishEditorTarget.Edit(it)) },
                onOpenTodo = { onOpenTodo(TodoEditorTarget.Edit(it)) },
                onOpenRoutine = { onOpenRoutine(RoutineEditorTarget.Edit(it)) },
                onCreate = { onOpenWish(WishEditorTarget.New()) },
                onOpenReview = onOpenWishReview,
            )
            TopLevelDestination.Notes -> NotesScreen(
                notes = notes,
                view = notesView,
                onViewChange = onNotesViewChange,
                contentPadding = contentPadding,
                onOpenNote = { onOpenNote(NoteEditorTarget.Edit(it)) },
                onCreateNote = { onOpenNote(NoteEditorTarget.New(it)) },
                onManageTabs = onOpenNoteTabs,
            )
        }
    }
}
