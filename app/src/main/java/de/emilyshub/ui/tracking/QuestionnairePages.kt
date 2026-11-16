package de.emilyshub.ui.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.Funcap
import de.emilyshub.tracking.FuncapDraft
import de.emilyshub.tracking.FuncapResult
import de.emilyshub.tracking.FuncapScore
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingQuestionnaire
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.touchesIsland
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch

/**
 * The monthly questionnaire (SPEC.md §17, "Monatsfragebogen"): the draft to go on with, or "Beginnen", and the ones
 * completed, each to open ([onOpen]). A draft older than [Funcap.DRAFT_DAYS] days is to be discarded first, once asked;
 * not discarded, the page is left ([onBack]).
 */
@Composable
internal fun QuestionnairePage(tracking: Tracking, questionnaire: TrackingQuestionnaire, contentPadding: PaddingValues, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    // Counted up when completing failed, so that the page shows why: the draft went stale meanwhile.
    var reload by remember { mutableIntStateOf(0) }
    val overview = rememberIslandLoad(tracking, reload) { questionnaire.overview() } ?: return
    val scope = rememberCoroutineScope()
    val draft = overview.draft
    if (draft != null && !draft.stale) {
        key(draft.form.id) { DraftForm(tracking, questionnaire, draft, contentPadding, onNotCompleted = { reload++ }) }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        PageTitle(stringResource(R.string.tracking_funcap_title))
        Text(
            stringResource(if (overview.due) R.string.tracking_funcap_due else R.string.tracking_funcap_not_due),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(onClick = { scope.launch { questionnaire.begin() } }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.tracking_funcap_begin))
        }
        if (overview.results.isNotEmpty()) {
            Text(
                stringResource(R.string.tracking_funcap_results),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
            overview.results.forEach { result -> ResultLine(result) { onOpen(result.form.id) } }
        }
        Source()
    }
    if (draft != null) {
        // Older than a week, its "letzter Monat" has moved on: discarded once asked.
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.tracking_funcap_stale_title)) },
            text = { Text(stringResource(R.string.tracking_funcap_stale_text, draft.form.startedOn.numericDate(), Funcap.DRAFT_DAYS)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { questionnaire.delete(draft.form.id) } }) {
                    Text(stringResource(R.string.tracking_funcap_stale_discard))
                }
            },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.tracking_funcap_stale_back)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** A completed questionnaire in the list: its day and its total, tapped it opens. */
@Composable
private fun ResultLine(result: FuncapResult, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(result.day.numericDate(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.tracking_funcap_total_short, scoreText(result.score.total)))
    }
}

