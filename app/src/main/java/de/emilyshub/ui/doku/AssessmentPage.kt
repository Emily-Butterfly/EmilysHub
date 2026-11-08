package de.emilyshub.ui.doku

import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import de.emilyshub.doku.DokuSaver
import de.emilyshub.doku.FilledAssessment
import de.emilyshub.doku.TestNumber
import de.emilyshub.doku.assessments.Answer
import de.emilyshub.doku.assessments.AnswerCodec
import de.emilyshub.doku.assessments.Assessment
import de.emilyshub.doku.assessments.BodyRegion
import de.emilyshub.doku.assessments.BodyRegionsItem
import de.emilyshub.doku.assessments.BuiltInAssessments
import de.emilyshub.doku.assessments.ChecklistItem
import de.emilyshub.doku.assessments.ChoiceItem
import de.emilyshub.doku.assessments.Item
import de.emilyshub.doku.assessments.NumberItem
import de.emilyshub.doku.assessments.ScaleItem
import de.emilyshub.doku.assessments.ScoreResult
import de.emilyshub.doku.assessments.Scoring
import de.emilyshub.doku.assessments.TextItem
import de.emilyshub.doku.assessments.YesNoItem
import de.emilyshub.doku.assessments.toggled
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch

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
    val sheet = loaded ?: return
    val filled = sheet.filled
    var answers by remember { mutableStateOf(filled.answers) }
    var deleted by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Closed by "Fertig": a new one with nothing answered but a result, such as an RMDQ, stays.
    var confirmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (sheet.canChange) {
        SaveAnswers(doku, assessments, page, original = filled.answers, current = answers, skip = { deleted }, confirmed = { confirmed })
    }
    val form = filled.form

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
            if (sheet.canChange) {
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
                val shown = Scoring.visible(form, answers).map(Item::id).toSet()
                form.sections.forEach { section ->
                    section.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                    if (section.hint.isNotEmpty()) Text(section.hint, style = MaterialTheme.typography.bodyMedium)
                    section.items.filter { it.id in shown }.forEach { item ->
                        // By its id, so that what a field keeps stays with its item as conditional ones come and go.
                        key(item.id) {
                            ItemField(
                                item = item,
                                // One of another kind is shown apart, with those the form no longer has.
                                answer = answers[item.id]?.takeIf { Scoring.fits(item, it) },
                                onAnswer = if (sheet.canChange) ({ answer: Answer? -> answers = if (answer == null) answers - item.id else answers + (item.id to answer) }) else null,
                            )
                        }
                    }
                }
            }
            val orphans = form?.let { Scoring.orphans(it, answers) } ?: answers
            if (orphans.isNotEmpty()) {
                Text(stringResource(R.string.doku_assessment_orphans), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                orphans.forEach { (itemId, answer) ->
                    Text("$itemId: ${answerText(answer)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (form != null && form.scores.isNotEmpty()) {
                HorizontalDivider()
                val results = Scoring.results(form, answers)
                results.forEach { Text(resultText(it, results.size), style = MaterialTheme.typography.titleMedium) }
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
                scope.launch {
                    assessments.delete(page.id)
                    onDone()
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

/**
 * Saves [current], once it is not [original] any more, as the page closes and before the Doku locks; a new form left
 * empty goes again then, unless [confirmed] by "Fertig" with a result ([Assessments.dropIfEmptyLater]), but not as the
 * screen turns, after which the page goes on with it. Nothing once [skip] says so.
 */
@Composable
private fun SaveAnswers(
    doku: Doku,
    assessments: Assessments,
    page: DokuPage.Assessment,
    original: Map<String, Answer>,
    current: Map<String, Answer>,
    skip: () -> Boolean,
    confirmed: () -> Boolean,
) {
    val activity = LocalActivity.current
    val answers by rememberUpdatedState(current)
    val skipNow by rememberUpdatedState(skip)
    val confirmedNow by rememberUpdatedState(confirmed)
    DisposableEffect(page) {
        val empty = { page.new && answers.isEmpty() }
        val changed = { !skipNow() && answers != original }
        val saver = doku.addSaver(
            DokuSaver {
                when {
                    skipNow() -> null
                    empty() -> assessments.pendingDrop(page.id)
                    changed() -> assessments.pending(page.appointmentId, page.id, page.assessmentId, answers)
                    else -> null
                }
            },
        )
        onDispose {
            saver.close()
            when {
                skipNow() -> Unit
                empty() -> if (activity?.isChangingConfigurations != true) assessments.dropIfEmptyLater(page.id, confirmedNow())
                changed() -> assessments.saveLater(page.appointmentId, page.id, page.assessmentId, answers)
            }
        }
    }
}

/** An item and its answer, to answer ([onAnswer], none for no answer); without it, only to read. */
@Composable
private fun ItemField(item: Item, answer: Answer?, onAnswer: ((Answer?) -> Unit)?) {
    val enabled = onAnswer != null
    val answerWith = onAnswer ?: {}
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item !is TextItem && item !is NumberItem) Text(item.text, style = MaterialTheme.typography.bodyLarge)
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
                    (item.min..item.max).forEach { n ->
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
                    OptionRow(option.label, on, enabled, Role.Checkbox, onClick = { answerWith(checked.toggled(item, option.id).orNone()) }) {
                        Checkbox(checked = on, onCheckedChange = null, enabled = enabled)
                    }
                }
                item.otherLabel?.let { label ->
                    WordsField(stringResource(R.string.doku_assessment_other, label), checked.other, enabled, onChange = { answerWith(checked.copy(other = it).orNone()) })
                }
            }
            is NumberItem -> NumberAnswer(item, answer as? Answer.Number, enabled, answerWith)
            is TextItem -> WordsField(item.text, (answer as? Answer.Text)?.text.orEmpty(), enabled, onChange = { answerWith(Answer.Text(it).takeIf { _ -> it.isNotEmpty() }) })
            is BodyRegionsItem -> {
                val regions = answer as? Answer.Regions ?: Answer.Regions(emptySet())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyRegion.entries.forEach { region ->
                        val on = region in regions.regions
                        Chip(region.label, chosen = on, enabled = enabled, onClick = {
                            answerWith(regions.copy(regions = if (on) regions.regions - region else regions.regions + region).orNone())
                        })
                    }
                }
                WordsField(stringResource(R.string.doku_assessment_regions_text), regions.text, enabled, onChange = { answerWith(regions.copy(text = it).orNone()) })
            }
        }
    }
}

