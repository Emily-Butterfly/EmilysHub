package de.emilyshub.ui.tracking

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import de.emilyshub.tracking.EntryLimits
import de.emilyshub.tracking.EventKind
import de.emilyshub.tracking.EventLimits
import de.emilyshub.tracking.EventPulse
import de.emilyshub.tracking.EventTimer
import de.emilyshub.tracking.Posture
import de.emilyshub.tracking.SkinArea
import de.emilyshub.tracking.SkinPattern
import de.emilyshub.tracking.Tier
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingEvent
import de.emilyshub.tracking.TrackingEvents
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.tracking.WaterTemperature
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.dateAndTime
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.island.KeepIslandDraft
import de.emilyshub.ui.island.WhileMeasuring
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.touchesIsland
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** "Was war?": the kinds of event to begin one with; chosen, it is created now ([onChosen]). */
@Composable
internal fun EventKindDialog(tracking: Tracking, onChosen: (EventKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracking_event_which)) },
        text = {
            Column {
                EventKind.entries.forEach { kind ->
                    Text(
                        stringResource(kindName(kind)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onChosen(kind) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tracking_event_cancel)) } },
        modifier = Modifier.touchesIsland(tracking),
    )
}

/**
 * Event [page] (SPEC.md §17, "Ereignisse"): all its fields, saved as they stand as the page closes and before Tracking
 * locks, to be filled in later as well; "Vorbei – Timer starten" starts the pulse timer, which while it runs keeps the
 * screen on and the five minutes resting, with a tone and a vibration as each pulse is due. Garmin's heart rate around
 * it with its tiers, the intakes before it, and deleting it after asking.
 */
@Composable
internal fun EventPage(tracking: Tracking, modules: TrackingModules, page: TrackingPage.Event, contentPadding: PaddingValues, onDone: () -> Unit) {
    val events = modules.events
    var loaded by remember { mutableStateOf<TrackingEvent?>(null) }
    LaunchedEffect(page) {
        val event = events.get(page.id)
        if (event == null) onDone() else loaded = event
    }
    val first = loaded ?: return
    EventForm(tracking, modules, events, first, contentPadding, onDone)
}

