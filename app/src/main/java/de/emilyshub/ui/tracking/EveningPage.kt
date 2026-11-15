package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.emilyshub.R
import de.emilyshub.tracking.Deviation
import de.emilyshub.tracking.EntryKind
import de.emilyshub.tracking.EntryLimits
import de.emilyshub.tracking.EveningEntry
import de.emilyshub.tracking.EveningSheet
import de.emilyshub.tracking.FollowUpAnswer
import de.emilyshub.tracking.IntakeDeviation
import de.emilyshub.tracking.Medication
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingEntries
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.island.KeepIslandDraft
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * The evening entry of [page]'s day (SPEC.md §17, "Abends"): each symptom asked, the load of the day, what kind of day
 * it was, what was drunk, eaten, how many coffees and drinks, each medication of the day "wie geplant" unless chosen
 * otherwise, and a note. Saved as it stands, also unfinished, as the page closes and before Tracking locks.
 */
@Composable
internal fun EveningPage(
    tracking: Tracking,
    modules: TrackingModules,
    page: TrackingPage.Evening,
    contentPadding: PaddingValues,
    onOpen: (TrackingPage) -> Unit,
    onDone: () -> Unit,
) {
    var loaded by remember { mutableStateOf<EveningSheet?>(null) }
    LaunchedEffect(page) {
        val sheet = modules.entries.evening(page.day)
        if (sheet == null) onDone() else loaded = sheet
    }
    val sheet = loaded ?: return
    val original = sheet.entry
    // Not saveable, as all that is typed in Tracking: nothing of it goes into the saved state.
    var day by remember { mutableStateOf(original.date) }
    val levels = remember { mutableStateMapOf<Long, Int>().apply { putAll(sheet.levels) } }
    val deviations = remember { mutableStateMapOf<Long, IntakeDeviation>().apply { putAll(sheet.deviations) } }
    val answers = remember { mutableStateMapOf<Long, FollowUpAnswer>().apply { putAll(sheet.followUpAnswers) } }
    var choosingEpisode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var loadPhysical by remember { mutableStateOf(original.loadPhysical) }
    var loadCognitive by remember { mutableStateOf(original.loadCognitive) }
    var loadEmotional by remember { mutableStateOf(original.loadEmotional) }
    var workday by remember { mutableStateOf(original.workday) }
    var heat by remember { mutableStateOf(original.heatOrStanding) }
    var fluid by remember { mutableStateOf(TypedNumber.textOf(original.fluidDecilitres, tenths = true)) }
    var meals by remember { mutableStateOf(TypedNumber.textOf(original.meals)) }
    var caffeine by remember { mutableStateOf(TypedNumber.textOf(original.caffeine)) }
    var alcohol by remember { mutableStateOf(TypedNumber.textOf(original.alcohol)) }
    var note by remember { mutableStateOf(original.note) }
    val fluidNumber = TypedNumber(fluid, EntryLimits.FLUID_DECILITRES, tenths = true)
    val mealsNumber = TypedNumber(meals, EntryLimits.COUNT, tenths = false)
    val caffeineNumber = TypedNumber(caffeine, EntryLimits.COUNT, tenths = false)
    val alcoholNumber = TypedNumber(alcohol, EntryLimits.COUNT, tenths = false)
    val entry = EveningEntry(
        date = day,
        loadPhysical = loadPhysical,
        loadCognitive = loadCognitive,
        loadEmotional = loadEmotional,
        workday = workday,
        heatOrStanding = heat,
        fluidDecilitres = fluidNumber.value,
        meals = mealsNumber.value,
        caffeine = caffeineNumber.value,
        alcohol = alcoholNumber.value,
        note = note,
        recordedOn = original.recordedOn,
    )
    KeepIslandDraft(tracking, page, modules.writes) {
        val unchanged = entry == original && levels.toMap() == sheet.levels && deviations.toMap() == sheet.deviations &&
            answers.toMap() == sheet.followUpAnswers
        if (unchanged) {
            null
        } else {
            val toWrite = levels.toMap()
            val otherwise = deviations.values.toList()
            val answered = answers.toMap()
            ({ database -> modules.entries.writeEvening(database, entry, toWrite, otherwise, original.date.takeIf { sheet.stored }, answered) })
        }
    }
    EntryPage(
        title = stringResource(R.string.tracking_evening),
        contentPadding = contentPadding,
        doneEnabled = listOf(fluidNumber, mealsNumber, caffeineNumber, alcoholNumber).none { it.wrong },
        onDone = onDone,
    ) {
        EntryDayField(modules, EntryKind.Evening, day, original.date.takeIf { sheet.stored }, onChange = { day = it })
        EntryHeading(stringResource(R.string.tracking_symptoms_heading))
        sheet.symptoms.forEach { symptom ->
            LevelField(symptom.name, levels[symptom.id], { level -> if (level == null) levels.remove(symptom.id) else levels[symptom.id] = level })
        }
        EntryHeading(stringResource(R.string.tracking_load_heading))
        LevelField(stringResource(R.string.tracking_load_physical), loadPhysical, { loadPhysical = it })
        LevelField(stringResource(R.string.tracking_load_cognitive), loadCognitive, { loadCognitive = it })
        LevelField(stringResource(R.string.tracking_load_emotional), loadEmotional, { loadEmotional = it })
        Suggestions(tracking, modules.exertions, only = day)
        // Those of the day it was opened for: a day changed asks after others once saved.
        if (sheet.followUps.isNotEmpty()) {
            EntryHeading(stringResource(R.string.tracking_follow_up_heading))
            sheet.followUps.forEach { exertion ->
                ChoiceField(
                    stringResource(R.string.tracking_follow_up_question, exertion.date.numericDate(), stringResource(kindName(exertion.kind)), exertion.strength),
                    listOf(stringResource(R.string.tracking_yes), stringResource(R.string.tracking_no), stringResource(R.string.tracking_follow_up_unclear)),
                    answers[exertion.id]?.ordinal,
                ) { index -> if (index == null) answers.remove(exertion.id) else answers[exertion.id] = FollowUpAnswer.entries[index] }
            }
        }
        OutlinedButton(onClick = { choosingEpisode = true }) { Text(stringResource(R.string.tracking_crash_or_pem)) }
        EntryHeading(stringResource(R.string.tracking_day_heading))
        YesNoField(stringResource(R.string.tracking_workday), workday, { workday = it })
        YesNoField(stringResource(R.string.tracking_heat), heat, { heat = it })
        NumberField(stringResource(R.string.tracking_fluid), fluid, { fluid = it }, fluidNumber.problem(), decimals = true)
        NumberField(stringResource(R.string.tracking_meals), meals, { meals = it }, mealsNumber.problem())
        NumberField(stringResource(R.string.tracking_caffeine), caffeine, { caffeine = it }, caffeineNumber.problem())
        NumberField(stringResource(R.string.tracking_alcohol), alcohol, { alcohol = it }, alcoholNumber.problem())
        // Those of the day it is for now: a day changed may take others.
        val medications = sheet.takenOn(day)
        if (medications.isNotEmpty()) {
            EntryHeading(stringResource(R.string.tracking_intakes_heading))
            Text(stringResource(R.string.tracking_medication_hint), style = MaterialTheme.typography.bodySmall)
            medications.forEach { medication ->
                IntakeField(medication, day, deviations[medication.id]) { deviation ->
                    if (deviation == null) deviations.remove(medication.id) else deviations[medication.id] = deviation
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { typed -> if (typed.length <= NOTE_MAX) note = typed },
            label = { Text(stringResource(R.string.tracking_note)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (choosingEpisode) {
        EpisodeKindDialog(
            tracking,
            onChosen = { kind ->
                choosingEpisode = false
                scope.launch { modules.episodes.create(kind, day)?.let { onOpen(TrackingPage.Episode(it)) } }
            },
            onDismiss = { choosingEpisode = false },
        )
    }
}

/**
 * How [medication] was taken on [day]: "wie geplant" unless [deviation] says otherwise, at another time, which the time
 * dialog asks for, or forgotten.
 */
@Composable
private fun IntakeField(medication: Medication, day: LocalDate, deviation: IntakeDeviation?, onChange: (IntakeDeviation?) -> Unit) {
    var askingTime by remember { mutableStateOf(false) }
    val atTime = deviation?.time?.let { stringResource(R.string.tracking_intake_at, it.clockTime()) }
    Column {
        ChoiceField(
            label = medication.name,
            options = listOf(
                stringResource(R.string.tracking_intake_planned),
                atTime ?: stringResource(R.string.tracking_intake_other_time),
                stringResource(R.string.tracking_intake_forgotten),
            ),
            selected = when (deviation?.deviation) {
                null -> 0
                Deviation.OtherTime -> 1
                Deviation.Forgotten -> 2
            },
            onSelect = { choice ->
                when (choice) {
                    1 -> askingTime = true
                    2 -> onChange(TrackingEntries.deviation(day, medication.id, null))
                    // "Wie geplant", also when the choice is taken back: there is nothing else to be.
                    else -> onChange(null)
                }
            },
        )
    }
    if (askingTime) {
        TimeDialog(
            initial = deviation?.time ?: medication.times.firstOrNull() ?: LocalTime.NOON,
            onConfirm = { time ->
                askingTime = false
                onChange(TrackingEntries.deviation(day, medication.id, time))
            },
            onDismiss = { askingTime = false },
        )
    }
}

/** The longest an evening's note can be. */
private const val NOTE_MAX = 10_000
