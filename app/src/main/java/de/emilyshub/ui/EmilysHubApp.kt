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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.backup.Backups
import de.emilyshub.backup.NightlyBackups
import de.emilyshub.data.AppSettings
import de.emilyshub.data.Design
import de.emilyshub.data.Settings
import de.emilyshub.ledger.Ledger
import de.emilyshub.notes.Notes
import de.emilyshub.places.CurrentLocation
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.Places
import de.emilyshub.reminders.Tone
import de.emilyshub.reminders.ToneCollection
import de.emilyshub.routines.Routines
import de.emilyshub.search.Search
import de.emilyshub.security.AppLock
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.tags.Tags
import de.emilyshub.todos.TodoOrder
import de.emilyshub.todos.Todos
import de.emilyshub.ui.backups.BackupScreen
import de.emilyshub.ui.ledger.BookingEditorScreen
import de.emilyshub.ui.ledger.BookingEditorTarget
import de.emilyshub.ui.ledger.LedgerArchiveScreen
import de.emilyshub.ui.ledger.LedgerCategoriesScreen
import de.emilyshub.ui.ledger.LedgerScreen
import de.emilyshub.ui.ledger.LedgerYearScreen
import de.emilyshub.ui.ledger.RecurringEditorScreen
import de.emilyshub.ui.ledger.RecurringEditorTarget
import de.emilyshub.ui.ledger.RecurringListScreen
import de.emilyshub.ui.lock.LockScreen
import de.emilyshub.ui.navigation.BackStack
import de.emilyshub.ui.navigation.Destination
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
import de.emilyshub.ui.search.SearchScreen
import de.emilyshub.ui.settings.SettingsScreen
import de.emilyshub.ui.stats.RoutineStatisticsScreen
import de.emilyshub.ui.stats.StatisticsScreen
import de.emilyshub.ui.status.StatusScreen
import de.emilyshub.ui.status.rememberUse
import de.emilyshub.ui.tags.TagsScreen
import de.emilyshub.ui.theme.EmilysHubTheme
import de.emilyshub.ui.theme.isDark
import de.emilyshub.ui.today.StreakFlameButton
import de.emilyshub.ui.today.TodayScreen
import de.emilyshub.ui.today.rememberToday
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
    nightlyBackups: NightlyBackups,
    search: Search,
    ledger: Ledger,
    tones: ToneCollection,
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
                    nightlyBackups = nightlyBackups,
                    search = search,
                    ledger = ledger,
                    tones = tones,
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
                    onAlarmToneChange = { tone -> scope.launch { settings.setAlarmTone(tone) } },
                    onNotificationToneChange = { tone -> scope.launch { settings.setNotificationTone(tone) } },
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
    nightlyBackups: NightlyBackups,
    search: Search,
    ledger: Ledger,
    tones: ToneCollection,
    onProbe: () -> Unit,
    settings: Settings,
    canAuthenticate: () -> Boolean,
    onDesignChange: (Design) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onLookAroundChange: (Boolean) -> Unit,
    onAlarmToneChange: (Tone) -> Unit,
    onNotificationToneChange: (Tone) -> Unit,
    onRemindersAsked: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.Today) }
    // The screens open over the tabs, each going back to the one it was opened from.
    var stack by rememberSaveable { mutableStateOf(BackStack()) }
    val open = { destination: Destination -> stack = stack.push(destination) }
    val back = { stack = stack.pop() }
    // Here, so that they stay while an entry is open and while another tab shows.
    var wishTagFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var notesView by rememberSaveable { mutableStateOf(NotesView()) }
    var todoOrder by rememberSaveable { mutableStateOf(TodoOrder.ByDue) }
    var todoTagFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var routineTagFilter by rememberSaveable { mutableStateOf<Long?>(null) }

    // What a screen over the tabs keeps while one opened from it covers it: what was typed in an editor a note was
    // opened from, say. The tabs keep only what is kept here above.
    val screens = rememberSaveableStateHolder()
    ForgetClosedScreens(screens, stack)
    val openNote = { id: Long -> open(Destination.NoteEditor(NoteEditorTarget.Edit(id))) }
    val top = stack.top
    if (top == null) {
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
            onOpenTodo = { open(Destination.TodoEditor(it)) },
            onOpenTodoArchive = { open(Destination.TodoArchive) },
            wishTagFilter = wishTagFilter,
            onWishTagFilterChange = { wishTagFilter = it },
            onOpenWish = { open(Destination.WishEditor(it)) },
            onOpenWishReview = { open(Destination.WishReview) },
            notes = notes,
            nightlyBackups = nightlyBackups,
            notesView = notesView,
            onNotesViewChange = { notesView = it },
            onOpenNote = { open(Destination.NoteEditor(it)) },
            onOpenNoteTabs = { open(Destination.NoteTabs) },
            onOpenLedger = { open(Destination.Ledger(ledger.thisMonth())) },
            current = current,
            onSelect = { current = it },
            onOpenSettings = { open(Destination.Settings) },
            onOpenSearch = { open(Destination.Search()) },
            onOpenRoutine = { open(Destination.RoutineEditor(it)) },
            onOpenArchive = { open(Destination.RoutineArchive) },
            onOpenStatistics = { open(Destination.Statistics) },
            onOpenStatus = { open(Destination.Status) },
            onOpenBackups = { open(Destination.Backups) },
        )
        if (!settings.remindersAsked) AskOnceSomethingReminds(routines, todos, onRemindersAsked)
        return
    }
    BackHandler(onBack = back)
    screens.SaveableStateProvider(checkNotNull(stack.topStateKey)) {
        when (top) {
            is Destination.NoteEditor -> NoteEditorScreen(notes, tags, top.target, onClose = back)
            is Destination.TodoEditor -> TodoEditorScreen(
                todos, wishes, tags, places, notes, top.target, tones, onClose = back, onOpenNote = openNote,
            )
            Destination.TodoArchive ->
                TodoArchiveScreen(todos, onOpen = { open(Destination.TodoEditor(TodoEditorTarget.Edit(it))) }, onBack = back)
            is Destination.WishEditor -> WishEditorScreen(
                wishes = wishes,
                todos = todos,
                tags = tags,
                notes = notes,
                target = top.target,
                onClose = back,
                onOpenNote = openNote,
                // A new editor for each wish, so that one opened from another starts afresh.
                onOpenWish = { stack = stack.replaceTop(Destination.WishEditor(it)) },
                // The to-do made of it in its place, going back where it would.
                onOpenTodo = { stack = stack.replaceTop(Destination.TodoEditor(TodoEditorTarget.Edit(it))) },
            )
            Destination.WishReview ->
                WishReviewScreen(wishes, onOpen = { open(Destination.WishEditor(WishEditorTarget.Edit(it))) }, onBack = back)
            is Destination.BookingEditor -> BookingEditorScreen(ledger, top.target, onClose = back)
            is Destination.RecurringEditor -> RecurringEditorScreen(ledger, top.target, onClose = back)
            Destination.Recurring -> RecurringListScreen(ledger, onOpen = { open(Destination.RecurringEditor(it)) }, onBack = back)
            Destination.LedgerArchive ->
                LedgerArchiveScreen(ledger, onOpen = { stack = stack.openArchivedMonth(it) }, onBack = back)
            is Destination.LedgerYear -> LedgerYearScreen(
                ledger = ledger,
                year = top.year,
                onYearChange = { stack = stack.replaceTop(Destination.LedgerYear(it)) },
                onOpenMonth = { open(Destination.Ledger(it)) },
                onBack = back,
            )
            Destination.LedgerCategories -> LedgerCategoriesScreen(ledger, onBack = back)
            is Destination.Ledger -> LedgerScreen(
                ledger = ledger,
                month = top.month,
                onMonthChange = { stack = stack.replaceTop(Destination.Ledger(it)) },
                onOpen = { open(Destination.BookingEditor(BookingEditorTarget.Edit(it))) },
                onCreate = { open(Destination.BookingEditor(BookingEditorTarget.New)) },
                onOpenCategories = { open(Destination.LedgerCategories) },
                onOpenRecurring = { open(Destination.Recurring) },
                onOpenArchive = { open(Destination.LedgerArchive) },
                onOpenYear = { stack = stack.openLedgerYear() },
                onBack = back,
            )
            Destination.NoteTabs -> NoteTabsScreen(notes, onBack = back)
            is Destination.PlaceEditor -> PlaceEditorScreen(places, wifi, location, top.target, onClose = back)
            Destination.Places -> PlacesScreen(
                places = places,
                wifi = wifi,
                location = location,
                onOpen = { open(Destination.PlaceEditor(PlaceEditorTarget.Edit(it))) },
                onCreate = { open(Destination.PlaceEditor(PlaceEditorTarget.New)) },
                onBack = back,
            )
            Destination.Status -> StatusScreen(routines, todos, onProbe, onBack = back)
            Destination.Tags -> TagsScreen(tags, onBack = back)
            Destination.Backups -> BackupScreen(backups, nightlyBackups, onBack = back)
            Destination.Settings -> SettingsScreen(
                settings = settings,
                canAuthenticate = canAuthenticate,
                onDesignChange = onDesignChange,
                onAppLockChange = onAppLockChange,
                onLookAroundChange = onLookAroundChange,
                tones = tones,
                onAlarmToneChange = onAlarmToneChange,
                onNotificationToneChange = onNotificationToneChange,
                onOpenStatus = { open(Destination.Status) },
                onOpenPlaces = { open(Destination.Places) },
                onOpenTags = { open(Destination.Tags) },
                onOpenBackups = { open(Destination.Backups) },
                onBack = back,
            )
            is Destination.RoutineEditor -> RoutineEditorScreen(
                routines, wishes, tags, places, notes, top.target, tones, onClose = back, onOpenNote = openNote,
            )
            Destination.RoutineArchive -> ArchiveScreen(routines, onBack = back)
            is Destination.RoutineStatistics -> RoutineStatisticsScreen(routines, top.routineId, onBack = back)
            Destination.Statistics ->
                StatisticsScreen(routines, onOpenRoutine = { open(Destination.RoutineStatistics(it)) }, onBack = back)
            is Destination.Search -> SearchScreen(
                search = search,
                query = top.query,
                onQueryChange = { stack = stack.search(it) },
                // An entry found opens over the search, which keeps what was searched for.
                onOpen = { area, id -> open(Destination.found(area, id)) },
                onBack = back,
            )
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
    nightlyBackups: NightlyBackups,
    notesView: NotesView,
    onNotesViewChange: (NotesView) -> Unit,
    onOpenNote: (NoteEditorTarget) -> Unit,
    onOpenNoteTabs: () -> Unit,
    onOpenLedger: () -> Unit,
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRoutine: (RoutineEditorTarget) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenBackups: () -> Unit,
) {
    // Only while Heute shows, for its list and its flame alike, so that both turn to the new day together.
    val today = if (current == TopLevelDestination.Today) rememberToday(routines) else null
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(current.label), modifier = Modifier.semantics { heading() })
                },
                actions = {
                    // Only on Heute: the streak spurs on there, where the day's routines are ticked off (SPEC.md §1).
                    if (today != null) StreakFlameButton(today, routines, onOpenStatistics)
                    // The household book is reached from the notes (SPEC.md §13).
                    if (current == TopLevelDestination.Notes) {
                        IconButton(onClick = onOpenLedger) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.ledger_open))
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
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
            TopLevelDestination.Today -> TodayScreen(
                today = checkNotNull(today),
                routines = routines,
                todos = todos,
                notes = notes,
                nightlyBackups = nightlyBackups,
                contentPadding = contentPadding,
                onOpenStatus = onOpenStatus,
                onOpenBackups = onOpenBackups,
                onOpenNote = { onOpenNote(NoteEditorTarget.Edit(it)) },
            )
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

/**
 * Forgets what a screen over the tabs kept once it is closed, gone from the [stack] (SPEC.md §1), so that opening it
 * again starts afresh; one covered by another keeps it.
 */
@Composable
private fun ForgetClosedScreens(screens: SaveableStateHolder, stack: BackStack) {
    var kept by remember { mutableStateOf(emptySet<String>()) }
    val open = stack.stateKeys.toSet()
    LaunchedEffect(open) {
        (kept - open).forEach(screens::removeState)
        kept = open
    }
}
