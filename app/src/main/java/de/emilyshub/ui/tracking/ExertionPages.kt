package de.emilyshub.ui.tracking

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.Certainty
import de.emilyshub.tracking.EntryLimits
import de.emilyshub.tracking.EpisodeKind
import de.emilyshub.tracking.EpisodeSheet
import de.emilyshub.tracking.EventLimits
import de.emilyshub.tracking.Exertion
import de.emilyshub.tracking.ExertionKind
import de.emilyshub.tracking.ExertionOrigin
import de.emilyshub.tracking.ExertionRules
import de.emilyshub.tracking.PacingThreshold
import de.emilyshub.tracking.Suggestion
import de.emilyshub.tracking.SuggestionSource
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingExertions
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.island.KeepIslandDraft
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.touchesIsland
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * An exertion by hand (SPEC.md §17), [page]'s or a new one: date, time if known, kind, duration and strength. Saved as
 * the page closes and before Tracking locks; a new one only once its strength is chosen, and made from an episode, one
 * of its triggers. One from the evening is not changed here: it follows the evening's load.
 */
@Composable
internal fun ExertionPage(tracking: Tracking, modules: TrackingModules, page: TrackingPage.Exertion, contentPadding: PaddingValues, onDone: () -> Unit) {
    val exertions = modules.exertions
    // The exertion stored, or none for a new one, once read.
    var loaded by remember { mutableStateOf<Loaded<Exertion>?>(null) }
    LaunchedEffect(page) {
        val stored = page.id?.let { exertions.get(it) }
        if (page.id != null && stored == null) onDone() else loaded = Loaded(stored)
    }
    val stored = loaded?.value
    if (loaded == null) return
    // A new one has no strength until it is chosen; until then it is not saved.
    val original = stored ?: Exertion(date = page.day ?: modules.entries.suggestedDay(), kind = ExertionKind.Physical, strength = ExertionRules.STRENGTH.first)
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(original.date) }
    var time by remember { mutableStateOf(original.time) }
    var kind by remember { mutableStateOf(original.kind) }
    var duration by remember { mutableStateOf(TypedNumber.textOf(original.durationMinutes)) }
    var strength by remember { mutableStateOf(stored?.strength) }
    var choosingDay by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val durationNumber = TypedNumber(duration, EventLimits.MINUTES, tenths = false)
    val editable = original.origin != ExertionOrigin.Evening
    KeepIslandDraft(tracking, page, modules.writes) {
        val chosen = strength
        val exertion = chosen?.let { original.copy(date = date, time = time, kind = kind, durationMinutes = durationNumber.value, strength = it) }
        if (!editable || deleting || exertion == null || exertion == stored) null else ({ database -> exertions.write(database, exertion, page.triggerOf) })
    }
    EntryPage(
        title = stringResource(R.string.tracking_exertion_title),
        contentPadding = contentPadding,
        doneEnabled = !durationNumber.wrong,
        onDone = onDone,
    ) {
        if (!editable) {
            Text(stringResource(R.string.tracking_exertion_evening_hint), style = MaterialTheme.typography.bodyMedium)
            return@EntryPage
        }
        DateValue(stringResource(R.string.tracking_event_date), date, onClick = { choosingDay = true })
        LabelledValue(
            stringResource(R.string.tracking_exertion_time_none),
            time?.clockTime() ?: stringResource(R.string.tracking_exertion_time_unknown),
            modifier = Modifier.clickable(role = Role.Button) { choosingTime = true },
        )
        ChoiceField(stringResource(R.string.tracking_exertion_kind), ExertionKind.entries.map { stringResource(kindName(it)) }, kind.ordinal) { index ->
            if (index != null) kind = ExertionKind.entries[index]
        }
        NumberField(stringResource(R.string.tracking_event_duration), duration, { duration = it }, durationNumber.problem())
        LevelField(stringResource(R.string.tracking_exertion_strength), strength, { strength = it })
        if (stored != null) OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.tracking_exertion_delete)) }
    }
    if (choosingDay) {
        DateDialog(
            initial = date,
            earliest = LocalDate.of(2000, 1, 1),
            latest = modules.entries.today(),
            onConfirm = {
                choosingDay = false
                date = it
            },
            onDismiss = { choosingDay = false },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
    if (choosingTime) {
        TimeDialog(
            initial = time ?: LocalTime.NOON,
            onConfirm = {
                choosingTime = false
                time = it
            },
            onDismiss = { choosingTime = false },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
    if (deleting) {
        ConfirmDelete(tracking, R.string.tracking_exertion_delete_title, onDismiss = { deleting = false }) {
            scope.launch {
                exertions.delete(original.id)
                onDone()
            }
        }
    }
}

/** All exertions, the latest first; those by hand or from a suggestion to open ([onOpen]). */
@Composable
internal fun ExertionsPage(tracking: Tracking, exertions: TrackingExertions, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val list = rememberIslandLoad(tracking) { exertions.list() } ?: return
    ListPage(R.string.tracking_exertion_list, contentPadding, empty = R.string.tracking_exertion_none.takeIf { list.isEmpty() }) {
        Text(stringResource(R.string.tracking_exertion_evening_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        list.forEach { exertion ->
            val origin = when (exertion.origin) {
                ExertionOrigin.Manual -> null
                ExertionOrigin.Evening -> R.string.tracking_exertion_from_evening
                ExertionOrigin.Steps -> R.string.tracking_exertion_from_steps
                ExertionOrigin.HeartRate -> R.string.tracking_exertion_from_heart_rate
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = exertion.origin != ExertionOrigin.Evening, role = Role.Button) { onOpen(exertion.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(exertionLine(exertion), style = MaterialTheme.typography.bodyLarge)
                origin?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
        }
    }
}

/** The suggestions not decided yet, [only] those of a day if given, each to confirm with a strength or to dismiss. */
@Composable
internal fun Suggestions(tracking: Tracking, exertions: TrackingExertions, only: LocalDate? = null) {
    val all = rememberIslandLoad(tracking) { exertions.suggestions() } ?: return
    val shown = all.filter { only == null || it.date == only }
    if (shown.isEmpty()) return
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { suggestion ->
            SuggestionCard(
                suggestion,
                onConfirm = { strength -> scope.launch { exertions.confirm(suggestion.date, suggestion.source, strength) } },
                onDismiss = { scope.launch { exertions.dismiss(suggestion.date, suggestion.source) } },
            )
        }
        Text(stringResource(R.string.tracking_suggestion_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SuggestionCard(suggestion: Suggestion, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var strength by remember(suggestion) { mutableStateOf<Int?>(null) }
    val mean = String.format(Locale.GERMANY, "%.0f", suggestion.check.mean)
    val text = when (suggestion.source) {
        SuggestionSource.Steps -> R.string.tracking_suggestion_steps
        SuggestionSource.HeartRate -> R.string.tracking_suggestion_heart_rate
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(text, suggestion.date.numericDate(), suggestion.check.value.toInt(), mean))
            LevelField(stringResource(R.string.tracking_exertion_strength), strength, { strength = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { strength?.let(onConfirm) }, enabled = strength != null) { Text(stringResource(R.string.tracking_suggestion_confirm)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tracking_suggestion_dismiss)) }
            }
        }
    }
}

/** "PEM oder Crash?": the kinds of episode to begin one with; chosen, it begins ([onChosen]). */
@Composable
internal fun EpisodeKindDialog(tracking: Tracking, onChosen: (EpisodeKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracking_episode_which)) },
        text = {
            Column {
                EpisodeKind.entries.forEach { kind ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onChosen(kind) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(stringResource(kindName(kind)), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (kind == EpisodeKind.Pem) R.string.tracking_episode_pem_text else R.string.tracking_episode_crash_text),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tracking_event_cancel)) } },
        modifier = Modifier.touchesIsland(tracking),
    )
}

/** The episodes still running, each with "beenden" and to open ([onOpen]). */
@Composable
internal fun RunningEpisodes(tracking: Tracking, modules: TrackingModules, onOpen: (Long) -> Unit) {
    val running = rememberIslandLoad(tracking) { modules.episodes.running() } ?: return
    val scope = rememberCoroutineScope()
    running.forEach { episode ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onOpen(episode.id) }
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.tracking_episode_running_line, stringResource(kindName(episode.kind)), episode.startDate.numericDate()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            TextButton(onClick = { scope.launch { modules.episodes.end(episode.id) } }) { Text(stringResource(R.string.tracking_episode_finish)) }
        }
    }
}

/**
 * Episode [page] (SPEC.md §17): kind, start with its approximate time, end or "läuft noch", severity, and the suspected
 * triggers from the exertions of the three days before, each with how sure and how long after it it began; a new
 * exertion made from here ([onNewTrigger]) is one of them. Saved as the page closes and before Tracking locks.
 */
@Composable
internal fun EpisodePage(
    tracking: Tracking,
    modules: TrackingModules,
    page: TrackingPage.Episode,
    contentPadding: PaddingValues,
    onNewTrigger: (LocalDate) -> Unit,
    onDone: () -> Unit,
) {
    val episodes = modules.episodes
    var loaded by remember { mutableStateOf<EpisodeSheet?>(null) }
    LaunchedEffect(page) {
        val sheet = episodes.sheet(page.id)
        if (sheet == null) onDone() else loaded = sheet
    }
    val sheet = loaded ?: return
    val original = sheet.episode
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(original.kind) }
    var start by remember { mutableStateOf(original.startDate) }
    var startTime by remember { mutableStateOf(original.startTime) }
    var end by remember { mutableStateOf(original.endDate) }
    var severity by remember { mutableStateOf(original.severity) }
    val triggers = remember { mutableStateMapOf<Long, Certainty>().apply { putAll(sheet.triggers) } }
    var choosing by remember { mutableStateOf<Choosing?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val episode = original.copy(kind = kind, startDate = start, startTime = startTime, endDate = end, severity = severity)
    // The exertions to choose from by the start as it stands, read anew when it changes.
    val candidates = rememberIslandLoad(tracking, start, startTime) { episodes.candidates(episode) } ?: sheet.candidates
    KeepIslandDraft(tracking, page, modules.writes) {
        if (deleting || (episode == original && triggers.toMap() == sheet.triggers)) {
            null
        } else {
            val chosen = triggers.toMap()
            ({ database -> episodes.write(database, episode, chosen) })
        }
    }
    EntryPage(title = stringResource(R.string.tracking_episode_title), contentPadding = contentPadding, doneEnabled = true, onDone = onDone) {
        ChoiceField(stringResource(R.string.tracking_episode_kind), EpisodeKind.entries.map { stringResource(kindName(it)) }, kind.ordinal) { index ->
            if (index != null) kind = EpisodeKind.entries[index]
        }
        DateValue(stringResource(R.string.tracking_episode_start), start, onClick = { choosing = Choosing.Start })
        LabelledValue(
            stringResource(R.string.tracking_episode_start_time),
            startTime?.clockTime() ?: stringResource(R.string.tracking_exertion_time_unknown),
            modifier = Modifier.clickable(role = Role.Button) { choosing = Choosing.Time },
        )
        CheckField(stringResource(R.string.tracking_episode_running), end == null, { running -> end = if (running) null else maxOf(start, modules.entries.today()) })
        end?.let { DateValue(stringResource(R.string.tracking_episode_end), it, onClick = { choosing = Choosing.End }) }
        ChoiceField(
            stringResource(R.string.tracking_episode_severity),
            listOf(R.string.tracking_episode_severity_1, R.string.tracking_episode_severity_2, R.string.tracking_episode_severity_3).map { stringResource(it) },
            severity?.let { it - 1 },
        ) { index -> severity = index?.let { it + 1 } }
        EntryHeading(stringResource(R.string.tracking_episode_triggers))
        Text(stringResource(R.string.tracking_episode_triggers_hint), style = MaterialTheme.typography.bodySmall)
        if (candidates.isEmpty()) Text(stringResource(R.string.tracking_episode_no_candidates), style = MaterialTheme.typography.bodyMedium)
        candidates.forEach { (exertion, gap) ->
            val certainty = triggers[exertion.id]
            CheckField(exertionLine(exertion), certainty != null, { on -> if (on) triggers[exertion.id] = Certainty.Unclear else triggers.remove(exertion.id) })
            if (certainty != null) {
                Text(
                    stringResource(if (gap.approximate) R.string.tracking_episode_gap_approximate else R.string.tracking_episode_gap, gap.hours.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
                ChoiceField(
                    exertionLine(exertion),
                    listOf(R.string.tracking_episode_sure, R.string.tracking_episode_likely, R.string.tracking_episode_unclear).map { stringResource(it) },
                    certainty.ordinal,
                ) { index -> if (index != null) triggers[exertion.id] = Certainty.entries[index] }
            }
        }
        OutlinedButton(onClick = { onNewTrigger(start) }) { Text(stringResource(R.string.tracking_episode_new_trigger)) }
        OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.tracking_episode_delete)) }
    }
    when (choosing) {
        Choosing.Start, Choosing.End -> DateDialog(
            initial = if (choosing == Choosing.Start) start else end ?: start,
            earliest = if (choosing == Choosing.Start) LocalDate.of(2000, 1, 1) else start,
            latest = modules.entries.today(),
            onConfirm = {
                if (choosing == Choosing.Start) {
                    start = it
                    end = end?.let { old -> maxOf(old, it) }
                } else {
                    end = it
                }
                choosing = null
            },
            onDismiss = { choosing = null },
            modifier = Modifier.touchesIsland(tracking),
        )
        Choosing.Time -> TimeDialog(
            initial = startTime ?: LocalTime.NOON,
            onConfirm = {
                startTime = it
                choosing = null
            },
            onDismiss = { choosing = null },
            modifier = Modifier.touchesIsland(tracking),
        )
        null -> {}
    }
    if (deleting) {
        ConfirmDelete(tracking, R.string.tracking_episode_delete_title, onDismiss = { deleting = false }) {
            scope.launch {
                episodes.delete(original.id)
                onDone()
            }
        }
    }
}

