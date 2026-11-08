package de.emilyshub.ui.doku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.doku.HistoryColumn
import de.emilyshub.doku.HistoryRow
import de.emilyshub.doku.Measurements
import de.emilyshub.doku.Patients
import de.emilyshub.doku.TestNumber
import de.emilyshub.ui.numericDate
import java.time.LocalDate

/**
 * The history of a test for a patient (SPEC.md §14): the test's and the patient's name on top; a line chart for each
 * value, retired ones with numbers too, and for the sum; below, a table of all measurements, the latest first, each
 * row opening its appointment ([onOpenAppointment]). Only this patient's: no comparing between patients.
 */
@Composable
internal fun HistoryPage(
    patients: Patients,
    measurements: Measurements,
    page: DokuPage.History,
    contentPadding: PaddingValues,
    onOpenAppointment: (Long) -> Unit,
) {
    val history = rememberDokuLoad(patients, page) { measurements.history(page.patientId, page.testId) } ?: return
    val sumLabel = stringResource(R.string.doku_card_sum)
    val name = { column: HistoryColumn -> column.name ?: sumLabel }
    LazyColumn(contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(history.test.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(history.patientName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        history.columns.forEachIndexed { index, column ->
            item {
                // Oldest first, as time runs.
                val points = history.rows.reversed().map { it.date to it.numbers[index] }
                LineChart(name(column), column, points)
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
        items(history.rows) { row ->
            HistoryLine(row, history.columns.map(name), history.columns, onClick = { onOpenAppointment(row.appointmentId) })
        }
    }
}

/** A measurement as a row of the table: its day, its numbers, each with its value's name and unit, and its remark. */
@Composable
private fun HistoryLine(row: HistoryRow, names: List<String>, columns: List<HistoryColumn>, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.doku_history_row, row.date.numericDate()), role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(row.date.numericDate(), style = MaterialTheme.typography.titleSmall)
        Text(
            columns.indices.joinToString(" · ") { i -> "${names[i]} ${row.numbers[i]?.let { withUnit(it, columns[i].unit) } ?: "–"}" },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (row.remark.isNotEmpty()) Text(row.remark, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

/**
 * [points] of [column] over time, spaced by their days, drawn in the theme's colours: scaled to the value's range if it
 * has both ends, else to its numbers; a measurement without a number leaves a gap, and a single one is a point.
 */
@Composable
private fun LineChart(label: String, column: HistoryColumn, points: List<Pair<LocalDate, Long?>>) {
    val numbers = points.mapNotNull { it.second }
    val low = column.min.takeIf { column.max != null } ?: numbers.minOrNull() ?: 0
    val high = column.max.takeIf { column.min != null } ?: numbers.maxOrNull() ?: 0
    val first = points.first().first.toEpochDay()
    val days = (points.last().first.toEpochDay() - first).coerceAtLeast(1)
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.doku_history_chart, label) + ": " +
        points.joinToString(", ") { (date, number) -> "${date.numericDate()} ${number?.let { withUnit(it, column.unit) } ?: "–"}" }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (column.unit.isEmpty()) label else "$label (${column.unit})", style = MaterialTheme.typography.titleSmall)
        Text(TestNumber.format(high), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = description },
        ) {
            val stroke = 2.dp.toPx()
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f))
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height))
            val spread = (high - low).toFloat()
            val at = { date: LocalDate, number: Long ->
                val x = if (points.size == 1) size.width / 2 else size.width * (date.toEpochDay() - first) / days
                // A number outside the range, as from before it changed, is drawn at its edge.
                val share = if (spread == 0f) 0.5f else ((number - low) / spread).coerceIn(0f, 1f)
                Offset(x, size.height * (1 - share))
            }
            val path = Path()
            var drawing = false
            points.forEach { (date, number) ->
                if (number == null) {
                    drawing = false
                    return@forEach
                }
                val point = at(date, number)
                if (drawing) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                drawing = true
                drawCircle(line, radius = stroke * 2, center = point)
            }
            drawPath(path, line, style = Stroke(stroke))
        }
        Text(TestNumber.format(low), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text(points.first().first.numericDate(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (points.size > 1) Text(points.last().first.numericDate(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
