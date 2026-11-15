package de.emilyshub.ui.tracking

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.emilyshub.Hub
import de.emilyshub.R
import de.emilyshub.tracking.ACUTE_WARNING_SIGNS
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.tracking.Urgency
import de.emilyshub.tracking.WarningSigns
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.island.IslandGate
import de.emilyshub.ui.island.IslandTexts
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.touchesIsland
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Tracking in the tab of Sonstiges (SPEC.md §17): behind its [IslandGate], as the Doku is, and only once unlocked its
 * [pages] ([onPagesChange]). Each unlock starts on its start page.
 */
@Composable
internal fun TrackingArea(hub: Hub, pages: TrackingPages, onPagesChange: (TrackingPages) -> Unit, contentPadding: PaddingValues) {
    val tracking = hub.tracking
    IslandGate(
        island = tracking,
        texts = TRACKING_TEXTS,
        contentPadding = contentPadding,
        // What was open goes with the lock: the next unlock starts on the start page.
        onLocked = { if (pages != TrackingPages()) onPagesChange(TrackingPages()) },
    ) {
        // Health Connect is read only while Tracking is open (SPEC.md §17): once as it opens, then by "Jetzt lesen".
        LaunchedEffect(tracking) { hub.trackingModules.garmin.read() }
        OpenPage(tracking, hub.trackingModules, pages, onPagesChange, contentPadding)
    }
}

/** The page of the open Tracking on top of [pages]. */
@Composable
private fun OpenPage(tracking: Tracking, modules: TrackingModules, pages: TrackingPages, onPagesChange: (TrackingPages) -> Unit, contentPadding: PaddingValues) {
    val open = { page: TrackingPage -> onPagesChange(pages.open(page)) }
    val back = { onPagesChange(pages.back()) }
    when (val page = pages.top) {
        TrackingPage.Home -> TrackingHome(tracking, modules, contentPadding, onOpen = open)
        is TrackingPage.Morning -> MorningPage(tracking, modules, page, contentPadding, onDone = back)
        is TrackingPage.Evening -> EveningPage(tracking, modules, page, contentPadding, onDone = back)
        TrackingPage.Symptoms -> SymptomsPage(tracking, modules.symptoms, contentPadding)
        TrackingPage.Medications -> MedicationsPage(tracking, modules.medications, contentPadding, onOpen = { open(TrackingPage.Medication(it)) })
        is TrackingPage.Medication -> MedicationPage(tracking, modules, page, contentPadding, onDone = back)
        TrackingPage.BaseData -> BaseDataPage(tracking, modules, contentPadding)
        TrackingPage.Garmin -> GarminPage(tracking, modules.garmin, contentPadding)
        TrackingPage.NewStandTest -> NewStandTestPage(modules.standTests, contentPadding, onStarted = { onPagesChange(pages.replaceTop(TrackingPage.StandTest(it))) })
        is TrackingPage.StandTest -> StandTestPage(tracking, modules, page.id, contentPadding)
        TrackingPage.StandTests -> StandTestsPage(tracking, modules.standTests, contentPadding, onOpen = { open(TrackingPage.StandTest(it)) })
        is TrackingPage.Event -> EventPage(tracking, modules, page, contentPadding, onDone = back)
        TrackingPage.Events -> EventsPage(tracking, modules.events, contentPadding, onOpen = { open(TrackingPage.Event(it)) })
    }
}

/** What Tracking's screens call it and its password. */
internal val TRACKING_TEXTS = IslandTexts(
    setUpTitle = R.string.tracking_set_up_title,
    passwordNew = R.string.tracking_password_new,
    passwordRepeat = R.string.tracking_password_repeat,
    lockedTitle = R.string.tracking_locked_title,
    fingerprintLost = R.string.tracking_fingerprint_lost,
    fingerprintAgainText = R.string.tracking_fingerprint_again_text,
    menu = R.string.tracking_menu,
    changePassword = R.string.tracking_menu_change_password,
)

/**
 * Tracking's start page: first of all "Akute Beschwerden?", then the entries of the day a new one is for, open or done,
 * each to open, and "Nachtragen" for one of the seven days before; "+ Stehtest", "+ Ereignis" and the lists of them.
 */
