package de.emilyshub.ui

import android.app.UiModeManager
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.Hub
import de.emilyshub.R
import de.emilyshub.backup.Backups
import de.emilyshub.data.Design
import de.emilyshub.data.Settings
import de.emilyshub.ledger.Ledger
import de.emilyshub.notes.Notes
import de.emilyshub.places.Places
import de.emilyshub.reminders.Tone
import de.emilyshub.routines.Routines
import de.emilyshub.search.Search
import de.emilyshub.tags.Tags
import de.emilyshub.today.Heute
import de.emilyshub.todos.Todos
import de.emilyshub.ui.backups.BackupScreen
import de.emilyshub.ui.backups.BackupWarningCard
import de.emilyshub.ui.ledger.BookingEditorScreen
import de.emilyshub.ui.ledger.BookingEditorTarget
import de.emilyshub.ui.ledger.LedgerArchiveScreen
import de.emilyshub.ui.ledger.LedgerButton
import de.emilyshub.ui.ledger.LedgerCategoriesScreen
import de.emilyshub.ui.ledger.LedgerScreen
import de.emilyshub.ui.ledger.LedgerYearScreen
import de.emilyshub.ui.ledger.RecurringEditorScreen
import de.emilyshub.ui.ledger.RecurringListScreen
import de.emilyshub.ui.lock.LockScreen
import de.emilyshub.ui.navigation.BackStack
import de.emilyshub.ui.navigation.Destination
import de.emilyshub.ui.notes.NoteArchiveScreen
import de.emilyshub.ui.notes.NoteEditorScreen
import de.emilyshub.ui.notes.NoteEditorTarget
import de.emilyshub.ui.notes.NoteTabsScreen
import de.emilyshub.ui.notes.NotesScreen
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
import de.emilyshub.ui.status.SetupWarning
import de.emilyshub.ui.status.StatusScreen
import de.emilyshub.ui.status.rememberUse
import de.emilyshub.ui.tags.TagsScreen
import de.emilyshub.ui.theme.EmilysHubTheme
import de.emilyshub.ui.theme.isDark
import de.emilyshub.ui.today.StreakFlameButton
import de.emilyshub.ui.today.TodayScreen
import de.emilyshub.ui.today.rememberHeute
import de.emilyshub.ui.todos.TodoArchiveScreen
import de.emilyshub.ui.todos.TodoEditorScreen
import de.emilyshub.ui.todos.TodoEditorTarget
import de.emilyshub.ui.todos.TodosScreen
import de.emilyshub.ui.wishes.WishEditorScreen
import de.emilyshub.ui.wishes.WishEditorTarget
import de.emilyshub.ui.wishes.WishReviewScreen
import de.emilyshub.ui.wishes.WishlistScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The whole UI. Nothing is drawn before the settings are known, not even the theme, and nothing but
 * the lock screen while the app is locked (SPEC.md §9).
 */