/** What was read, [value] none if there was nothing: apart from not read yet. */
private class Loaded<T>(val value: T?)

/** Which dialog of an episode's page is open. */
private enum class Choosing { Start, Time, End }

/** All episodes, the latest first, each to open ([onOpen]). */
@Composable
internal fun EpisodesPage(tracking: Tracking, modules: TrackingModules, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val list = rememberIslandLoad(tracking) { modules.episodes.list() } ?: return
    ListPage(R.string.tracking_episode_list, contentPadding, empty = R.string.tracking_episode_none.takeIf { list.isEmpty() }) {
        list.forEach { episode ->
            Text(
                stringResource(
                    R.string.tracking_episode_line,
                    stringResource(kindName(episode.kind)),
                    episode.startDate.numericDate(),
                    episode.endDate?.numericDate() ?: stringResource(R.string.tracking_episode_running),
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(episode.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }
    }
}

/**
 * The pacing threshold (SPEC.md §17): one's own value, else the rule of thumb, marked as weakly backed, with the hint on
 * pulse-lowering medication where it applies; saved as the page closes.
 */
@Composable
internal fun PacingPage(tracking: Tracking, modules: TrackingModules, contentPadding: PaddingValues, onDone: () -> Unit) {
    val exertions = modules.exertions
    val view = rememberIslandLoad(tracking) { exertions.threshold() } ?: return
    var own by remember { mutableStateOf<String?>(null) }
    val typed = own ?: TypedNumber.textOf(view.own)
    val ownNumber = TypedNumber(typed, EntryLimits.PULSE, tenths = false)
    KeepIslandDraft(tracking, TrackingPage.Pacing, modules.writes) {
        if (own == null || ownNumber.wrong || ownNumber.value == view.own) null else ({ database -> exertions.writeOwnThreshold(database, ownNumber.value) })
    }
    EntryPage(title = stringResource(R.string.tracking_pacing_title), contentPadding = contentPadding, doneEnabled = !ownNumber.wrong, onDone = onDone) {
        Text(stringResource(R.string.tracking_pacing_text), style = MaterialTheme.typography.bodyMedium)
        NumberField(stringResource(R.string.tracking_pacing_own), typed, { own = it }, ownNumber.problem())
        val threshold = view.threshold
        if (threshold == null) {
            Text(stringResource(R.string.tracking_pacing_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.tracking_pacing_in_force, threshold.bpm), style = MaterialTheme.typography.titleMedium)
            if (!threshold.own) Text(stringResource(R.string.tracking_pacing_rule_of_thumb), style = MaterialTheme.typography.bodyMedium)
        }
        if (view.warnsOfPulseLowering) {
            Text(stringResource(R.string.tracking_pacing_pulse_lowering), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.tracking_pacing_sources, PacingThreshold.PULSE_LOWERING_SOURCE.code), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.tracking_pacing_sources, PacingThreshold.SOURCES.joinToString { it.code }), style = MaterialTheme.typography.bodySmall)
    }
}

/** A page of a list under its [title], with [empty] said if nothing is in it. */
@Composable
private fun ListPage(@StringRes title: Int, contentPadding: PaddingValues, @StringRes empty: Int?, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(16.dp)
                .semantics { heading() },
        )
        empty?.let { Text(stringResource(it), modifier = Modifier.padding(horizontal = 16.dp)) }
        content()
    }
}

/** Asks, under [title], before something is deleted for good ([onConfirm]). */
@Composable
private fun ConfirmDelete(tracking: Tracking, @StringRes title: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.tracking_event_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tracking_event_cancel)) } },
        modifier = Modifier.touchesIsland(tracking),
    )
}

/** An exertion in a line: its date, kind and strength. */
@Composable
internal fun exertionLine(exertion: Exertion): String =
    stringResource(R.string.tracking_exertion_line, exertion.date.numericDate(), stringResource(kindName(exertion.kind)), exertion.strength)

@StringRes
internal fun kindName(kind: ExertionKind): Int = when (kind) {
    ExertionKind.Physical -> R.string.tracking_exertion_physical
    ExertionKind.Cognitive -> R.string.tracking_exertion_cognitive
    ExertionKind.Emotional -> R.string.tracking_exertion_emotional
    ExertionKind.Social -> R.string.tracking_exertion_social
    ExertionKind.StandingHeat -> R.string.tracking_exertion_standing_heat
}

@StringRes
internal fun kindName(kind: EpisodeKind): Int = when (kind) {
    EpisodeKind.Pem -> R.string.tracking_episode_pem
    EpisodeKind.Crash -> R.string.tracking_episode_crash
}