@Composable
private fun EventForm(tracking: Tracking, modules: TrackingModules, events: TrackingEvents, first: TrackingEvent, contentPadding: PaddingValues, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val zone = events.zone()
    // Not saveable, as all that is typed in Tracking: nothing of it goes into the saved state.
    var saved by remember { mutableStateOf(first) }
    var kind by remember { mutableStateOf(first.kind) }
    var at by remember { mutableStateOf(first.at.atZone(zone)) }
    var water by remember { mutableStateOf(first.water) }
    var duration by remember { mutableStateOf(TypedNumber.textOf(first.durationMinutes)) }
    var posture by remember { mutableStateOf(first.posture) }
    var pulseBefore by remember { mutableStateOf(TypedNumber.textOf(first.pulseBefore)) }
    var endedAt by remember { mutableStateOf(first.endedAt) }
    var pulseAfter by remember { mutableStateOf(TypedNumber.textOf(first.pulseAfter)) }
    var pulseFive by remember { mutableStateOf(TypedNumber.textOf(first.pulseFive)) }
    var pulseTen by remember { mutableStateOf(TypedNumber.textOf(first.pulseTen)) }
    var spo2 by remember { mutableStateOf(TypedNumber.textOf(first.spo2)) }
    var breathlessness by remember { mutableStateOf(first.breathlessness) }
    var dizziness by remember { mutableStateOf(first.dizziness) }
    var satDown by remember { mutableStateOf(first.satDown) }
    var satDownAfter by remember { mutableStateOf(TypedNumber.textOf(first.satDownAfterMinutes)) }
    var skinAreas by remember { mutableStateOf(first.skinAreas) }
    var skinPattern by remember { mutableStateOf(first.skinPattern) }
    var skinPainful by remember { mutableStateOf(first.skinPainful) }
    var skinFadesLying by remember { mutableStateOf(first.skinFadesLying) }
    var skinFade by remember { mutableStateOf(TypedNumber.textOf(first.skinFadeMinutes)) }
    var recovered by remember { mutableStateOf(TypedNumber.textOf(first.recoveredMinutes)) }
    var deleting by remember { mutableStateOf(false) }
    var choosingDay by remember { mutableStateOf(false) }
    var choosingTime by remember { mutableStateOf(false) }
    val numbers = mapOf(
        "duration" to TypedNumber(duration, EventLimits.MINUTES, tenths = false),
        "before" to TypedNumber(pulseBefore, EntryLimits.PULSE, tenths = false),
        "after" to TypedNumber(pulseAfter, EntryLimits.PULSE, tenths = false),
        "five" to TypedNumber(pulseFive, EntryLimits.PULSE, tenths = false),
        "ten" to TypedNumber(pulseTen, EntryLimits.PULSE, tenths = false),
        "spo2" to TypedNumber(spo2, EventLimits.SPO2, tenths = false),
        "satDown" to TypedNumber(satDownAfter, EventLimits.MINUTES, tenths = false),
        "fade" to TypedNumber(skinFade, EventLimits.MINUTES, tenths = false),
        "recovered" to TypedNumber(recovered, EventLimits.MINUTES, tenths = false),
    )
    val event = saved.copy(
        kind = kind,
        at = at.toInstant(),
        water = water.takeIf { kind.water },
        durationMinutes = numbers.getValue("duration").value,
        posture = posture,
        pulseBefore = numbers.getValue("before").value,
        endedAt = endedAt,
        pulseAfter = numbers.getValue("after").value,
        pulseFive = numbers.getValue("five").value,
        pulseTen = numbers.getValue("ten").value,
        spo2 = numbers.getValue("spo2").value,
        breathlessness = breathlessness,
        dizziness = dizziness,
        satDown = satDown,
        satDownAfterMinutes = numbers.getValue("satDown").value.takeIf { satDown == true },
        skinAreas = skinAreas,
        skinPattern = skinPattern,
        skinPainful = skinPainful,
        skinFadesLying = skinFadesLying,
        skinFadeMinutes = numbers.getValue("fade").value,
        recoveredMinutes = numbers.getValue("recovered").value,
    )
    KeepIslandDraft(tracking, first.id, modules.writes) {
        if (event == saved || deleting) null else ({ database -> events.write(database, event) })
    }
    // The end moves with the time, so that the timer's pulses and the curve stay where they were measured.
    val moveTo = { moved: ZonedDateTime ->
        endedAt = endedAt?.plus(Duration.between(at.toInstant(), moved.toInstant()))
        at = moved
    }
    val now = rememberNow(events::now)
    val running = EventTimer.runs(event, now)
    if (running) EventTimerSignals(tracking, event, now)
    EntryPage(
        title = stringResource(R.string.tracking_event_title),
        contentPadding = contentPadding,
        // Wrong numbers stay to be put right; leaving otherwise saves all but them.
        doneEnabled = numbers.values.none { it.wrong },
        onDone = onDone,
    ) {
        ChoiceField(stringResource(R.string.tracking_event_kind), EventKind.entries.map { stringResource(kindName(it)) }, kind.ordinal) { index ->
            if (index != null) kind = EventKind.entries[index]
        }
        DateValue(stringResource(R.string.tracking_event_date), at.toLocalDate(), onClick = { choosingDay = true })
        LabelledValue(
            stringResource(R.string.tracking_event_time),
            at.clockTime(),
            modifier = Modifier.clickable(role = Role.Button) { choosingTime = true },
        )
        if (kind.water) {
            val temperatures = listOf(R.string.tracking_event_lukewarm, R.string.tracking_event_warm, R.string.tracking_event_hot)
            ChoiceField(stringResource(R.string.tracking_event_water), temperatures.map { stringResource(it) }, water?.ordinal) { index ->
                water = index?.let { WaterTemperature.entries[it] }
            }
        }
        NumberField(stringResource(R.string.tracking_event_duration), duration, { duration = it }, numbers.getValue("duration").problem())
        ChoiceField(
            stringResource(R.string.tracking_event_posture),
            listOf(stringResource(R.string.tracking_event_standing), stringResource(R.string.tracking_event_sitting)),
            posture?.ordinal,
        ) { index -> posture = index?.let { Posture.entries[it] } }
        NumberField(stringResource(R.string.tracking_event_pulse_before), pulseBefore, { pulseBefore = it }, numbers.getValue("before").problem())
        val ended = endedAt
        if (ended == null) {
            Button(
                onClick = {
                    scope.launch {
                        // What was typed so far first, then the end: the timer counts from now.
                        events.save(event)
                        events.end(event.id)
                        events.get(event.id)?.let {
                            saved = it
                            endedAt = it.endedAt
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tracking_event_end)) }
        } else {
            Text(stringResource(R.string.tracking_event_ended, ended.atZone(zone).clockTime()), style = MaterialTheme.typography.bodyMedium)
            if (running) TimerLine(event, ended, now)
        }
        NumberField(stringResource(R.string.tracking_event_pulse_after), pulseAfter, { pulseAfter = it }, numbers.getValue("after").problem())
        NumberField(stringResource(R.string.tracking_event_pulse_five), pulseFive, { pulseFive = it }, numbers.getValue("five").problem())
        NumberField(stringResource(R.string.tracking_event_pulse_ten), pulseTen, { pulseTen = it }, numbers.getValue("ten").problem())
        EntryHeading(stringResource(R.string.tracking_event_complaints))
        NumberField(stringResource(R.string.tracking_event_spo2), spo2, { spo2 = it }, numbers.getValue("spo2").problem())
        LevelField(stringResource(R.string.tracking_stand_breathlessness), breathlessness, { breathlessness = it })
        LevelField(stringResource(R.string.tracking_stand_dizziness), dizziness, { dizziness = it })
        YesNoField(stringResource(R.string.tracking_event_sat_down), satDown, { satDown = it })
        if (satDown == true) {
            NumberField(stringResource(R.string.tracking_event_sat_down_after), satDownAfter, { satDownAfter = it }, numbers.getValue("satDown").problem())
        }
        EntryHeading(stringResource(R.string.tracking_event_skin))
        Text(stringResource(R.string.tracking_event_skin_where), style = MaterialTheme.typography.bodyLarge)
        SkinArea.entries.forEach { area ->
            CheckField(stringResource(areaName(area)), area in skinAreas, { skinAreas = if (it) skinAreas + area else skinAreas - area })
        }
        ChoiceField(
            stringResource(R.string.tracking_event_skin_pattern),
            listOf(stringResource(R.string.tracking_event_skin_even), stringResource(R.string.tracking_event_skin_reticular)),
            skinPattern?.ordinal,
        ) { index -> skinPattern = index?.let { SkinPattern.entries[it] } }
        YesNoField(stringResource(R.string.tracking_event_skin_painful), skinPainful, { skinPainful = it })
        YesNoField(stringResource(R.string.tracking_event_skin_fades_lying), skinFadesLying, { skinFadesLying = it })
        NumberField(stringResource(R.string.tracking_event_skin_fade_minutes), skinFade, { skinFade = it }, numbers.getValue("fade").problem())
        NumberField(stringResource(R.string.tracking_event_recovered), recovered, { recovered = it }, numbers.getValue("recovered").problem())
        Text(stringResource(R.string.tracking_event_later_hint), style = MaterialTheme.typography.bodySmall)
        EventAround(tracking, events, event.id)
        OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.tracking_event_delete)) }
    }
    if (choosingDay) {
        DateDialog(
            initial = at.toLocalDate(),
            earliest = LocalDate.of(2000, 1, 1),
            latest = ZonedDateTime.ofInstant(now, zone).toLocalDate(),
            onConfirm = {
                choosingDay = false
                moveTo(at.with(it))
            },
            onDismiss = { choosingDay = false },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
    if (choosingTime) {
        TimeDialog(
            initial = at.toLocalTime(),
            onConfirm = {
                choosingTime = false
                moveTo(at.with(it))
            },
            onDismiss = { choosingTime = false },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.tracking_event_delete_title)) },
            text = { Text(stringResource(R.string.tracking_event_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        events.delete(event.id)
                        onDone()
                    }
                }) { Text(stringResource(R.string.tracking_event_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.tracking_event_cancel)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** While the timer runs: the measuring assistant's exception, and a tone and a vibration as the 5 and 10 minutes come. */
@Composable
private fun EventTimerSignals(tracking: Tracking, event: TrackingEvent, now: Instant) {
    WhileMeasuring(tracking)
    val signal = rememberMeasureSignal()
    val ended = event.endedAt ?: return
    val five = EventTimer.isDue(ended, EventPulse.Five, now)
    val ten = EventTimer.isDue(ended, EventPulse.Ten, now)
    // Only as each comes due while the page shows: reopened after, it does not sound again.
    val fiveAtOpen = remember { five }
    val tenAtOpen = remember { ten }
    LaunchedEffect(five) { if (five && !fiveAtOpen) signal.play() }
    LaunchedEffect(ten) { if (ten && !tenAtOpen) signal.play() }
}

/** The pulse to measure next and how long until it is due. */
@Composable
private fun TimerLine(event: TrackingEvent, ended: Instant, now: Instant) {
    val next = EventTimer.next(event) ?: return
    val label = stringResource(pulseName(next))
    val left = Duration.between(now, EventTimer.dueAt(ended, next))
    Text(
        if (left.isPositive) stringResource(R.string.tracking_event_timer_next, label, clock(left)) else stringResource(R.string.tracking_event_timer_now, label),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Garmin's heart rate around the event, by tier, and the intakes before it. */
@Composable
private fun EventAround(tracking: Tracking, events: TrackingEvents, id: Long) {
    val report = rememberIslandLoad(tracking, id) { events.report(id) } ?: return
    report.intakes.forEach { (medication, minutes) ->
        Text(stringResource(R.string.tracking_stand_intake, medication.name, hours(minutes)), style = MaterialTheme.typography.bodyMedium)
    }
    if (report.curve.isEmpty()) return
    Text(stringResource(R.string.tracking_event_curve), style = MaterialTheme.typography.titleSmall)
    HeartRateCurve(
        report.curve.map { it.at to it.bpm },
        mark = report.event.endedAt,
        dotted = report.curve.indices.filter { report.curve[it].tier == Tier.B }.toSet(),
    )
    Text(
        stringResource(R.string.tracking_event_curve_tiers, report.curve.count { it.tier == Tier.A }, report.curve.count { it.tier == Tier.B }),
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * All events (SPEC.md §17), the latest first: when and what; each to open ([onOpen]).
 */
@Composable
internal fun EventsPage(tracking: Tracking, events: TrackingEvents, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val list = rememberIslandLoad(tracking) { events.list() } ?: return
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.tracking_event_list),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(16.dp)
                .semantics { heading() },
        )
        if (list.isEmpty()) Text(stringResource(R.string.tracking_event_none), modifier = Modifier.padding(horizontal = 16.dp))
        list.forEach { event ->
            Text(
                stringResource(R.string.tracking_event_line, event.at.atZone(events.zone()).dateAndTime(), stringResource(kindName(event.kind))),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(event.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }
    }
}

@StringRes
internal fun kindName(kind: EventKind): Int = when (kind) {
    EventKind.Shower -> R.string.tracking_event_shower
    EventKind.Bath -> R.string.tracking_event_bath
    EventKind.Sauna -> R.string.tracking_event_sauna
    EventKind.Heat -> R.string.tracking_event_heat
    EventKind.LongStanding -> R.string.tracking_event_long_standing
    EventKind.Other -> R.string.tracking_event_other
}

@StringRes
private fun areaName(area: SkinArea): Int = when (area) {
    SkinArea.Legs -> R.string.tracking_event_skin_legs
    SkinArea.Arms -> R.string.tracking_event_skin_arms
    SkinArea.Trunk -> R.string.tracking_event_skin_trunk
    SkinArea.FingersToes -> R.string.tracking_event_skin_fingers_toes
}

@StringRes
private fun pulseName(pulse: EventPulse): Int = when (pulse) {
    EventPulse.After -> R.string.tracking_event_after_label
    EventPulse.Five -> R.string.tracking_event_five_label
    EventPulse.Ten -> R.string.tracking_event_ten_label
}
