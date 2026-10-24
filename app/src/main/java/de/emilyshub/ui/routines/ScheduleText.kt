package de.emilyshub.ui.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.Routine
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.enumeration
import de.emilyshub.ui.numericDate

/** When a routine is due, in a line: "Täglich um 07:30", "Mo, Mi, Fr um 07:30 und 20:00", "Einmalig am 30.10.2026 um 09:30". */
@Composable
fun scheduleText(routine: Routine): String {
    val times = enumeration(routine.times.map { it.clockTime() })
    return when (val repetition = routine.repetition) {
        Repetition.Daily -> stringResource(R.string.routine_schedule_daily, times)
        is Repetition.Weekdays -> {
            val names = stringArrayResource(R.array.weekdays_short)
            val days = repetition.days.sorted().joinToString(", ") { names[it.ordinal] }
            stringResource(R.string.routine_schedule_weekdays, days, times)
        }
        is Repetition.EveryNDays -> stringResource(R.string.routine_schedule_every_n_days, repetition.days, times)
        is Repetition.Once -> stringResource(R.string.routine_schedule_once, repetition.date.numericDate(), times)
    }
}

