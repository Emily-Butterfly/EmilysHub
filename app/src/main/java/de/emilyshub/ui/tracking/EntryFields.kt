package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.EntryKind
import de.emilyshub.tracking.TrackingEntries
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import java.time.LocalDate

/** The words of the scale 0–3 for symptoms and load (SPEC.md §17): 0 nicht vorhanden … 3 schwer. */
internal val LEVEL_WORDS = listOf(R.string.tracking_level_0, R.string.tracking_level_1, R.string.tracking_level_2, R.string.tracking_level_3)

/** The words of the sleep's quality 0–3, 0 being the best. */
internal val SLEEP_WORDS = listOf(R.string.tracking_sleep_0, R.string.tracking_sleep_1, R.string.tracking_sleep_2, R.string.tracking_sleep_3)

/** A heading within an entry. */
@Composable
internal fun EntryHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/**
 * [label] with one of [options] to choose, as words, not only numbers; none chosen is "nicht erfasst", and the one
 * chosen tapped again is taken back. Each reads "label: option", so that a screen reader says what it is for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceField(label: String, options: List<String>, selected: Int?, onSelect: (Int?) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val description = stringResource(R.string.tracking_choice, label, option)
                FilterChip(
                    selected = selected == index,
                    onClick = { onSelect(if (selected == index) null else index) },
                    label = { Text(option) },
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
    }
}

/** [label] on the scale 0–3 in words ([words]); none is "nicht erfasst", never 0. */
@Composable
internal fun LevelField(label: String, value: Int?, onChange: (Int?) -> Unit, words: List<Int> = LEVEL_WORDS) {
    ChoiceField(label, words.map { stringResource(it) }, value, onChange)
}

/** [label] answered yes or no, or not at all. */
@Composable
internal fun YesNoField(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    ChoiceField(
        label = label,
        options = listOf(stringResource(R.string.tracking_yes), stringResource(R.string.tracking_no)),
        selected = when (value) {
            true -> 0
            false -> 1
            null -> null
        },
        onSelect = { onChange(it?.let { index -> index == 0 }) },
    )
}

/** [label] ticked or not, as for what is the exception, such as a pulse measured after coffee. */
@Composable
internal fun CheckField(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The [day] an entry of [kind] is for, to change to another within reach without one of its kind ([TrackingEntries.takenDays]);
 * [own] is the day it was saved for, if it was.
 */
@Composable
internal fun EntryDayField(modules: TrackingModules, kind: EntryKind, day: LocalDate, own: LocalDate?, onChange: (LocalDate) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    DateValue(stringResource(R.string.tracking_entry_day), day, onClick = { choosing = true })
    if (choosing) {
        val taken = rememberTaken(modules, kind) ?: return
        DateDialog(
            initial = day,
            earliest = modules.entries.earliestDay(),
            latest = modules.entries.today(),
            selectable = { it == own || it == day || it !in taken },
            onConfirm = {
                choosing = false
                onChange(it)
            },
            onDismiss = { choosing = false },
        )
    }
}

/** The days that have an entry of [kind], once read. */
@Composable
private fun rememberTaken(modules: TrackingModules, kind: EntryKind): Set<LocalDate>? {
    var taken by remember { mutableStateOf<Set<LocalDate>?>(null) }
    LaunchedEffect(kind) { taken = modules.entries.takenDays(kind) }
    return taken
}

/**
 * A number typed as [text] under [label], whole or with a decimal comma if [decimals]; its [problem] below, if any.
 * Left empty, it is "nicht erfasst".
 */
@Composable
internal fun NumberField(label: String, text: String, onChange: (String) -> Unit, problem: String?, decimals: Boolean = false) {
    OutlinedTextField(
        value = text,
        onValueChange = { typed -> if (typed.length <= NUMBER_MAX_LENGTH) onChange(typed) },
        label = { Text(label) },
        singleLine = true,
        isError = problem != null,
        supportingText = problem?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A number as typed, kept apart from what it reads as, so that a wrong one is shown, not lost. */
internal class TypedNumber(val text: String, private val range: IntRange, private val tenths: Boolean) {
    /** What it reads as, in tenths if so; null when empty, not a number or outside its range. */
    val value: Int? get() = parsed()?.takeIf { it in range }

    /** Whether it reads as nothing that could be saved, though something is typed. */
    val wrong: Boolean get() = text.isNotBlank() && value == null

    /** What is wrong with it, said as the field shows it; none when empty or fine. */
    @Composable
    fun problem(): String? = when {
        text.isBlank() || value != null -> null
        parsed() == null -> stringResource(R.string.tracking_not_a_number)
        else -> stringResource(R.string.tracking_out_of_range, show(range.first), show(range.last))
    }

    private fun parsed(): Int? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        return if (tenths) cleaned.toBigDecimalOrNull()?.movePointRight(1)?.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toInt() else cleaned.toIntOrNull()
    }

    private fun show(value: Int) = if (tenths) "%d,%d".format(value / 10, value % 10) else value.toString()

    companion object {
        /** [value] as typed in a field that reads it in tenths if [tenths]. */
        fun textOf(value: Int?, tenths: Boolean = false): String = when {
            value == null -> ""
            tenths -> if (value % 10 == 0) (value / 10).toString() else "%d,%d".format(value / 10, value % 10)
            else -> value.toString()
        }
    }
}

/** No number of an entry is longer. */
private const val NUMBER_MAX_LENGTH = 6
