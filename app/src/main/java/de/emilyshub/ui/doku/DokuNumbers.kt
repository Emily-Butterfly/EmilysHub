package de.emilyshub.ui.doku

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.doku.Change
import de.emilyshub.doku.Direction
import de.emilyshub.doku.TestNumber
import de.emilyshub.ui.numericDate

/** [hundredths] as they read in [unit]: "120°" right after the number, as degrees are written; "12,5 kg" else. */
internal fun withUnit(hundredths: Long, unit: String): String = when {
    unit.isEmpty() -> TestNumber.format(hundredths)
    unit == "°" -> "${TestNumber.format(hundredths)}°"
    else -> "${TestNumber.format(hundredths)} $unit"
}

/** What [label] names with its [number] in [unit], "Flexion 120°", or "–" for none. */
internal fun entryText(label: String, number: Long?, unit: String): String = "$label ${number?.let { withUnit(it, unit) } ?: "–"}"

/** How a value changed, in [unit]: "▲ +30° besser seit 03.09.2026", with arrow and word, not by colour alone; none if measured once. */
@Composable
internal fun changeText(change: Change, unit: String): String? = when (change) {
    Change.MeasuredOnce -> null
    Change.Unchanged -> stringResource(R.string.doku_change_none)
    is Change.Changed -> {
        val arrow = if (change.by > 0) "▲" else "▼"
        val by = (if (change.by > 0) "+" else "") + withUnit(change.by, unit)
        stringResource(
            if (change.better) R.string.doku_change_better else R.string.doku_change_worse,
            arrow,
            by,
            change.since.numericDate(),
        )
    }
}

/** Which way is better, as the editor offers it. */
internal val Direction.label: Int
    get() = when (this) {
        Direction.HigherIsBetter -> R.string.doku_test_higher_better
        Direction.LowerIsBetter -> R.string.doku_test_lower_better
    }
