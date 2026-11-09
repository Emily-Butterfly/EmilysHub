package de.emilyshub.ui.doku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.doku.AssessmentSheet
import de.emilyshub.doku.Assessments
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuDraft
import de.emilyshub.doku.FilledAssessment
import de.emilyshub.doku.TestNumber
import de.emilyshub.doku.assessments.Answer
import de.emilyshub.doku.assessments.Assessment
import de.emilyshub.doku.assessments.BodyRegion
import de.emilyshub.doku.assessments.BodyRegionsItem
import de.emilyshub.doku.assessments.BuiltInAssessments
import de.emilyshub.doku.assessments.ChecklistItem
import de.emilyshub.doku.assessments.ChoiceItem
import de.emilyshub.doku.assessments.Goal
import de.emilyshub.doku.assessments.GoalsItem
import de.emilyshub.doku.assessments.Item
import de.emilyshub.doku.assessments.NumberItem
import de.emilyshub.doku.assessments.ScaleItem
import de.emilyshub.doku.assessments.ScoreResult
import de.emilyshub.doku.assessments.TextItem
import de.emilyshub.doku.assessments.TimeItem
import de.emilyshub.doku.assessments.YesNoItem
import de.emilyshub.doku.assessments.toggled
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.rememberClosing

/**
 * The forms filled in at an appointment, in the order they were added, each with its results; tapped, one opens
 * ([onOpen]); "+ Bogen" fills in one more ([onAdd]), unless only to read.
 */
@Composable
internal fun ColumnScope.AssessmentList(filled: List<FilledAssessment>, onOpen: (FilledAssessment) -> Unit, onAdd: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.doku_assessments),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onAdd != null) TextButton(onClick = onAdd) { Text(stringResource(R.string.doku_assessment_add)) }
    }
    filled.forEach { form ->
        val results = form.results
        ListItem(
            headlineContent = { Text(formName(form)) },
            supportingContent = if (results.isEmpty()) null else ({ Text(results.map { resultText(it, results.size) }.joinToString("\n")) }),
            modifier = Modifier.clickable { onOpen(form) },
        )
        HorizontalDivider()
    }
}

/** The name of [form] as this version of the app has it, or that it no longer does. */
@Composable
internal fun formName(form: FilledAssessment): String = form.form?.name ?: stringResource(R.string.doku_assessment_unknown, form.assessmentId)

/**
 * [result] as it reads: "Summenwert: 10 Punkte · „leichtgradige“ Depression"; while too little is answered, "Ergebnis: –"
 * for a form's only result, else its name with "–".
 */
@Composable
internal fun resultText(result: ScoreResult, of: Int): String {
    val value = result.value ?: return if (of == 1) {
        stringResource(R.string.doku_assessment_no_result)
    } else {
        stringResource(R.string.doku_assessment_result_none, result.score.name)
    }
    val shown = listOfNotNull(withUnit(value, result.score.unit), result.band).joinToString(" · ")
    return stringResource(R.string.doku_assessment_result, result.score.name, shown)
}

/**
 * The forms to fill in ([onChoose]): a search by name and abbreviation on top; below, without anything typed, those last
 * used, then each category as DIGOTOR has it with its forms; hidden ones not at all.
 */
@Composable
internal fun ChooseAssessment(doku: Doku, assessments: Assessments, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    // Not saveable, as all that is typed in the Doku.
    var query by remember { mutableStateOf("") }
    val recent = rememberDokuLoad(doku) { assessments.recent() }.orEmpty()
    val hidden = rememberDokuLoad(doku) { assessments.hidden() } ?: return
    val choice = @Composable { form: Assessment ->
        ListItem(
            headlineContent = { Text(form.name) },
            supportingContent = form.abbreviation?.let { { Text(it) } },
            modifier = Modifier.clickable { onChoose(form.id) },
        )
    }
    val heading = @Composable { text: String ->
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { heading() },
        )
    }
    DokuInsel(doku) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.doku_assessment_choose)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.doku_assessment_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isNotBlank()) {
                        val found = BuiltInAssessments.search(query, hidden)
                        if (found.isEmpty()) Text(stringResource(R.string.doku_assessment_none_found), modifier = Modifier.padding(top = 12.dp))
                        found.forEach { choice(it) }
                    } else {
                        if (recent.isNotEmpty()) {
                            heading(stringResource(R.string.doku_assessment_recent))
                            recent.forEach { choice(it) }
                        }
                        BuiltInAssessments.byCategory(hidden).forEach { (category, forms) ->
                            heading(category.label)
                            forms.forEach { choice(it) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            modifier = Modifier.touchesDoku(doku),
        )
    }
}

