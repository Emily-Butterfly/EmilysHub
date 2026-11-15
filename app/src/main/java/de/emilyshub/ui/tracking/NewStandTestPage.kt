package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.ChecklistItem
import de.emilyshub.tracking.EntryLimits
import de.emilyshub.tracking.StandBefore
import de.emilyshub.tracking.StandChecklist
import de.emilyshub.tracking.StandVariant
import de.emilyshub.tracking.TrackingStandTests
import kotlinx.coroutines.launch

/**
 * A new Stehtest (SPEC.md §17, "Stehtest"): "Stehen" or "Anlehnen" with a short guide and the codes of its sources, the
 * checklist to tick, without all of which it does not start, and what came before. Started, it lies down
 * ([onStarted] with its id).
 */
@Composable
internal fun NewStandTestPage(standTests: TrackingStandTests, contentPadding: PaddingValues, onStarted: (Long) -> Unit) {
    // Not saveable, as all that is typed in Tracking: nothing of it goes into the saved state.
    var variant by remember { mutableStateOf(StandVariant.Standing) }
    var ticked by remember { mutableStateOf(emptySet<ChecklistItem>()) }
    var caffeine by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf("") }
    var fluid by remember { mutableStateOf("") }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val caffeineNumber = TypedNumber(caffeine, EntryLimits.COUNT, tenths = false)
    val mealNumber = TypedNumber(meal, TrackingStandTests.MEAL_MINUTES.first / MINUTES_PER_HOUR_TENTH..TrackingStandTests.MEAL_MINUTES.last / MINUTES_PER_HOUR_TENTH, tenths = true)
    val fluidNumber = TypedNumber(fluid, EntryLimits.FLUID_DECILITRES, tenths = true)
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tracking_stand_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        ChoiceField(
            label = stringResource(R.string.tracking_stand_variant),
            options = listOf(stringResource(R.string.tracking_stand_standing), stringResource(R.string.tracking_stand_leaning)),
            selected = variant.ordinal,
            onSelect = { index -> if (index != null) variant = StandVariant.entries[index] },
        )
        Text(variantText(variant), style = MaterialTheme.typography.bodyMedium)
        EntryHeading(stringResource(R.string.tracking_stand_checklist))
        ChecklistItem.entries.forEach { item ->
            CheckField(stringResource(checklistText(item)), item in ticked, { ticked = if (it) ticked + item else ticked - item })
        }
        EntryHeading(stringResource(R.string.tracking_stand_before))
        NumberField(stringResource(R.string.tracking_stand_caffeine), caffeine, { caffeine = it }, caffeineNumber.problem())
        NumberField(stringResource(R.string.tracking_stand_meal_hours), meal, { meal = it }, mealNumber.problem(), decimals = true)
        NumberField(stringResource(R.string.tracking_stand_fluid), fluid, { fluid = it }, fluidNumber.problem(), decimals = true)
        val complete = StandChecklist.complete(ticked)
        if (!complete) Text(stringResource(R.string.tracking_stand_checklist_missing), style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = {
                starting = true
                scope.launch {
                    val before = StandBefore(caffeineNumber.value, mealNumber.value?.let { it * MINUTES_PER_HOUR_TENTH }, fluidNumber.value)
                    val id = standTests.begin(variant, ticked, before)
                    starting = false
                    if (id != null) onStarted(id)
                }
            },
            enabled = complete && !starting && listOf(caffeineNumber, mealNumber, fluidNumber).none { it.wrong },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.tracking_stand_start)) }
        Text(stringResource(R.string.tracking_stand_disclaimer), style = MaterialTheme.typography.bodySmall)
    }
}

/** The short guide of [variant] with the codes of its sources. */
@Composable
internal fun variantText(variant: StandVariant): String {
    val sources = variant.sources.joinToString { it.code }
    return when (variant) {
        StandVariant.Standing -> stringResource(R.string.tracking_stand_standing_text, sources)
        StandVariant.Leaning -> stringResource(R.string.tracking_stand_leaning_text, sources)
    }
}

/** What [item] of the checklist says. */
private fun checklistText(item: ChecklistItem): Int = when (item) {
    ChecklistItem.SecondPerson -> R.string.tracking_stand_check_second_person
    ChecklistItem.BesideBed -> R.string.tracking_stand_check_beside_bed
    ChecklistItem.NoWarningSigns -> R.string.tracking_stand_check_no_warning_signs
    ChecklistItem.NotAfterHeat -> R.string.tracking_stand_check_not_after_heat
    ChecklistItem.MedicationsAsUsual -> R.string.tracking_stand_check_medications
}

/** Hours are typed with a decimal and kept in minutes: a tenth of an hour is six. */
internal const val MINUTES_PER_HOUR_TENTH = 6
