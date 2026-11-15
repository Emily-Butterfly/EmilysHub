package de.emilyshub.ui.tracking

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.Figure
import de.emilyshub.tracking.HintRule
import de.emilyshub.tracking.HistoryRange
import de.emilyshub.tracking.MarkerKind
import de.emilyshub.tracking.Series
import de.emilyshub.tracking.SeriesKind
import de.emilyshub.tracking.Source
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingEvaluation
import de.emilyshub.tracking.Week
import de.emilyshub.tracking.WeekFigures
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.island.touchesIsland
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The hints on Tracking's start page (SPEC.md §17): each by its title and pattern, the urgent marked, the newest and
 * most important first, to open ([onOpen]); then the rules still short of data, "noch 2 Stehtests".
 */
@Composable
internal fun HomeHints(tracking: Tracking, evaluation: TrackingEvaluation, onOpen: (HintRule) -> Unit) {
    val hints = rememberIslandLoad(tracking) { evaluation.hints() } ?: return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tracking_hints), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (hints.shown.isEmpty()) Text(stringResource(R.string.tracking_hints_none))
        hints.shown.forEach { hint ->
            Card(
                colors = if (hint.urgent) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                } else {
                    CardDefaults.cardColors()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpen(hint.rule) },
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hint.urgent) Text(stringResource(R.string.tracking_hint_urgent), style = MaterialTheme.typography.labelMedium)
                    Text(hint.title, style = MaterialTheme.typography.titleSmall)
                    Text(hint.pattern, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (hints.missing.isNotEmpty()) {
            Text(stringResource(R.string.tracking_hints_missing), style = MaterialTheme.typography.titleSmall)
            hints.missing.forEach {
                Text(stringResource(R.string.tracking_hints_missing_line, it.title, it.missing), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A hint on its page (SPEC.md §17): its pattern, what it rests on, what backs it up, the next step with the specialty
 * and the question, its sources, each tapped to show in full, the own working values among its thresholds; "Erledigt"
 * hides it until something is added to what it rests on, and goes back ([onDone]).
 */
@Composable
internal fun HintPage(tracking: Tracking, evaluation: TrackingEvaluation, page: TrackingPage.Hint, contentPadding: PaddingValues, onDone: () -> Unit) {
    val loaded = rememberIslandLoad(tracking, page) { listOf(evaluation.hint(page.rule)) } ?: return
    val hint = loaded.single()
    var source by remember { mutableStateOf<Source?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hint == null) {
            Text(stringResource(R.string.tracking_hint_gone))
            return@Column
        }
        Text(hint.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (hint.urgent) Text(stringResource(R.string.tracking_hint_urgent), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
        Text(hint.pattern, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.tracking_hint_no_diagnosis), style = MaterialTheme.typography.bodySmall)
        hint.basis?.let {
            Section(R.string.tracking_hint_basis)
            Text(it.describe())
        }
        if (hint.support.isNotEmpty()) {
            Section(R.string.tracking_hint_support)
            hint.support.forEach { Text(it) }
        }
        Section(R.string.tracking_hint_next)
        Text(stringResource(R.string.tracking_hint_specialty, hint.specialty))
        Text(stringResource(R.string.tracking_hint_question, hint.question))
        Section(R.string.tracking_hint_sources)
        if (hint.sources.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hint.sources.forEach { cited ->
                    val label = stringResource(R.string.tracking_hint_source_open, cited.code)
                    AssistChip(
                        onClick = { source = cited },
                        label = { Text(cited.code) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
        }
        hint.ownValues?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                scope.launch {
                    evaluation.markDone(hint.rule)
                    onDone()
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.tracking_hint_done))
        }
        Text(stringResource(R.string.tracking_hint_done_text), style = MaterialTheme.typography.bodySmall)
    }
    source?.let { shown ->
        AlertDialog(
            onDismissRequest = { source = null },
            title = { Text(shown.code) },
            text = { Text(shown.citation) },
            confirmButton = { TextButton(onClick = { source = null }) { Text(stringResource(R.string.ok)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** A heading within a page of the evaluation. */
@Composable
private fun Section(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/**
 * The week evaluation (SPEC.md §17): the week, Monday to Sunday, to change to the one before or after ([onWeek]); each
 * figure with the entries it rests on, "–" without data.
 */
@Composable
internal fun WeekPage(tracking: Tracking, evaluation: TrackingEvaluation, page: TrackingPage.Week, contentPadding: PaddingValues, onWeek: (LocalDate) -> Unit) {
    val week = Week(page.monday)
    val figures = rememberIslandLoad(tracking, week) { evaluation.week(week) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.tracking_week),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { onWeek(week.previous().monday) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tracking_week_previous))
            }
            Text(
                stringResource(R.string.tracking_week_range, week.monday.numericDate(), week.sunday.numericDate()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onWeek(week.next().monday) }, enabled = !week.next().monday.isAfter(evaluation.today())) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tracking_week_next))
            }
        }
        if (figures?.week == week) WeekFigureList(figures)
    }
}

/** The figures of the week, by what they are about. */
@Composable
private fun WeekFigureList(figures: WeekFigures) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Section(R.string.tracking_week_pulse)
        FigureLine(R.string.tracking_week_pulse_mean, figures.pulseMean)
        FigureLine(R.string.tracking_week_pulse_min, figures.pulseMin)
        FigureLine(R.string.tracking_week_pulse_max, figures.pulseMax)
        FigureLine(R.string.tracking_week_pulse_high, figures.pulseHighDays)
        Section(R.string.tracking_week_stand)
        FigureLine(R.string.tracking_week_stand_count, figures.standTests)
        FigureLine(R.string.tracking_week_stand_max_rise, figures.standMaxRise)
        FigureLine(R.string.tracking_week_stand_rise, figures.standRiseTwice)
        FigureLine(R.string.tracking_week_stand_drop_early, figures.standDropEarly)
        FigureLine(R.string.tracking_week_stand_drop_late, figures.standDropLate)
        Section(R.string.tracking_week_events)
        FigureLine(R.string.tracking_week_events_count, figures.events)
        FigureLine(R.string.tracking_week_events_breathless, figures.eventsBreathless)
        FigureLine(R.string.tracking_week_events_skin, figures.eventsSkin)
        FigureLine(R.string.tracking_week_events_fade, figures.skinFadeMinutes)
        Section(R.string.tracking_week_exertions)
        FigureLine(R.string.tracking_week_exertions_count, figures.exertions)
        FigureLine(R.string.tracking_week_episodes_count, figures.episodes)
        FigureLine(R.string.tracking_week_episodes_gap, figures.episodeGapHours)
        Section(R.string.tracking_week_symptoms)
        figures.symptoms.forEach { FigureLine(it.symptom.name, it.mean) }
        Section(R.string.tracking_week_day)
        FigureLine(R.string.tracking_week_fluid, figures.fluidLowDays)
        FigureLine(R.string.tracking_week_sleep, figures.poorNights)
        FigureLine(R.string.tracking_week_deviations, figures.deviations)
        FigureLine(R.string.tracking_week_backfilled, figures.backfilledShare, percent = true)
        Section(R.string.tracking_week_garmin)
        FigureLine(R.string.tracking_week_steps, figures.stepsPerDay)
        FigureLine(R.string.tracking_week_minutes_above, figures.minutesAbovePerDay)
    }
}

/** A figure under the label of resource [label]. */
@Composable
private fun FigureLine(@StringRes label: Int, figure: Figure, percent: Boolean = false) = FigureLine(stringResource(label), figure, percent)

/** A figure as "label: value", "–" without data, and the entries it rests on. */
@Composable
private fun FigureLine(label: String, figure: Figure, percent: Boolean = false) {
    val value = figure.value?.let { if (percent) stringResource(R.string.tracking_week_percent, (it * 100).roundToInt()) else formatNumber(it) } ?: stringResource(R.string.tracking_week_none)
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(stringResource(R.string.tracking_week_figure, label, value), style = MaterialTheme.typography.bodyMedium)
        if (figure.basis > 0) {
            Text(
                pluralStringResource(R.plurals.tracking_week_entries, figure.basis, figure.basis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A number as Germans write it: whole ones without a comma, others with one place. */
private fun formatNumber(value: Double): String =
    if (value == Math.rint(value)) value.toLong().toString() else String.format(Locale.GERMANY, "%.1f", value)

/**
 * The history (SPEC.md §17, "Verlauf"): over 4 or 12 weeks ([onRange]), a line chart for each symptom, the morning's
 * resting pulse and Garmin's values, drawn as the Doku's are, with the medications' changes as upright lines and the
 * episodes as bands; Garmin's own reckonings marked "Garmin-Schätzung". The changes and episodes listed below.
 */
@Composable
internal fun HistoryChartsPage(tracking: Tracking, evaluation: TrackingEvaluation, page: TrackingPage.History, contentPadding: PaddingValues, onRange: (HistoryRange) -> Unit) {
    val history = rememberIslandLoad(tracking, page.range) { evaluation.history(page.range) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.tracking_history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                .semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            ChoiceChip(stringResource(R.string.tracking_history_four), page.range == HistoryRange.Four) { onRange(HistoryRange.Four) }
            ChoiceChip(stringResource(R.string.tracking_history_twelve), page.range == HistoryRange.Twelve) { onRange(HistoryRange.Twelve) }
        }
        if (history == null) return@Column
        Text(stringResource(R.string.tracking_history_legend), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        history.series.forEach { series ->
            HistoryChart(seriesLabel(series), series, history.from, history.to, history.markers.map { it.date }, history.bands.map { it.from to it.to })
        }
        if (history.markers.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Section(R.string.tracking_history_changes)
                history.markers.forEach { marker ->
                    val date = marker.date.numericDate()
                    Text(
                        when (marker.kind) {
                            MarkerKind.Start -> stringResource(R.string.tracking_history_start, date, marker.name)
                            MarkerKind.DoseChange -> stringResource(R.string.tracking_history_dose, date, marker.name, marker.from, marker.to)
                            MarkerKind.End -> stringResource(R.string.tracking_history_end, date, marker.name)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (history.bands.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Section(R.string.tracking_history_episodes)
                history.bands.forEach { band ->
                    val kind = stringResource(kindName(band.kind))
                    Text(
                        if (band.running) {
                            stringResource(R.string.tracking_history_band_running, band.from.numericDate(), kind)
                        } else {
                            stringResource(R.string.tracking_history_band, band.from.numericDate(), band.to.numericDate(), kind)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

/** What a line of the history is called, with its unit and, for Garmin's, its tier. */
@Composable
private fun seriesLabel(series: Series): String = when (val kind = series.kind) {
    is SeriesKind.OfSymptom -> stringResource(R.string.tracking_history_symptom, kind.symptom.name)
    SeriesKind.MorningPulse -> stringResource(R.string.tracking_history_morning_pulse)
    SeriesKind.Steps -> stringResource(R.string.tracking_history_steps)
    SeriesKind.GarminRestingPulse -> stringResource(R.string.tracking_history_garmin_pulse)
    SeriesKind.GarminSleep -> stringResource(R.string.tracking_history_garmin_sleep)
    SeriesKind.MinutesAbove -> stringResource(R.string.tracking_history_minutes_above)
    SeriesKind.GarminDeepSleep -> stringResource(R.string.tracking_history_garmin_deep_sleep)
}

/**
 * [series] from [from] to [to], drawn as the Doku's line charts are (§14), in the theme's colours, with no library: a day
 * without a value leaves a gap, a single one a point; the episodes' [bands] behind as shaded spans, the medications'
 * changes ([markers]) as upright lines. Sleep is shown in hours.
 */
@Composable
private fun HistoryChart(label: String, series: Series, from: LocalDate, to: LocalDate, markers: List<LocalDate>, bands: List<Pair<LocalDate, LocalDate>>) {
    val scaleOf = if (series.kind == SeriesKind.GarminSleep) 1 / 60.0 else 1.0
    val points = series.points.map { (date, value) -> date to value?.let { it * scaleOf } }
    val numbers = points.mapNotNull { it.second }
    val low = series.scale?.first?.toDouble() ?: numbers.minOrNull() ?: 0.0
    val high = series.scale?.last?.toDouble() ?: numbers.maxOrNull() ?: 0.0
    val first = from.toEpochDay()
    val days = (to.toEpochDay() - first).coerceAtLeast(1)
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val mark = MaterialTheme.colorScheme.tertiary
    val band = MaterialTheme.colorScheme.secondaryContainer
    val none = stringResource(R.string.tracking_history_empty)
    val values = points.filter { it.second != null }.joinToString(", ") { (date, value) -> "${date.numericDate()} ${formatNumber(value!!)}" }.ifEmpty { none }
    val description = stringResource(R.string.tracking_history_chart, label, values)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(formatNumber(high), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = description },
        ) {
            val stroke = 2.dp.toPx()
            val xOf = { date: LocalDate -> size.width * (date.toEpochDay() - first) / days }
            bands.forEach { (start, end) ->
                // A band covers its days whole: from the start of its first to the end of its last.
                val left = xOf(start)
                val right = (xOf(end.plusDays(1))).coerceAtMost(size.width)
                drawRect(band, topLeft = Offset(left, 0f), size = Size((right - left).coerceAtLeast(stroke), size.height))
            }
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f))
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
            markers.forEach { date -> drawLine(mark, Offset(xOf(date), 0f), Offset(xOf(date), size.height), strokeWidth = stroke) }
            val spread = high - low
            val path = Path()
            var drawing = false
            points.forEach { (date, value) ->
                if (value == null) {
                    drawing = false
                    return@forEach
                }
                val share = if (spread == 0.0) 0.5f else ((value - low) / spread).toFloat().coerceIn(0f, 1f)
                val point = Offset(xOf(date), size.height * (1 - share))
                if (drawing) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                drawing = true
                drawCircle(line, radius = stroke * 2, center = point)
            }
            drawPath(path, line, style = Stroke(stroke))
        }
        Text(formatNumber(low), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text(from.numericDate(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(to.numericDate(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
