package de.emilyshub.stats

import de.emilyshub.routines.Routines
import de.emilyshub.todos.Todos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import java.time.ZoneId

/**
 * The flame atop Heute and the heatmap under it (SPEC.md §1), each read from only as much of the routines' and
 * to-dos' history as it shows (#108): the heatmap from its first day, the flame from the latest day that breaks the
 * overall streak. A to-do counts on the day in the time zone given that it was ticked off on.
 */
class Progress(private val routines: Routines, private val todos: Todos) {

    /** The heatmap up to [today], as [Heatmap.of] has it; anew once something on it is done or undone. */
    fun heatmap(today: LocalDate, zone: ZoneId): Flow<Heatmap> {
        val first = Heatmap.firstDay(today)
        return combine(routines.doneFrom(first), todos.ticked(first, zone)) { routinesDone, todosDone ->
            Heatmap.of(today, routinesDone, todosDone, zone)
        }
    }

    /**
     * The flame on [today] by [rule], as [OverallHistory.flame] has it over the whole history, read as [flameOf] does;
     * anew once an occurrence or a to-do changes, given only if it changed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun flame(today: LocalDate, zone: ZoneId, rule: StreakRule): Flow<StreakFlame?> =
        combine(routines.historyChanges(), todos.changes()) { _, _ -> }
            .mapLatest { readFlame(today, zone, rule) }
            .distinctUntilChanged()

    private suspend fun readFlame(today: LocalDate, zone: ZoneId, rule: StreakRule): StreakFlame? {
        val first = listOfNotNull(routines.firstDay(), todos.firstTicked(zone)).minOrNull()
        return flameOf(today, first) { from ->
            OverallHistory(routines.historyFrom(from), todos.tickedDays(from, zone), rule, today)
        }
    }
}