/** A number typed with a comma, kept as typed while it is none. */
@Composable
private fun NumberAnswer(item: NumberItem, answer: Answer.Number?, enabled: Boolean, onAnswer: (Answer?) -> Unit) {
    // Not saveable, as all that is typed in the Doku.
    var typed by remember { mutableStateOf(answer?.hundredths?.let(TestNumber::typed).orEmpty()) }
    val invalid = typed.isNotBlank() && TestNumber.parse(typed, item.decimals) == null
    OutlinedTextField(
        value = typed,
        onValueChange = {
            typed = it
            onAnswer(TestNumber.parse(it, item.decimals)?.let(Answer::Number))
        },
        label = { Text(if (item.unit.isEmpty()) item.text else "${item.text} (${item.unit})") },
        singleLine = true,
        enabled = enabled,
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.doku_assessment_number_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (item.decimals) KeyboardType.Decimal else KeyboardType.Number),
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

/** Nothing checked and no words is no answer, as it is stored ([AnswerCodec]). */
private fun Answer.orNone(): Answer? = takeIf { AnswerCodec.encode(it) != null }

/** [answer] in words, for an item the form no longer has. */
@Composable
private fun answerText(answer: Answer): String = when (answer) {
    is Answer.Chosen -> answer.optionId
    is Answer.YesNo -> stringResource(if (answer.yes) R.string.doku_assessment_yes else R.string.doku_assessment_no)
    is Answer.OnScale -> "${answer.value}"
    // Numbers as numbers: 1, 3, 13.
    is Answer.Checked -> (answer.optionIds.sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })) + listOfNotNull(answer.other.ifEmpty { null })).joinToString(", ")
    is Answer.Number -> TestNumber.format(answer.hundredths)
    is Answer.Text -> answer.text
    is Answer.Regions -> (answer.regions.map(BodyRegion::label) + listOfNotNull(answer.text.ifEmpty { null })).joinToString(", ")
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