/**
 * A form filled in at an appointment (SPEC.md §14), its own page: its name, the appointment's day, its source and hint,
 * its items in the order of the original, those under a condition only once it holds, and at the bottom its results,
 * live, with where they lie. Answers to items the form no longer has stay, greyed. Saved as far as answered as the page
 * closes, by "Fertig" ([onDone]), back or a rotation, and before the Doku locks; never into the saved state. A new one
 * left empty goes again. Its menu deletes it, after asking. A closed patient's is only to read. Gone, the page closes.
 */
@Composable
internal fun AssessmentPage(doku: Doku, assessments: Assessments, page: DokuPage.Assessment, contentPadding: PaddingValues, onDone: () -> Unit) {
    var loaded by remember { mutableStateOf<AssessmentSheet?>(null) }
    LaunchedEffect(page) {
        val sheet = assessments.sheet(page.id)
        if (sheet == null) onDone() else loaded = sheet
    }
    var sheet by remember(loaded) { mutableStateOf(loaded) }
    val current = sheet ?: return
    val filled = current.filled
    var deleted by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Closed by "Fertig": a new one with nothing answered but a result, such as an RMDQ, stays.
    var confirmed by remember { mutableStateOf(false) }
    // Saves or deletes once, to its end, though the page is gone meanwhile.
    val closing = rememberClosing(onDone)
    KeepDraft(doku, page) {
        val answered = sheet
        if (deleted || answered == null) DokuDraft.None else assessments.draft(answered, page.new, confirmed)
    }
    val form = current.form

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                formName(filled),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (current.canChange) {
                TextButton(
                    onClick = {
                        confirmed = true
                        onDone()
                    },
                ) {
                    Text(stringResource(R.string.doku_assessment_done))
                }
                AssessmentMenu(doku, onDelete = { deleting = true })
            }
        }
        // Only the items scroll: the name and "Fertig" stay in reach.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(filled.date.numericDate(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (form != null) {
                Text(stringResource(R.string.doku_assessment_source, form.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (form.hint.isNotEmpty()) Text(form.hint, style = MaterialTheme.typography.bodyMedium)
                current.sections.forEach { (section, items) ->
                    section.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                    if (section.hint.isNotEmpty()) Text(section.hint, style = MaterialTheme.typography.bodyMedium)
                    items.forEach { (item, answer) ->
                        // By its id, so that what a field keeps stays with its item as conditional ones come and go.
                        key(item.id) {
                            ItemField(
                                item = item,
                                answer = answer,
                                onAnswer = if (current.canChange) ({ given: Answer? -> sheet = current.answering(item.id, given) }) else null,
                            )
                        }
                    }
                }
            }
            if (current.orphans.isNotEmpty()) {
                Text(stringResource(R.string.doku_assessment_orphans), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                current.orphans.forEach { (itemId, answer) ->
                    Text("$itemId: ${answerText(answer)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (form != null && form.scores.isNotEmpty()) {
                HorizontalDivider()
                current.results.forEach { Text(resultText(it, current.results.size), style = MaterialTheme.typography.titleMedium) }
            }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }

    if (deleting) {
        ConfirmDeleting(
            doku = doku,
            title = stringResource(R.string.doku_assessment_delete_title, formName(filled)),
            text = stringResource(R.string.doku_assessment_delete_text),
            onDelete = {
                deleting = false
                deleted = true
                closing { assessments.delete(page.id) }
            },
            onDismiss = { deleting = false },
        )
    }
}

/** An item and its answer, to answer ([onAnswer], none for no answer); without it, only to read. */
@Composable
private fun ItemField(item: Item, answer: Answer?, onAnswer: ((Answer?) -> Unit)?) {
    val enabled = onAnswer != null
    val answerWith = onAnswer ?: {}
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item !is TextItem && item !is NumberItem && item !is TimeItem) Text(item.text, style = MaterialTheme.typography.bodyLarge)
        when (item) {
            is ChoiceItem -> item.options.forEach { option ->
                val chosen = (answer as? Answer.Chosen)?.optionId == option.id
                OptionRow(option.label, chosen, enabled, Role.RadioButton, onClick = { answerWith(if (chosen) null else Answer.Chosen(option.id)) }) {
                    RadioButton(selected = chosen, onClick = null, enabled = enabled)
                }
            }
            is YesNoItem -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val yes = (answer as? Answer.YesNo)?.yes
                listOf(true to R.string.doku_assessment_yes, false to R.string.doku_assessment_no).forEach { (value, label) ->
                    Chip(stringResource(label), chosen = yes == value, enabled = enabled, onClick = { answerWith(if (yes == value) null else Answer.YesNo(value)) })
                }
            }
            is ScaleItem -> {
                val value = (answer as? Answer.OnScale)?.value
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.steps.forEach { n ->
                        Chip("$n", chosen = value == n, enabled = enabled, onClick = { answerWith(if (value == n) null else Answer.OnScale(n)) })
                    }
                }
                if (item.minLabel.isNotEmpty() || item.maxLabel.isNotEmpty()) {
                    Row {
                        Text("${item.min} = ${item.minLabel}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("${item.max} = ${item.maxLabel}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            is ChecklistItem -> {
                val checked = answer as? Answer.Checked ?: Answer.Checked(emptySet())
                item.options.forEach { option ->
                    val on = option.id in checked.optionIds
                    OptionRow(option.label, on, enabled, Role.Checkbox, onClick = { answerWith(checked.toggled(item, option.id)) }) {
                        Checkbox(checked = on, onCheckedChange = null, enabled = enabled)
                    }
                }
                item.otherLabel?.let { label ->
                    WordsField(stringResource(R.string.doku_assessment_other, label), checked.other, enabled, onChange = { answerWith(checked.copy(other = it)) })
                }
            }
            is NumberItem -> NumberAnswer(item, answer as? Answer.Number, enabled, answerWith)
            is TimeItem -> TimeAnswer(item, answer as? Answer.Time, enabled, answerWith)
            is TextItem -> WordsField(item.text, (answer as? Answer.Text)?.text.orEmpty(), enabled, onChange = { answerWith(Answer.Text(it)) })
            is BodyRegionsItem -> {
                val regions = answer as? Answer.Regions ?: Answer.Regions(emptySet())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.regions.forEach { region ->
                        val on = region in regions.regions
                        Chip(region.label, chosen = on, enabled = enabled, onClick = {
                            answerWith(regions.copy(regions = if (on) regions.regions - region else regions.regions + region))
                        })
                    }
                }
                WordsField(stringResource(R.string.doku_assessment_regions_text), regions.text, enabled, onChange = { answerWith(regions.copy(text = it)) })
            }
            is GoalsItem -> GoalsAnswer(item, answer as? Answer.Goals, enabled, answerWith)
        }
    }
}

/**
 * Goals named and rated, one after the other, with "+ Ziel" for one more and a cross to take one away; kept as typed,
 * empty ones too, while the answer holds only those with words or a level.
 */
@Composable
private fun GoalsAnswer(item: GoalsItem, answer: Answer.Goals?, enabled: Boolean, onAnswer: (Answer?) -> Unit) {
    // Not saveable, as all that is typed in the Doku.
    var goals by remember { mutableStateOf(answer?.goals?.ifEmpty { null } ?: listOf(Goal(""))) }
    fun change(changed: List<Goal>) {
        goals = changed
        onAnswer(Answer.Goals(changed))
    }
    goals.forEachIndexed { i, goal ->
        val name = stringResource(R.string.doku_assessment_goal, item.goalLabel, i + 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = goal.text,
                onValueChange = { text -> change(goals.toMutableList().also { it[i] = goal.copy(text = text.replace('\n', ' ')) }) },
                label = { Text(name) },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            if (enabled && goals.size > 1) {
                IconButton(onClick = { change(goals.toMutableList().also { it.removeAt(i) }) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.doku_assessment_goal_remove, name))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item.levels.forEach { level ->
                val chosen = goal.level == level.points
                Chip(level.label, chosen = chosen, enabled = enabled, onClick = {
                    change(goals.toMutableList().also { it[i] = goal.copy(level = if (chosen) null else level.points) })
                })
            }
        }
    }
    if (enabled) {
        TextButton(onClick = { change(goals + Goal("")) }) { Text(stringResource(R.string.doku_assessment_goal_add, item.goalLabel)) }
    }
}

/** A number typed with a comma, kept as typed while it is none. */
@Composable
private fun NumberAnswer(item: NumberItem, answer: Answer.Number?, enabled: Boolean, onAnswer: (Answer?) -> Unit) {
    // Not saveable, as all that is typed in the Doku.
    var typed by remember { mutableStateOf(answer?.hundredths?.let(TestNumber::typed).orEmpty()) }
    // Out of its range, such as more days than three months have, it is no answer.
    fun parsed(text: String) = TestNumber.parse(text, item.decimals)?.takeIf(item::accepts)
    val invalid = typed.isNotBlank() && parsed(typed) == null
    OutlinedTextField(
        value = typed,
        onValueChange = {
            typed = it
            onAnswer(parsed(it)?.let(Answer::Number))
        },
        label = { Text(if (item.unit.isEmpty()) item.text else "${item.text} (${item.unit})") },
        singleLine = true,
        enabled = enabled,
        isError = invalid,
        supportingText = if (invalid) {
            { Text(item.max?.let { stringResource(R.string.doku_assessment_number_out_of_range, it) } ?: stringResource(R.string.doku_assessment_number_invalid)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = if (item.decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A time typed as hh:mm, kept as typed while it is none. */
@Composable
private fun TimeAnswer(item: TimeItem, answer: Answer.Time?, enabled: Boolean, onAnswer: (Answer?) -> Unit) {
    // Not saveable, as all that is typed in the Doku.
    var typed by remember { mutableStateOf(answer?.minutes?.let(TimeItem::format).orEmpty()) }
    val invalid = typed.isNotBlank() && TimeItem.parse(typed) == null
    OutlinedTextField(
        value = typed,
        onValueChange = {
            typed = it
            onAnswer(TimeItem.parse(it)?.let(Answer::Time))
        },
        label = { Text("${item.text} (hh:mm)") },
        singleLine = true,
        enabled = enabled,
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(if (item.duration) R.string.doku_assessment_duration_invalid else R.string.doku_assessment_time_invalid)) }
        } else {
            null
        },
        // With the colon, which the keypads of numbers lack.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Words typed for [label]. */
@Composable
private fun WordsField(label: String, text: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A line to tap, with its radio button or checkbox in front. */
@Composable
private fun OptionRow(label: String, on: Boolean, enabled: Boolean, role: Role, onClick: () -> Unit, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(
                if (role == Role.RadioButton) {
                    Modifier.selectable(selected = on, enabled = enabled, role = role, onClick = onClick)
                } else {
                    Modifier.toggleable(value = on, enabled = enabled, role = role, onValueChange = { onClick() })
                },
            ),
    ) {
        control()
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A chip to choose, with a tick once chosen, not by colour alone. */
@Composable
private fun Chip(label: String, chosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = chosen,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = if (chosen) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }) else null,
    )
}

/** [answer] in words, for an item the form no longer has. */
@Composable
private fun answerText(answer: Answer): String = when (answer) {
    is Answer.Chosen -> answer.optionId
    is Answer.YesNo -> stringResource(if (answer.yes) R.string.doku_assessment_yes else R.string.doku_assessment_no)
    is Answer.OnScale -> "${answer.value}"
    // Numbers as numbers: 1, 3, 13.
    is Answer.Checked -> (answer.optionIds.sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })) + listOfNotNull(answer.other.ifEmpty { null })).joinToString(", ")
    is Answer.Number -> TestNumber.format(answer.hundredths)
    is Answer.Time -> TimeItem.format(answer.minutes)
    is Answer.Text -> answer.text
    is Answer.Regions -> (answer.regions.map(BodyRegion::label) + listOfNotNull(answer.text.ifEmpty { null })).joinToString(", ")
    is Answer.Goals -> answer.goals.joinToString("; ") { goal -> listOfNotNull(goal.text.ifEmpty { null }, goal.level?.let { "%+d".format(it) }).joinToString(" ") }
}

/** The three dots of a form, with "Löschen". */
@Composable
private fun AssessmentMenu(doku: Doku, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doku_assessment_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.touchesDoku(doku)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.doku_assessment_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
