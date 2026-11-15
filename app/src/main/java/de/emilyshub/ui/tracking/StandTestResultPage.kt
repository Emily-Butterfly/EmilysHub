package de.emilyshub.ui.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.AbortCause
import de.emilyshub.tracking.Beat
import de.emilyshub.tracking.StandTest
import de.emilyshub.tracking.StandTestReport
import de.emilyshub.tracking.StandTestRules
import de.emilyshub.tracking.StandTestSummary
import de.emilyshub.tracking.StandVariant
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingStandTests
import de.emilyshub.ui.dateAndTime
import de.emilyshub.ui.island.rememberIslandLoad
import java.time.Instant
import java.util.Locale

/**
 * The result of Stehtest [id] (SPEC.md §17): that a test at home is no medical examination and medications change it,
 * how it ended, what came before, the table of its points with those filled from Garmin's curve marked, the evaluation,
 * the curve, and the sources of the thresholds.
 */
@Composable
internal fun StandTestResultPage(tracking: Tracking, standTests: TrackingStandTests, id: Long, contentPadding: PaddingValues) {
    val report = rememberIslandLoad(tracking, id) { standTests.report(id) } ?: return
    val test = report.test
    val result = report.result
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.tracking_stand_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.tracking_stand_started, variantName(test.variant), test.startedAt.atZone(standTests.zone()).dateAndTime()))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.tracking_stand_disclaimer), modifier = Modifier.padding(12.dp)) }
        Text(statusText(test), style = MaterialTheme.typography.titleSmall)
        if (test.abortReason.isNotEmpty()) Text(stringResource(R.string.tracking_stand_reason, test.abortReason))
        Context(report)
        EntryHeading(stringResource(R.string.tracking_stand_evaluation))
        Text(
            stringResource(
                R.string.tracking_stand_lying_values,
                result.lyingPulse?.let { if (result.lyingFilled) stringResource(R.string.tracking_stand_filled, it.toString()) else it.toString() } ?: missing(),
                pressure(test.lyingSystolic, test.lyingDiastolic),
            ),
        )
        PointTable(report)
        Text(stringResource(R.string.tracking_stand_max_rise, result.maxRise?.let(::signed) ?: missing()))
        Text(stringResource(R.string.tracking_stand_rises_30, minutes(result.risesAtLeast30)))
        if (test.variant == StandVariant.Leaning) {
            Text(stringResource(R.string.tracking_stand_last_three, result.lastThreeMean?.let(::decimal) ?: missing(), result.lastThreeRise?.let(::decimal) ?: missing()))
        }
        Text(stringResource(R.string.tracking_stand_drop_early, mmHg(result.systolicDropEarly), mmHg(result.diastolicDropEarly)))
        Text(stringResource(R.string.tracking_stand_drop_late, mmHg(result.systolicDropLate), mmHg(result.diastolicDropLate)))
        Text(stringResource(R.string.tracking_stand_below_90, minutes(result.systolicBelow90)))
        if (report.curve.isNotEmpty()) Curve(report.curve, test)
        Text(
            stringResource(R.string.tracking_stand_sources, StandTestRules.SOURCES.joinToString { it.code }),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** What came before: coffees, the last meal, what was drunk, and the hours since today's intakes. */
@Composable
private fun Context(report: StandTestReport) {
    val test = report.test
    Text(
        stringResource(
            R.string.tracking_stand_context,
            test.caffeine?.toString() ?: missing(),
            test.mealMinutesBefore?.let { hours(it) } ?: missing(),
            test.fluidDecilitres?.let { TypedNumber.textOf(it, tenths = true) } ?: missing(),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    report.intakes.forEach { (medication, minutes) ->
        Text(stringResource(R.string.tracking_stand_intake, medication.name, hours(minutes)), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The points as a table: minute, pulse (filled ones marked), rise, blood pressure, complaints and legs. */
@Composable
private fun PointTable(report: StandTestReport) {
    val byMinute = report.points.associateBy { it.minute }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Header(R.string.tracking_stand_table_minute, 0.6f)
            Header(R.string.tracking_stand_table_pulse, 0.8f)
            Header(R.string.tracking_stand_table_rise, 0.9f)
            Header(R.string.tracking_stand_table_pressure, 1.2f)
            Header(R.string.tracking_stand_table_complaints, 0.9f)
            Header(R.string.tracking_stand_table_legs, 0.7f)
        }
        HorizontalDivider()
        report.result.points.forEach { point ->
            val own = byMinute[point.minute]
            Row(Modifier.fillMaxWidth()) {
                Cell(point.minute.toString(), 0.6f)
                Cell(point.pulse?.let { if (point.filled) stringResource(R.string.tracking_stand_filled, it.toString()) else it.toString() } ?: missing(), 0.8f)
                Cell(point.rise?.let(::signed) ?: missing(), 0.9f)
                Cell(pressure(point.systolic, point.diastolic), 1.2f)
                Cell(listOf(own?.dizziness, own?.palpitations, own?.breathlessness).joinToString("/") { it?.toString() ?: "–" }, 0.9f)
                Cell(
                    when (own?.legsDiscoloured) {
                        true -> stringResource(R.string.tracking_yes)
                        false -> stringResource(R.string.tracking_no)
                        null -> missing()
                    },
                    0.7f,
                )
            }
        }
        Text(stringResource(R.string.tracking_stand_table_legend), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RowScope.Header(text: Int, weight: Float) {
    Text(stringResource(text), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(weight))
}

@Composable
private fun RowScope.Cell(text: String, weight: Float) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(weight))
}

/** Garmin's heart rate over the test, tier A, beside the own points; a mark where it stood up. */
@Composable
private fun Curve(curve: List<Beat>, test: StandTest) {
    Text(stringResource(R.string.tracking_stand_curve), style = MaterialTheme.typography.titleSmall)
    HeartRateCurve(curve.map { it.at to it.bpm }, mark = test.standingAt)
}

/** Heart rates over time as a line, scaled to their range, with a [mark] across at a moment if given. */
@Composable
internal fun HeartRateCurve(beats: List<Pair<Instant, Int>>, mark: Instant?) {
    val line = MaterialTheme.colorScheme.tertiary
    val markColor = MaterialTheme.colorScheme.outline
    val low = beats.minOf { it.second }
    val high = beats.maxOf { it.second }
    val first = beats.first().first.toEpochMilli()
    val span = (beats.last().first.toEpochMilli() - first).coerceAtLeast(1)
    val description = stringResource(R.string.tracking_stand_curve_description, beats.joinToString { it.second.toString() })
    Text(high.toString(), style = MaterialTheme.typography.labelSmall)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
    ) {
        val stroke = 2.dp.toPx()
        val spread = (high - low).toFloat()
        val at = { beat: Pair<Instant, Int> ->
            val x = if (beats.size == 1) size.width / 2 else size.width * (beat.first.toEpochMilli() - first) / span
            val share = if (spread == 0f) 0.5f else (beat.second - low) / spread
            Offset(x, size.height * (1 - share))
        }
        mark?.let { moment ->
            val x = (size.width * (moment.toEpochMilli() - first) / span).coerceIn(0f, size.width)
            drawLine(markColor, Offset(x, 0f), Offset(x, size.height))
        }
        val path = Path()
        beats.forEachIndexed { index, beat ->
            val point = at(beat)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, line, style = Stroke(stroke))
    }
    Text(low.toString(), style = MaterialTheme.typography.labelSmall)
}

/**
 * All Stehtests (SPEC.md §17), the latest first: when, how, how it ended and its largest rise; each to open for its
 * result ([onOpen]).
 */
@Composable
internal fun StandTestsPage(tracking: Tracking, standTests: TrackingStandTests, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val list = rememberIslandLoad(tracking) { standTests.list() } ?: return
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.tracking_stand_list),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(16.dp)
                .semantics { heading() },
        )
        if (list.isEmpty()) Text(stringResource(R.string.tracking_stand_none), modifier = Modifier.padding(horizontal = 16.dp))
        list.forEach { summary -> SummaryLine(standTests, summary) { onOpen(summary.test.id) } }
    }
}

@Composable
private fun SummaryLine(standTests: TrackingStandTests, summary: StandTestSummary, onClick: () -> Unit) {
    val test = summary.test
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(test.startedAt.atZone(standTests.zone()).dateAndTime(), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(
                R.string.tracking_stand_line,
                variantName(test.variant),
                statusText(test),
                stringResource(R.string.tracking_stand_line_rise, summary.result.maxRise?.let(::signed) ?: missing()),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

/** How [test] ended, or that it runs. */
@Composable
private fun statusText(test: StandTest): String = when {
    test.finished -> stringResource(R.string.tracking_stand_finished)
    test.abortCause == AbortCause.Locked -> stringResource(R.string.tracking_stand_aborted_locked)
    test.abortCause == AbortCause.Pressed && test.abortedMinute == null -> stringResource(R.string.tracking_stand_aborted_lying)
    test.abortCause == AbortCause.Pressed -> stringResource(R.string.tracking_stand_aborted_at, test.abortedMinute!!)
    else -> stringResource(R.string.tracking_stand_running)
}

@Composable
private fun variantName(variant: StandVariant): String = stringResource(
    when (variant) {
        StandVariant.Standing -> R.string.tracking_stand_standing
        StandVariant.Leaning -> R.string.tracking_stand_leaning
    },
)

@Composable
private fun missing() = stringResource(R.string.tracking_stand_missing)

@Composable
private fun minutes(list: List<Int>): String =
    if (list.isEmpty()) stringResource(R.string.tracking_stand_none_value) else stringResource(R.string.tracking_stand_minutes, list.joinToString())

@Composable
private fun mmHg(value: Int?): String = value?.let { stringResource(R.string.tracking_stand_mmhg, it) } ?: missing()

@Composable
private fun pressure(systolic: Int?, diastolic: Int?): String =
    if (systolic == null && diastolic == null) missing() else "${systolic ?: "–"}/${diastolic ?: "–"}"

private fun signed(value: Int) = if (value > 0) "+$value" else value.toString()

private fun decimal(value: Double) = String.format(Locale.GERMANY, "%.1f", value)

/** Minutes as hours with a decimal comma. */
internal fun hours(minutes: Int) = String.format(Locale.GERMANY, "%.1f", minutes / 60.0)