/**
 * The draft as it is answered: the introduction, the answers 0–6, and each domain with its items, each answer saved as
 * it is chosen and taken back when chosen again; "Abschließen" once any is answered, [onNotCompleted] if that failed.
 * The answers shown are those tapped, kept here at once, so that a second tap before the save sees the first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftForm(tracking: Tracking, questionnaire: TrackingQuestionnaire, draft: FuncapDraft, contentPadding: PaddingValues, onNotCompleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val answers = remember { mutableStateMapOf<Int, Int>().apply { putAll(draft.answers) } }
    var completing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        PageTitle(stringResource(R.string.tracking_funcap_title))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tracking_funcap_begun, draft.form.startedOn.numericDate()), style = MaterialTheme.typography.bodySmall)
            Text(Funcap.INTRODUCTION, style = MaterialTheme.typography.bodyMedium)
            Text(Funcap.QUESTION, style = MaterialTheme.typography.titleSmall)
            Funcap.ANSWERS.forEachIndexed { value, answer -> Text("$value: $answer", style = MaterialTheme.typography.bodySmall) }
        }
        Funcap.DOMAINS.forEach { domain ->
            Text(
                "${domain.code}  ${domain.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(start = 16.dp, top = 20.dp, end = 16.dp)
                    .semantics { heading() },
            )
            domain.items.forEach { item ->
                val chosen = answers[item.number]
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${item.number}  ${item.text}", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Funcap.SCALE.forEach { value ->
                            val description = stringResource(R.string.tracking_funcap_answer, value, Funcap.ANSWERS[value])
                            FilterChip(
                                selected = chosen == value,
                                onClick = {
                                    val next = if (answers[item.number] == value) null else value
                                    if (next == null) answers.remove(item.number) else answers[item.number] = next
                                    scope.launch { questionnaire.answer(draft.form.id, item.number, next) }
                                },
                                label = { Text(value.toString()) },
                                modifier = Modifier.semantics { contentDescription = description },
                            )
                        }
                    }
                    chosen?.let { Text(Funcap.ANSWERS[it], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            pluralStringResource(R.plurals.tracking_funcap_answered, answers.size, answers.size, Funcap.ITEMS),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Button(onClick = { completing = true }, enabled = answers.isNotEmpty()) { Text(stringResource(R.string.tracking_funcap_complete)) }
            OutlinedButton(onClick = { discarding = true }) { Text(stringResource(R.string.tracking_funcap_discard)) }
        }
        Source()
    }
    if (completing) {
        val missing = Funcap.ITEMS - answers.size
        AlertDialog(
            onDismissRequest = { completing = false },
            title = { Text(stringResource(R.string.tracking_funcap_complete_title)) },
            text = { if (missing > 0) Text(pluralStringResource(R.plurals.tracking_funcap_complete_missing, missing, missing)) },
            confirmButton = {
                TextButton(onClick = {
                    completing = false
                    scope.launch { if (!questionnaire.complete(draft.form.id)) onNotCompleted() }
                }) { Text(stringResource(R.string.tracking_funcap_complete)) }
            },
            dismissButton = { TextButton(onClick = { completing = false }) { Text(stringResource(R.string.cancel)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.tracking_funcap_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    discarding = false
                    scope.launch { questionnaire.delete(draft.form.id) }
                }) { Text(stringResource(R.string.tracking_funcap_discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.cancel)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** Completed questionnaire [id]: its day, its total and domains, its answers; deleted once asked ([onDone] then). */
@Composable
internal fun QuestionnaireResultPage(tracking: Tracking, questionnaire: TrackingQuestionnaire, id: Long, contentPadding: PaddingValues, onDone: () -> Unit) {
    val loaded = rememberIslandLoad(tracking, id) { questionnaire.result(id) } ?: return
    val (result, answers) = loaded
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        PageTitle(stringResource(R.string.tracking_funcap_result_title, result.day.numericDate()))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Scores(result.score)
            Text(stringResource(R.string.tracking_funcap_scores_note), style = MaterialTheme.typography.bodySmall)
        }
        Funcap.DOMAINS.forEach { domain ->
            Text(
                "${domain.code}  ${domain.name}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                    .semantics { heading() },
            )
            domain.items.forEach { item ->
                val answer = answers[item.number]
                Text(
                    "${item.number}  ${item.text}: ${answer?.toString() ?: stringResource(R.string.tracking_stand_missing)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
        OutlinedButton(onClick = { deleting = true }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.tracking_funcap_delete), color = MaterialTheme.colorScheme.error)
        }
        Source()
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.tracking_funcap_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        questionnaire.delete(id)
                        onDone()
                    }
                }) { Text(stringResource(R.string.tracking_funcap_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** The total and each domain's score of [score]. */
@Composable
private fun Scores(score: FuncapScore) {
    Text(stringResource(R.string.tracking_funcap_total, scoreText(score.total)), style = MaterialTheme.typography.titleMedium)
    Funcap.DOMAINS.forEach { domain ->
        Text(stringResource(R.string.tracking_funcap_domain_score, domain.code, domain.name, scoreText(score.domains[domain.code])), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A score with one decimal, "–" without one. */
@Composable
private fun scoreText(value: Double?): String = value?.let { formatNumber(it) } ?: stringResource(R.string.tracking_stand_missing)

/** The questionnaire's source and licence, as CC BY asks. */
@Composable
private fun Source() {
    Text(Funcap.SOURCE, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
}

@Composable
private fun PageTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(16.dp)
            .semantics { heading() },
    )
}