@Composable
private fun TrackingHome(tracking: Tracking, modules: TrackingModules, contentPadding: PaddingValues, onOpen: (TrackingPage) -> Unit) {
    val entries = modules.entries
    val status = rememberIslandLoad(tracking) { entries.status(entries.suggestedDay()) }
    var backfilling by remember { mutableStateOf(false) }
    var backfillDay by remember { mutableStateOf<LocalDate?>(null) }
    var choosingEvent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            // Outside the scrolling, so that nothing scrolls below the bars.
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        AcuteComplaints()
        if (status != null) {
            Text(
                stringResource(R.string.tracking_entries_of, status.day.numericDate()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    .semantics { heading() },
            )
            EntryLine(R.string.tracking_morning, status.morningDone) { onOpen(TrackingPage.Morning(status.day)) }
            EntryLine(R.string.tracking_evening, status.eveningDone) { onOpen(TrackingPage.Evening(status.day)) }
            OutlinedButton(onClick = { backfilling = true }, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.tracking_backfill))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { onOpen(TrackingPage.NewStandTest) }) { Text(stringResource(R.string.tracking_stand_new)) }
                TextButton(onClick = { onOpen(TrackingPage.StandTests) }) { Text(stringResource(R.string.tracking_stand_list)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { choosingEvent = true }) { Text(stringResource(R.string.tracking_event_new)) }
                TextButton(onClick = { onOpen(TrackingPage.Events) }) { Text(stringResource(R.string.tracking_event_list)) }
            }
        }
    }
    if (choosingEvent) {
        EventKindDialog(
            tracking,
            onChosen = { kind ->
                choosingEvent = false
                scope.launch { modules.events.create(kind)?.let { onOpen(TrackingPage.Event(it)) } }
            },
            onDismiss = { choosingEvent = false },
        )
    }
    if (backfilling) {
        DateDialog(
            initial = entries.suggestedDay().minusDays(1).coerceAtLeast(entries.earliestDay()),
            earliest = entries.earliestDay(),
            latest = entries.today(),
            onConfirm = {
                backfilling = false
                backfillDay = it
            },
            onDismiss = { backfilling = false },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
    backfillDay?.let { day ->
        AlertDialog(
            onDismissRequest = { backfillDay = null },
            title = { Text(stringResource(R.string.tracking_backfill_which, day.numericDate())) },
            confirmButton = {
                TextButton(onClick = {
                    backfillDay = null
                    onOpen(TrackingPage.Evening(day))
                }) { Text(stringResource(R.string.tracking_evening)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    backfillDay = null
                    onOpen(TrackingPage.Morning(day))
                }) { Text(stringResource(R.string.tracking_morning)) }
            },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** An entry of the day, [label]led, said [done] or open; tapped, it opens. */
@Composable
private fun EntryLine(@StringRes label: Int, done: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (done) R.string.tracking_entry_done else R.string.tracking_entry_open),
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Akute Beschwerden?" (SPEC.md §17): first a button that opens the phone app with 112, then the warning signs that
 * call for 112 at once and those that call for a doctor soon, each with their sources; then that medication is changed
 * only with those treating. The same for everyone, whatever is recorded.
 */
@Composable
private fun AcuteComplaints() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.tracking_acute_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            // First, so that it is at hand without reading on.
            CallEmergency()
            ACUTE_WARNING_SIGNS.forEach { WarningSignList(it) }
            Text(stringResource(R.string.tracking_medication_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The [signs] under what they call for, and their sources. */
@Composable
private fun WarningSignList(signs: WarningSigns) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val heading = when (signs.urgency) {
            Urgency.Emergency -> R.string.tracking_acute_emergency
            Urgency.Soon -> R.string.tracking_acute_soon
        }
        Text(stringResource(heading), style = MaterialTheme.typography.titleSmall)
        signs.signs.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Text(
            stringResource(R.string.tracking_acute_sources, signs.sources.joinToString { it.code }),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "112 anrufen": the phone app opens with the number, to call from there; no permission is needed for that. */
@Composable
private fun CallEmergency() {
    val context = LocalContext.current
    Button(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$EMERGENCY_NUMBER".toUri()))
            } catch (e: ActivityNotFoundException) {
                // A phone without a phone app, a tablet say: the number stands on the button.
                Log.w(TAG, "No app to dial with: ${e.javaClass.simpleName}")
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.tracking_call_emergency))
    }
}

/** The emergency number across the EU. */
private const val EMERGENCY_NUMBER = "112"
private const val TAG = "EmilysHub"
