package de.emilyshub.stats

import de.emilyshub.routines.today as dayOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A day of the heatmap: what was [done] on [date], at what [level] of [Heatmap.LEVELS]; one to come ([future]) is
 * empty.
 */
data class HeatmapDay(val date: LocalDate, val done: Int, val level: Int, val future: Boolean)

/** A [month] named over the [week] of the heatmap its first day falls in. */
data class HeatmapMonth(val week: Int, val month: YearMonth)

/**
 * What was done each day of the last [WEEKS] weeks, under Heute (SPEC.md §1): the [weeks] from Monday to Sunday, the
 * current one last, each day's count at a level measured against the fullest day ([max]), and the [months] to name
 * over them. Counted, not a rate: nothing skipped, dropped or missed comes in.
 */
class Heatmap private constructor(val weeks: List<List<HeatmapDay>>, val max: Int, val months: List<HeatmapMonth>) {

    companion object {
        /** The weeks shown, about six months. */
        const val WEEKS = 26

        /** The levels above empty. */
        const val LEVELS = 4

        /**
         * The heatmap up to [today], of the occurrences of routines done, by their day ([routinesDone]), and of the
         * to-dos ticked off, by the day in [zone] they were ticked off ([todosDone]).
         */
        fun of(today: LocalDate, routinesDone: List<LocalDate>, todosDone: List<Instant>, zone: ZoneId): Heatmap {
            val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L)
            val counts = (routinesDone + todosDone.map { dayOf(it, zone) })
                .filter { !it.isBefore(first) && !it.isAfter(today) }
                .groupingBy { it }
                .eachCount()
            val max = counts.values.maxOrNull() ?: 0
            val weeks = List(WEEKS) { week ->
                List(DayOfWeek.entries.size) { day ->
                    val date = first.plusWeeks(week.toLong()).plusDays(day.toLong())
                    val done = counts[date] ?: 0
                    HeatmapDay(date, done, level(done, max), future = date.isAfter(today))
                }
            }
            val months = weeks.mapIndexedNotNull { index, week ->
                week.firstOrNull { it.date.dayOfMonth == 1 }?.let { HeatmapMonth(index, YearMonth.from(it.date)) }
            }
            return Heatmap(weeks, max, months)
        }

        /** Empty with nothing done, else the share of [max], rounded up, so that a day with anything done shows. */
        private fun level(done: Int, max: Int): Int = if (done == 0) 0 else (done * LEVELS + max - 1) / max
    }
}
