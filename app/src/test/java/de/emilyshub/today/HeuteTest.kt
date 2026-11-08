package de.emilyshub.today

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.Day
import de.emilyshub.TestApplication
import de.emilyshub.notes.NoteDraft
import de.emilyshub.routine
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Priority
import de.emilyshub.stats.StreakFlame
import de.emilyshub.todo
import de.emilyshub.todos.Due
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Heute as one module (SPEC.md §1): what is on it for the day it is, in its order, with Demnächst, the notes pinned,
 * the flame and the heatmap; anew as the day turns, each minute and as anything on it changes; and what each entry
 * offers. Over the app's modules on the in-memory database, the waits on the test's virtual time, the clock moved by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HeuteTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val clockChanges = MutableSharedFlow<Unit>()
    private val heute = Heute(app.routines, app.todos, app.notes, Day(app.clock, clockChanges))

    @Test
    fun `the day's routines and to-dos come in their order, with those shown before their day and the notes pinned`() = runTest {
        app.routine("Vitamine", "07:30")
        app.todo("Müll", due = Due.On(FRIDAY))
        app.todo("Arzt", due = Due.At(FRIDAY, LocalTime.of(7, 0)))
        app.todo("Geschenk", due = Due.On(FRIDAY.plusDays(2)), showsDaysBefore = 3)
        val note = runBlocking { app.notes.create(NoteDraft("Einkauf")) }
        runBlocking { app.notes.pinOnToday(note, pinned = true) }

        val day = collect().last()

        assertEquals(FRIDAY, day.date)
        assertEquals(listOf("Müll", "Arzt", "Vitamine"), day.entries.map { it.name })
        assertEquals(listOf("Geschenk"), day.upcoming.map { it.name })
        assertEquals(listOf("Einkauf"), day.pinned.map { it.title })
    }

    @Test
    fun `past midnight the day turns, the day before's routine staying while its time window is open`() = runTest {
        app.routine("Lesen", "23:00")
        app.clock.now = Instant.parse("2026-10-23T21:59:30Z") // 23:59:30 in Berlin
        val seen = collect()
        assertEquals(listOf(FRIDAY.atTime(23, 0)), seen.last().routineTimes())

        app.clock.now = Instant.parse("2026-10-23T22:00:01Z") // 00:00:01 on Saturday
        advanceTimeBy(31_000)
        runCurrent()

        assertEquals(FRIDAY.plusDays(1), seen.last().date)
        assertEquals(listOf(FRIDAY.atTime(23, 0), FRIDAY.plusDays(1).atTime(23, 0)), seen.last().routineTimes())
    }

    @Test
    fun `a to-do turning overdue moves to the top within a minute`() = runTest {
        app.routine("Vitamine", "07:00")
        app.todo("Arzt", due = Due.At(FRIDAY, LocalTime.of(7, 30)))
        app.clock.now = Instant.parse("2026-10-23T05:30:30Z") // 07:30:30 in Berlin
        val seen = collect()
        assertEquals(listOf("Vitamine", "Arzt"), seen.last().entries.map { it.name })

        // Overdue a minute after its time.
        app.clock.now = Instant.parse("2026-10-23T05:31:01Z")
        advanceTimeBy(31_000)
        runCurrent()

        assertEquals(listOf("Arzt", "Vitamine"), seen.last().entries.map { it.name })
    }

    @Test
    fun `a new time zone gives the day it is there at once`() = runTest {
        app.clock.now = Instant.parse("2026-10-23T20:00:00Z") // Friday 22:00 in Berlin
        val seen = collect()

        app.clock.timeZone = ZoneId.of("Asia/Tokyo") // Saturday 05:00 there
        clockChanges.emit(Unit)
        runCurrent()

        assertEquals(FRIDAY.plusDays(1), seen.last().date)
    }

    @Test
    fun `ticking off lights the flame and fills the heatmap's day, and undoing takes both back`() = runTest {
        app.routine("Vitamine", "07:30")
        val seen = collect()
        assertNull(seen.last().flame)
        assertEquals(0, seen.last().doneToday())

        heute.onToday.setDone(seen.last().entries.single(), done = true)
        runCurrent()
        assertEquals(StreakFlame(days = 1, todayOpen = false), seen.last().flame)
        assertEquals(1, seen.last().doneToday())

        heute.onToday.setDone(seen.last().entries.single(), done = false)
        runCurrent()
        assertNull(seen.last().flame)
        assertEquals(0, seen.last().doneToday())
    }

    @Test
    fun `a to-do ticked off on Heute stays there, done, for the rest of the day`() = runTest {
        app.todo("Müll", due = Due.On(FRIDAY))
        val seen = collect()

        heute.onToday.setDone(seen.last().entries.single(), done = true)
        runCurrent()

        val entry = seen.last().entries.single() as TodayEntry.OfTodo
        assertTrue(entry.todo.done)
    }

    @Test
    fun `never half of one day and half of the next`() = runTest {
        app.routine("Vitamine", "07:30")
        // On Heute of Saturday; under Demnächst the day before.
        app.todo("Geschenk", due = Due.On(FRIDAY.plusDays(1)), showsDaysBefore = 1)
        app.clock.now = Instant.parse("2026-10-23T21:59:30Z") // 23:59:30 in Berlin
        val seen = collect()

        app.clock.now = Instant.parse("2026-10-23T22:00:01Z")
        advanceTimeBy(31_000)
        runCurrent()

        assertEquals(listOf(FRIDAY, FRIDAY.plusDays(1)), seen.map { it.date }.distinct())
        seen.forEach { day ->
            val saturday = day.date == FRIDAY.plusDays(1)
            assertEquals("Geschenk on Heute of ${day.date}", saturday, day.entries.any { it.name == "Geschenk" })
            assertEquals("Geschenk under Demnächst of ${day.date}", !saturday, day.upcoming.any { it.name == "Geschenk" })
            assertTrue("Vitamine of ${day.date}", day.routineTimes().all { !it.toLocalDate().isAfter(day.date) })
        }
    }

    @Test
    fun `an open occurrence can be ticked off, skipped or dropped`() {
        val open = due(OccurrenceStatus.Open)
        assertTrue(open.tickable)
        assertEquals(RoutineOffer.SkipOrDrop, open.offer)
    }

    @Test
    fun `a done one can be unticked, and offers nothing else`() {
        val done = due(OccurrenceStatus.Done)
        assertTrue(done.tickable)
        assertEquals(RoutineOffer.None, done.offer)
    }

    @Test
    fun `a skipped one can be ticked off or undone, a dropped one only undone`() {
        listOf(OccurrenceStatus.Skipped, OccurrenceStatus.Dropped).forEach { status ->
            assertEquals(status.name, status == OccurrenceStatus.Skipped, due(status).tickable)
            assertEquals(status.name, RoutineOffer.Undo, due(status).offer)
        }
    }

    @Test
    fun `a missed one is how it ended, neither ticked off nor undone`() {
        val missed = due(OccurrenceStatus.Missed)
        assertFalse(missed.tickable)
        assertEquals(RoutineOffer.None, missed.offer)
    }

    @Test
    fun `one done by another of its group is as good as done, so offers nothing`() {
        val covered = due(OccurrenceStatus.Skipped).copy(coveredBy = "Laufen")
        assertTrue(covered.covered)
        assertFalse(covered.tickable)
        assertEquals(RoutineOffer.None, covered.offer)
    }

    /** Heute as [Heute.day] gives it, collected for the rest of the test. */
    private fun TestScope.collect(): List<HeuteDay> {
        val seen = mutableListOf<HeuteDay>()
        backgroundScope.launch { heute.day().collect { seen += it } }
        runCurrent()
        return seen
    }

    private fun HeuteDay.routineTimes() =
        entries.filterIsInstance<TodayEntry.OfRoutine>().map { it.routine.dueAt.toLocalDateTime() }

    private fun HeuteDay.doneToday() = heatmap.weeks.flatten().single { it.date == date }.done

    private fun due(status: OccurrenceStatus) = DueRoutine(
        occurrenceId = 1, routineId = 1, name = "Vitamine",
        dueAt = ZonedDateTime.of(FRIDAY, LocalTime.of(7, 30), ZoneId.of("Europe/Berlin")), status = status,
        priority = Priority.Normal,
    )

    private companion object {
        /** Friday, 23 October 2026, the day the test app's clock starts on. */
        val FRIDAY: LocalDate = LocalDate.of(2026, 10, 23)
    }
}
