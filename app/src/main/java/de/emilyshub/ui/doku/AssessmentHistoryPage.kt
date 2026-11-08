package de.emilyshub.ui.doku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.doku.Assessments
import de.emilyshub.doku.Doku
import de.emilyshub.doku.assessments.BuiltInAssessments
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch

/**
 * A patient's forms, in the tab Bögen (SPEC.md §14): a card for each form with a result filled in, the one filled in last
 * first, with each result's last value, unit and band, and its change since the first, better or worse the way the form
 * says; tapped, it opens the form's history ([onOpenHistory]). Below, the forms without a result, the latest first, each
 * opening ([onOpen]).
 */
@Composable
internal fun AssessmentCards(
    doku: Doku,
    assessments: Assessments,
    patientId: Long,
    onOpenHistory: (assessmentId: String) -> Unit,
    onOpen: (appointmentId: Long, assessmentId: String, id: Long) -> Unit,
) {
    val cards = rememberDokuLoad(doku, patientId) { assessments.cards(patientId) } ?: return
    if (cards.scored.isEmpty() && cards.unscored.isEmpty()) return EmptyLine(stringResource(R.string.doku_patient_no_assessments))
    LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards.scored, key = { it.form.id }) { card ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(onClickLabel = stringResource(R.string.doku_history_chart, card.form.name), role = Role.Button) { onOpenHistory(card.form.id) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.form.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.doku_card_last, card.lastFilled.numericDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (card.lines.isEmpty()) Text(stringResource(R.string.doku_assessment_no_result))
                    card.lines.forEach { line ->
                        val value = listOfNotNull(withUnit(line.last, line.score.unit), line.band).joinToString(" · ")
                        Text(listOfNotNull("${line.score.name}: $value", changeText(line.change, line.score.unit)).joinToString(" "))
                    }
                }
            }
        }
        items(cards.unscored, key = { "unscored ${it.id}" }) { form ->
            ListItem(
                headlineContent = { Text(formName(form)) },
                supportingContent = { Text(form.date.numericDate()) },
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.doku_assessment_open, form.date.numericDate()), role = Role.Button) {
                    onOpen(form.appointmentId, form.assessmentId, form.id)
                },
            )
            HorizontalDivider()
        }
    }
}

/**
 * The history of a form for a patient (SPEC.md §14): a line chart for each of its results, subscales each their own;
 * below, a table of every time it was filled in, the latest first, a row opening the form at its appointment ([onOpen]).
 */
@Composable
internal fun AssessmentHistoryPage(
    doku: Doku,
    assessments: Assessments,
    page: DokuPage.AssessmentHistory,
    contentPadding: PaddingValues,
    onOpen: (appointmentId: Long, id: Long) -> Unit,
) {
    val history = rememberDokuLoad(doku, page) { assessments.history(page.patientId, page.assessmentId) } ?: return
    LazyColumn(contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(history.form.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(history.patientName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        history.columns.forEachIndexed { index, column ->
            item {
                // Oldest first, as time runs.
                val points = history.rows.reversed().map { it.date to it.numbers[index] }
                LineChart(checkNotNull(column.name), column, points)
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
        items(history.rows, key = { it.id }) { row ->
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.doku_assessment_open, row.date.numericDate()), role = Role.Button) {
                        onOpen(row.appointmentId, row.id)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(row.date.numericDate(), style = MaterialTheme.typography.titleSmall)
                Text(
                    history.columns.indices.joinToString(" · ") { i -> entryText(checkNotNull(history.columns[i].name), row.numbers[i], history.columns[i].unit) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider()
        }
    }
}

/**
 * The built-in forms, in the Doku's menu (SPEC.md §14): by category as DIGOTOR has them, each with its source and date,
 * and a switch to hide it from "+ Bogen"; those filled in stay wherever they are.
 */
@Composable
internal fun AssessmentsMenuPage(doku: Doku, assessments: Assessments, contentPadding: PaddingValues) {
    val hidden = rememberDokuLoad(doku) { assessments.hidden() } ?: return
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.doku_assessments_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.doku_assessments_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        BuiltInAssessments.byCategory(hidden = emptySet()).forEach { (category, forms) ->
            item(key = category.name) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp)
                        .semantics { heading() },
                )
            }
            items(forms, key = { "${category.name} ${it.id}" }) { form ->
                val isHidden = form.id in hidden
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = isHidden, role = Role.Switch, onValueChange = { scope.launch { assessments.setHidden(form.id, it) } })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(form.name, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.doku_assessment_source, form.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.doku_assessments_hide), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = isHidden, onCheckedChange = null)
                }
                HorizontalDivider()
            }
        }
    }
}