@Composable
fun EmilysHubApp(hub: Hub) {
    val settings = hub.settings
    val lock = hub.appLock
    val authenticator = hub.authenticator
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
                    hub = hub,
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
    hub: Hub,
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
    var tabs by rememberSaveable { mutableStateOf(TabViews()) }

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
            hub = hub,
            current = current,
            onSelect = { current = it },
            tabs = tabs,
            onTabsChange = { tabs = it },
            open = open,
        )
        if (!settings.remindersAsked) AskOnceSomethingReminds(hub.routines, hub.todos, onRemindersAsked)
        return
    }
    BackHandler(onBack = back)
    screens.SaveableStateProvider(checkNotNull(stack.topStateKey)) {
        when (top) {
            is Destination.NoteEditor -> NoteEditorScreen(hub.notes, hub.tags, top.target, onClose = back)
            is Destination.TodoEditor -> TodoEditorScreen(
                hub.todos, hub.day, hub.wishes, hub.tags, hub.places, hub.notes, top.target, hub.toneCollection,
                onClose = back, onOpenNote = openNote,
            )
            Destination.TodoArchive ->
                TodoArchiveScreen(
                    hub.todos, hub.day, onOpen = { open(Destination.TodoEditor(TodoEditorTarget.Edit(it))) }, onBack = back,
                )
            is Destination.WishEditor -> WishEditorScreen(
                wishes = hub.wishes,
                day = hub.day,
                todos = hub.todos,
                tags = hub.tags,
                notes = hub.notes,
                target = top.target,
                onClose = back,
                onOpenNote = openNote,
                // A new editor for each wish, so that one opened from another starts afresh.
                onOpenWish = { stack = stack.replaceTop(Destination.WishEditor(it)) },
                // The to-do made of it in its place, going back where it would.
                onOpenTodo = { stack = stack.replaceTop(Destination.TodoEditor(TodoEditorTarget.Edit(it))) },
            )
            Destination.WishReview ->
                WishReviewScreen(hub.wishes, onOpen = { open(Destination.WishEditor(WishEditorTarget.Edit(it))) }, onBack = back)
            is Destination.BookingEditor -> BookingEditorScreen(hub.ledger, top.target, onClose = back)
            is Destination.RecurringEditor -> RecurringEditorScreen(hub.ledger, top.target, onClose = back)
            Destination.Recurring -> RecurringListScreen(hub.ledger, onOpen = { open(Destination.RecurringEditor(it)) }, onBack = back)
            Destination.LedgerArchive ->
                LedgerArchiveScreen(hub.ledger, onOpen = { stack = stack.openArchivedMonth(it) }, onBack = back)
            is Destination.LedgerYear -> LedgerYearScreen(
                ledger = hub.ledger,
                day = hub.day,
                year = top.year,
                onYearChange = { stack = stack.replaceTop(Destination.LedgerYear(it)) },
                onOpenMonth = { open(Destination.Ledger(it)) },
                onBack = back,
            )
            Destination.LedgerCategories -> LedgerCategoriesScreen(hub.ledger, onBack = back)
            is Destination.Ledger -> LedgerScreen(
                ledger = hub.ledger,
                day = hub.day,
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
            Destination.NoteTabs -> NoteTabsScreen(hub.notes, onBack = back)
            Destination.NoteArchive -> NoteArchiveScreen(hub.notes, onOpenNote = openNote, onBack = back)
            is Destination.PlaceEditor -> PlaceEditorScreen(hub.places, hub.wifi, hub.location, top.target, onClose = back)
            Destination.Places -> PlacesScreen(
                places = hub.places,
                wifi = hub.wifi,
                location = hub.location,
                onOpen = { open(Destination.PlaceEditor(PlaceEditorTarget.Edit(it))) },
                onCreate = { open(Destination.PlaceEditor(PlaceEditorTarget.New)) },
                onBack = back,
            )
            Destination.Status -> StatusScreen(hub.routines, hub.todos, hub::probe, onBack = back)
            Destination.Tags -> TagsScreen(hub.tags, onBack = back)
            Destination.Backups -> BackupScreen(hub.backups, hub.nightlyBackups, onBack = back)
            Destination.Settings -> SettingsScreen(
                settings = settings,
                canAuthenticate = canAuthenticate,
                onDesignChange = onDesignChange,
                onAppLockChange = onAppLockChange,
                onLookAroundChange = onLookAroundChange,
                tones = hub.toneCollection,
                onAlarmToneChange = onAlarmToneChange,
                onNotificationToneChange = onNotificationToneChange,
                onOpenStatus = { open(Destination.Status) },
                onOpenPlaces = { open(Destination.Places) },
                onOpenTags = { open(Destination.Tags) },
                onOpenBackups = { open(Destination.Backups) },
                onBack = back,
            )
            is Destination.RoutineEditor -> RoutineEditorScreen(
                hub.routines, hub.day, hub.wishes, hub.tags, hub.places, hub.notes, top.target, hub.toneCollection, onClose = back, onOpenNote = openNote,
            )
            Destination.RoutineArchive -> ArchiveScreen(hub.routines, onBack = back)
            is Destination.RoutineStatistics -> RoutineStatisticsScreen(hub.routines, hub.day, top.routineId, onBack = back)
            Destination.Statistics ->
                StatisticsScreen(hub.routines, hub.day, onOpenRoutine = { open(Destination.RoutineStatistics(it)) }, onBack = back)
            is Destination.Search -> SearchScreen(
                search = hub.search,
                query = top.query,
                onQueryChange = { stack = stack.search(it) },
                // An entry found opens over the hub.search, which keeps what was searched for.
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

/**
 * The tabs ([current], [onSelect]) with the bar above them, each tab shown as [tabs] has it ([onTabsChange]); what is
 * tapped there opens over them ([open]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopLevelScaffold(
    hub: Hub,
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    tabs: TabViews,
    onTabsChange: (TabViews) -> Unit,
    open: (Destination) -> Unit,
) {
    // Only while Heute shows, for its list and its flame alike, so that both turn to the new day together; none until
    // all of it is there.
    val heuteDay = if (current == TopLevelDestination.Today) rememberHeute(hub.heute) else null
    val openRoutine = { target: RoutineEditorTarget -> open(Destination.RoutineEditor(target)) }
    val openTodo = { target: TodoEditorTarget -> open(Destination.TodoEditor(target)) }
    val openWish = { target: WishEditorTarget -> open(Destination.WishEditor(target)) }
    val openNote = { target: NoteEditorTarget -> open(Destination.NoteEditor(target)) }
    Scaffold(
        topBar = {
            BoxWithConstraints {
                // The household book is reached from the notes (SPEC.md §1, §13): a button in the middle of the bar,
                // a quarter of the screen wide. The title ends as far left of it as the magnifier begins right of it.
                val ledgerButton = current == TopLevelDestination.Notes
                val buttonWidth = maxWidth / 4
                var magnifierLeft by remember { mutableStateOf<Float?>(null) }
                val titlePlace = if (ledgerButton) {
                    val (buttonLeft, buttonRight) = with(LocalDensity.current) { (maxWidth * 3 / 8).toPx() to (maxWidth * 5 / 8).toPx() }
                    val gap = magnifierLeft?.let { it - buttonRight } ?: with(LocalDensity.current) { TITLE_GAP.toPx() }
                    Modifier.endingAt(buttonLeft - gap, minStart = with(LocalDensity.current) { TITLE_MIN_START.toPx() })
                } else {
                    Modifier
                }
                TopAppBar(
                    title = {
                        Text(
                            stringResource(current.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = titlePlace.semantics { heading() },
                        )
                    },
                    actions = {
                    // Only on Heute: the streak spurs on there, where the day's routines are ticked off (SPEC.md §1).
                        heuteDay?.flame?.let { StreakFlameButton(it, onOpenStatistics = { open(Destination.Statistics) }) }
                        IconButton(onClick = { open(Destination.Search()) }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search),
                                // Where it is seen, not the room around it to tap.
                                modifier = Modifier.onGloballyPositioned { magnifierLeft = it.boundsInRoot().left },
                            )
                        }
                        IconButton(onClick = { open(Destination.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
                if (ledgerButton) {
                    // In the bar, below the status bar and the camera in it.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .height(TopAppBarDefaults.TopAppBarExpandedHeight),
                    ) {
                        LedgerButton(
                            onClick = { open(Destination.Ledger(hub.ledger.thisMonth())) },
                            modifier = Modifier.width(buttonWidth),
                        )
                    }
                }
            }
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
                day = heuteDay,
                heute = hub.heute,
                contentPadding = contentPadding,
                warnings = {
                    SetupWarning(hub.routines, hub.todos, onCheck = { open(Destination.Status) })
                    BackupWarningCard(hub.nightlyBackups, onOpen = { open(Destination.Backups) })
                },
                onOpenNote = { openNote(NoteEditorTarget.Edit(it)) },
            )
            TopLevelDestination.Routines -> RoutinesScreen(
                routines = hub.routines,
                tags = hub.tags,
                tagFilter = tabs.routineTagFilter,
                onTagFilterChange = { onTabsChange(tabs.copy(routineTagFilter = it)) },
                contentPadding = contentPadding,
                onOpen = { openRoutine(RoutineEditorTarget.Edit(it)) },
                onCreate = { openRoutine(RoutineEditorTarget.New) },
                onOpenArchive = { open(Destination.RoutineArchive) },
                onOpenStatistics = { open(Destination.Statistics) },
            )
            TopLevelDestination.Todos -> TodosScreen(
                todos = hub.todos,
                day = hub.day,
                tags = hub.tags,
                order = tabs.todoOrder,
                onOrderChange = { onTabsChange(tabs.copy(todoOrder = it)) },
                tagFilter = tabs.todoTagFilter,
                onTagFilterChange = { onTabsChange(tabs.copy(todoTagFilter = it)) },
                contentPadding = contentPadding,
                onOpen = { openTodo(TodoEditorTarget.Edit(it)) },
                onCreate = { openTodo(TodoEditorTarget.New) },
                onOpenArchive = { open(Destination.TodoArchive) },
            )
            TopLevelDestination.Wishlist -> WishlistScreen(
                wishes = hub.wishes,
                todos = hub.todos,
                tags = hub.tags,
                tagFilter = tabs.wishTagFilter,
                onTagFilterChange = { onTabsChange(tabs.copy(wishTagFilter = it)) },
                contentPadding = contentPadding,
                onOpen = { openWish(WishEditorTarget.Edit(it)) },
                onOpenTodo = { openTodo(TodoEditorTarget.Edit(it)) },
                onOpenRoutine = { openRoutine(RoutineEditorTarget.Edit(it)) },
                onCreate = { openWish(WishEditorTarget.New()) },
                onOpenReview = { open(Destination.WishReview) },
            )
            TopLevelDestination.Notes -> NotesScreen(
                notes = hub.notes,
                view = tabs.notes,
                onViewChange = { onTabsChange(tabs.copy(notes = it)) },
                contentPadding = contentPadding,
                onOpenNote = { openNote(NoteEditorTarget.Edit(it)) },
                onCreateNote = { openNote(NoteEditorTarget.New(it)) },
                onManageTabs = { open(Destination.NoteTabs) },
                onOpenArchive = { open(Destination.NoteArchive) },
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

/**
 * Places the title so that it ends at [end], in the root's pixels, as far right in its slot or as far left of it as that
 * takes, but never starting left of [minStart]: shortened with "…" if it does not fit.
 */
private fun Modifier.endingAt(end: Float, minStart: Float): Modifier = composed {
    var slotLeft by remember { mutableFloatStateOf(0f) }
    onGloballyPositioned { slotLeft = it.positionInRoot().x }
        .layout { measurable, constraints ->
            val room = (end - minStart).roundToInt().coerceIn(0, constraints.maxWidth)
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = room))
            layout(placeable.width, placeable.height) {
                placeable.place((end - slotLeft - placeable.width).roundToInt(), 0)
            }
        }
}

/** How far the title keeps from the household book's button until the magnifier is placed, and from the screen's edge. */
private val TITLE_GAP = 8.dp
private val TITLE_MIN_START = 4.dp
