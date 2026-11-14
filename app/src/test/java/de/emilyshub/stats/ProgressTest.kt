package de.emilyshub.stats

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.Day
import de.emilyshub.TestApplication
import de.emilyshub.history
import de.emilyshub.routine
import de.emilyshub.routines.Occurrence
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.today.Heute
import de.emilyshub.today.HeuteDay
import de.emilyshub.todo
import de.emilyshub.todos.Due
import de.emilyshub.todos.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Heute and the to-dos read only what they show (#108): each new query at its edges, the flame and the heatmap read
 * anew only as something on them changes, and a big history giving Heute the same as reading all of it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProgressTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val clockChanges = MutableSharedFlow<Unit>()
    private val heute =
        Heute(app.routines, app.todos, app.notes, app.subscriptions, Day(app.clock, clockChanges), Dispatchers.Unconfined)

    @Test
    fun `the heatmap reads from its first day on, and not the day before`() = runBlocking {
        val first = Heatmap.firstDay(FRIDAY)
        assertEquals(LocalDate.parse("2026-04-27"), first)
        val routine = app.routine("Vitamine", "07:30")
        app.history(routine, first.minusDays(1).toString() to Done, first.toString() to Done, first.plusDays(1).toString() to Missed)
        // 00:00:30 on the first day in Berlin, and 23:59:30 on the day before.
        ticked("Morgens", Instant.parse("2026-04-26T22:00:30Z"))
        ticked("Abends", Instant.parse("2026-04-26T21:59:30Z"))

        assertEquals(listOf(first), app.routines.doneFrom(first).first())
        assertEquals(listOf(Instant.parse("2026-04-26T22:00:30Z")), app.todos.ticked(first, BERLIN).first())
        assertEquals(2, Progress(app.routines, app.todos).heatmap(FRIDAY, BERLIN).first().weeks.first().first().done)
    }

    @Test
    fun `a to-do ticked off today stays on Heute, one ticked off yesterday does not, though both are read`() = runBlocking {
        ticked("Heute", Instant.parse("2026-10-22T22:00:01Z"), due = Due.On(FRIDAY)) // 00:00:01 today in Berlin
        ticked("Gestern", Instant.parse("2026-10-22T21:59:59Z"), due = Due.On(FRIDAY))
        ticked("Bald", Instant.parse("2026-10-23T05:00:00Z"), due = Due.On(FRIDAY.plusDays(2)), showsDaysBefore = 3)
        ticked("Bald gestern", Instant.parse("2026-10-22T05:00:00Z"), due = Due.On(FRIDAY.plusDays(2)), showsDaysBefore = 3)
        app.todo("Offen", due = Due.On(FRIDAY))
        ticked("Lange her", Instant.parse("2025-01-01T10:00:00Z"), due = Due.On(LocalDate.parse("2025-01-01")))

        val now = app.clock.instant()
        assertEquals(listOf("Heute", "Offen"), app.todos.dueOn(FRIDAY, now).first().map { it.title }.sorted())
        assertEquals(listOf("Bald"), app.todos.upcoming(FRIDAY, now).first().map { it.title })
        assertEquals(listOf("Offen"), app.todos.open().first().map { it.todo.title })
        assertEquals(5, app.todos.archivedCount().first())
    }

    @Test
    fun `in a new time zone a to-do counts on the day it was ticked off there`() = runBlocking {
        // 00:30 on the 23rd in Berlin, 23:30 on the 22nd in London.
        ticked("Spät", Instant.parse("2026-10-22T22:30:00Z"))
        val london = ZoneId.of("Europe/London")

        assertEquals(listOf(FRIDAY), app.todos.tickedDays(FRIDAY, BERLIN))
        assertEquals(emptyList<LocalDate>(), app.todos.tickedDays(FRIDAY, london))
        assertEquals(listOf(FRIDAY.minusDays(1)), app.todos.tickedDays(FRIDAY.minusDays(1), london))
        assertEquals(FRIDAY, app.todos.firstTicked(BERLIN))
        assertEquals(FRIDAY.minusDays(1), app.todos.firstTicked(london))
        assertEquals(emptyList<Instant>(), app.todos.ticked(FRIDAY, london).first())
    }

    @Test
    fun `the flame reads the day that broke the streak, and the same as from all of it`() = runBlocking {
        val routine = app.routine("Vitamine", "07:30")
        val broken = FRIDAY.minusDays(40)
        val days = (1L..300).map { FRIDAY.minusDays(it) }
        app.history(routine, *days.map { it.toString() to if (it == broken) Missed else Done }.toTypedArray())
        // Only a to-do on the day before the break, which the flame never reads.
        ticked("Früher", broken.minusDays(1).atTime(12, 0).atZone(BERLIN).toInstant())

        assertEquals(Missed, app.routines.historyFrom(broken).minBy { it.date }.status)
        assertEquals(broken, app.routines.historyFrom(broken).minOf { it.date })
        assertEquals(days.last(), app.routines.firstDay())
        val flame = Progress(app.routines, app.todos).flame(FRIDAY, BERLIN, StreakRule.DEFAULT).first()
        assertEquals(StreakFlame(days = 39, todayOpen = true), flame)
        assertEquals(wholeHistory(StreakRule.DEFAULT, FRIDAY).flame(), flame)
    }

    @Test
    fun `each minute Heute keeps its flame and heatmap, and reads them anew once something is ticked off`() = runTest {
        app.routine("Vitamine", "07:30")
        val seen = mutableListOf<HeuteDay>()
        backgroundScope.launch { heute.day().collect { seen += it } }
        runCurrent()
        val before = seen.last()

        app.clock.now = app.clock.now.plusSeconds(60)
        advanceTimeBy(61_000)
        runCurrent()
        assertTrue(seen.size > 1)
        assertSame(before.heatmap, seen.last().heatmap)

        val vitamine = seen.last().entries.single()
        heute.onToday.setDone(vitamine, done = true)
        runCurrent()
        assertEquals(1, seen.last().heatmap.weeks.flatten().single { it.date == FRIDAY }.done)
        assertEquals(StreakFlame(days = 1, todayOpen = false), seen.last().flame)
    }

    @Test
    fun `with five years of history Heute gives what reading all of it gave`() = runBlocking {
        val random = Random(108)
        val routines = (1..6).map { app.routine("Routine $it", "07:00") }
        val times = (6..10).map { LocalTime.of(it, 0) }
        val days = (1L..(5 * 365 + 1)).map { FRIDAY.minusDays(it) }
        // A month in the middle with all of it missed, the last two months all done, and the rest at random; no
        // to-do ticked off yesterday.
        val idle = days[900]..days[870]
        val good = days[60]..days[0]
        val occurrences = days.flatMap { date ->
            routines.flatMap { routine ->
                times.map { time ->
                    val status = when {
                        date in idle -> Missed
                        date in good -> Done
                        else -> STATUSES.random(random)
                    }
                    Occurrence(routineId = routine, date = date, time = time, status = status)
                }
            }
        }
        assertTrue(occurrences.size > 50_000)
        val database = app.testDatabase
        database.withTransaction { database.routines().insertOccurrences(occurrences) }
        database.withTransaction {
            repeat(5_000) {
                val at = FRIDAY.minusDays(random.nextLong(2, days.size.toLong())).atStartOfDay(BERLIN)
                    .plusSeconds(random.nextLong(86_400)).toInstant()
                database.todos().insert(Todo(title = "To-do $it", description = "", doneAt = at))
            }
        }

        assertSameAsWhole()
        // Then yesterday all missed, which breaks every streak, and the last one is needed too.
        database.openHelper.writableDatabase.execSQL("UPDATE occurrences SET status = 'missed' WHERE date = '${days[0]}'")
        assertSameAsWhole()
    }

    /** Heute's flame and heatmap by each of the [RULES], against those over the whole history. */
    private suspend fun assertSameAsWhole() {
        RULES.forEach { rule ->
            val day = heute.day(rule).first()
            val whole = wholeHistory(rule, FRIDAY)
            assertEquals(rule.toString(), whole.flame(), day.flame)
            val heatmap = Heatmap.of(
                FRIDAY,
                app.routines.history().first().filter { it.status == Done }.map { it.date },
                app.todos.ticked().first(),
                BERLIN,
            )
            assertEquals(heatmap.weeks, day.heatmap.weeks)
            assertEquals(heatmap.max, day.heatmap.max)
        }
    }

    /** The overall streak as Heute reckoned it before #108: over all occurrences and all to-dos ticked off. */
    private suspend fun wholeHistory(rule: StreakRule, today: LocalDate) = OverallHistory(
        app.routines.history().first(),
        app.todos.ticked().first().map { it.atZone(BERLIN).toLocalDate() },
        rule,
        today,
    )

    /** A to-do [title] ticked off [at]. */
    private fun ticked(title: String, at: Instant, due: Due = Due.None, showsDaysBefore: Int? = null) {
        val id = app.todo(title, due = due, showsDaysBefore = showsDaysBefore)
        runBlocking { app.testDatabase.todos().setDoneAt(id, at) }
    }

    private companion object {
        /** Friday, 23 October 2026, the day the test app's clock starts on. */
        val FRIDAY: LocalDate = LocalDate.of(2026, 10, 23)
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

        /** Mostly done, as an occurrence goes. */
        val STATUSES = List(14) { Done } + listOf(Missed, Missed, OccurrenceStatus.Dropped, OccurrenceStatus.Skipped, OccurrenceStatus.Skipped)

        val RULES = listOf(
            StreakRule.DEFAULT, StreakRule.Count(3), StreakRule.Count(30), StreakRule.Share(50), StreakRule.Share(90), StreakRule.Share(100),
        )
    }
}
