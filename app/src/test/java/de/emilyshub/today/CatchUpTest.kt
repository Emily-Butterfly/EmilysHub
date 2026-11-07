package de.emilyshub.today

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.Day
import de.emilyshub.TestApplication
import de.emilyshub.history
import de.emilyshub.routine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Dropped
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.OccurrenceStatus.Skipped
import de.emilyshub.routines.TimeWindow
import de.emilyshub.stats.StreakFlame
import de.emilyshub.todo
import de.emilyshub.todos.Due
import de.emilyshub.todos.Recurrence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
 * Catching up on yesterday from Heute (SPEC.md §1, Gestern nachtragen): the day before's routines and to-dos, ticked
 * off late as done that day, or unticked, and counting in full. Over the app's modules on the in-memory database, the
 * clock on Friday, 23 October 2026, 07:00 in Berlin unless moved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CatchUpTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val clockChanges = MutableSharedFlow<Unit>()
    private val heute = Heute(app.routines, app.todos, app.notes, Day(app.clock, clockChanges))

    @Test
    fun `yesterday shows its routines by time, with their status, and its to-dos, but nothing of today`() = runTest {
        val vitamins = app.routine("Vitamine", "07:30")
        val walk = app.routine("Spazieren", "12:00")
        app.history(vitamins, YESTERDAY_ISO to Done)
        app.history(walk, YESTERDAY_ISO to Missed)
        app.todo("Müll", due = Due.On(YESTERDAY))
        app.todo("Arzt", due = Due.On(TODAY))

        val day = collect().last()

        assertEquals(YESTERDAY, day.date)
        assertEquals(listOf("Müll", "Vitamine", "Spazieren"), day.entries.map { it.name })
        assertEquals(listOf(Done, Missed), day.routines().map { it.status })
        assertTrue(day.routines().all { it.dueAt.toLocalDate() == YESTERDAY })
    }

    @Test
    fun `a missed one is caught up as done, and unticked it is missed again`() = runTest {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, YESTERDAY_ISO to Missed)
        val seen = collect()
        val missed = seen.last().routines().single()
        assertTrue(missed.catchUpTickable)

        heute.catchUp(TodayEntry.OfRoutine(missed), done = true)
        runCurrent()
        assertEquals(Done, seen.last().routines().single().status)

        heute.catchUp(seen.last().entries.single(), done = false)
        runCurrent()
        assertEquals(Missed, seen.last().routines().single().status)
    }

    @Test
    fun `one dropped is caught up too, and unticked it counts as missed`() = runTest {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, YESTERDAY_ISO to Dropped)
        val seen = collect()

        heute.catchUp(seen.last().entries.single(), done = true)
        runCurrent()
        assertEquals(Done, seen.last().routines().single().status)

        heute.catchUp(seen.last().entries.single(), done = false)
        runCurrent()
        assertEquals(Missed, seen.last().routines().single().status)
    }

    @Test
    fun `one still open in its time window is ticked off, and unticked it is open again`() = runTest {
        app.clock.now = Instant.parse("2026-10-21T22:30:00Z") // Thursday 00:30 in Berlin
        app.routine("Lesen", "23:30", window = TimeWindow(hours = 4))
        app.clock.now = Instant.parse("2026-10-22T22:30:00Z") // Friday 00:30, Thursday's window open until 03:30
        val seen = collect()
        assertEquals(OccurrenceStatus.Open, seen.last().routines().single().status)

        heute.catchUp(seen.last().entries.single(), done = true)
        runCurrent()
        assertEquals(Done, seen.last().routines().single().status)

        heute.catchUp(seen.last().entries.single(), done = false)
        runCurrent()
        assertEquals(OccurrenceStatus.Open, seen.last().routines().single().status)
    }

    @Test
    fun `one skipped by hand stays as it is`() = runTest {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, YESTERDAY_ISO to Skipped)
        val seen = collect()
        assertFalse(seen.last().routines().single().catchUpTickable)

        heute.catchUp(seen.last().entries.single(), done = true)
        runCurrent()

        assertEquals(Skipped, seen.last().routines().single().status)
    }

    @Test
    fun `caught up, a missed day mends the streak and fills the heatmap, and unticking takes both back`() = runTest {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-21" to Done, YESTERDAY_ISO to Missed)
        val today = collectHeute()
        val yesterday = collect()
        assertNull(today.last().flame)

        heute.catchUp(yesterday.last().entries.single(), done = true)
        runCurrent()
        assertEquals(StreakFlame(days = 2, todayOpen = true), today.last().flame)
        assertEquals(1, today.last().doneOn(YESTERDAY))

        heute.catchUp(yesterday.last().entries.single(), done = false)
        runCurrent()
        assertNull(today.last().flame)
        assertEquals(0, today.last().doneOn(YESTERDAY))
    }

    @Test
    fun `in a coupled group, one caught up covers the others that day, and unticking gives them back`() = runTest {
        val home = app.routine("Tabletten zu Hause", "08:00")
        val work = app.routine("Tabletten Arbeit", "09:00", coupledWith = setOf(home))
        app.history(home, YESTERDAY_ISO to Missed)
        app.history(work, YESTERDAY_ISO to Missed)
        val seen = collect()

        heute.catchUp(seen.last().entries.first { it.name == "Tabletten zu Hause" }, done = true)
        runCurrent()
        val covered = seen.last().routines().single { it.name == "Tabletten Arbeit" }
        assertEquals(Skipped to "Tabletten zu Hause", covered.status to covered.coveredBy)
        assertFalse(covered.catchUpTickable)

        heute.catchUp(seen.last().entries.first { it.name == "Tabletten zu Hause" }, done = false)
        runCurrent()
        assertEquals(listOf(Missed, Missed), seen.last().routines().map { it.status })
        assertNull(seen.last().routines().single { it.name == "Tabletten Arbeit" }.coveredBy)
    }

    @Test
    fun `the checklist of a missed one is ticked off as on Heute, all of it catching it up`() = runTest {
        val stretching = app.routine("Dehnen", "07:30", checklist = listOf("Beine", "Rücken"))
        app.history(stretching, YESTERDAY_ISO to Missed)
        val seen = collect()
        val (legs, back) = seen.last().routines().single().checklist

        heute.catchUpTick(seen.last().entries.single(), legs.itemId, ticked = true)
        runCurrent()
        assertEquals(Missed, seen.last().routines().single().status)
        assertTrue(seen.last().routines().single().checklist.first().ticked)

        heute.catchUpTick(seen.last().entries.single(), back.itemId, ticked = true)
        runCurrent()
        assertEquals(Done, seen.last().routines().single().status)

        heute.catchUpTick(seen.last().entries.single(), legs.itemId, ticked = false)
        runCurrent()
        val unticked = seen.last().routines().single()
        assertEquals(Missed, unticked.status)
        assertEquals(listOf(false, true), unticked.checklist.map { it.ticked })
    }

    @Test
    fun `only yesterday's occurrences can be caught up, not older ones`() = runBlocking {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-21" to Missed)
        val older = app.routines.dueOn(LocalDate.parse("2026-10-21")).first().single()

        app.routines.catchUp(older.occurrenceId, done = true)

        assertEquals(Missed, app.routines.dueOn(LocalDate.parse("2026-10-21")).first().single().status)
    }

    @Test
    fun `today's occurrences are not caught up either, they are ticked off on Heute`() = runBlocking {
        app.routine("Vitamine", "07:30")
        val today = app.routines.dueOn(TODAY).first().single()

        app.routines.catchUp(today.occurrenceId, done = true)

        assertEquals(OccurrenceStatus.Open, app.routines.dueOn(TODAY).first().single().status)
    }

    @Test
    fun `yesterday shows the to-dos due then or overdue, and those done then, not those done today`() = runTest {
        app.todo("Müll", due = Due.On(YESTERDAY))
        app.todo("Steuer", due = Due.On(LocalDate.parse("2026-10-19")))
        val doneToday = app.todo("Brief", due = Due.On(YESTERDAY))
        val upcoming = app.todo("Geschenk", due = Due.On(TODAY.plusDays(1)), showsDaysBefore = 3)
        runBlocking { app.todos.markDone(doneToday) }

        val day = collect().last()

        assertEquals(listOf("Steuer", "Müll"), day.entries.map { it.name })
        assertTrue(day.entries.none { it is TodayEntry.OfTodo && it.todo.todoId == upcoming })
        // Overdue as it stood yesterday: the one due then was not yet.
        assertEquals(listOf(true, false), day.todos().map { it.overdue })
    }

    @Test
    fun `a to-do ticked off yesterday is done at 23 59 that day, leaves today's overdue and goes to the archive`() = runTest {
        val rubbish = app.todo("Müll", due = Due.On(YESTERDAY))
        val today = collectHeute()
        val yesterday = collect()
        assertEquals(listOf("Müll"), today.last().entries.map { it.name })

        heute.catchUp(yesterday.last().entries.single(), done = true)
        runCurrent()

        val done = checkNotNull(app.todos.get(rubbish)).doneAt
        assertEquals(ZonedDateTime.of(YESTERDAY, LocalTime.of(23, 59), BERLIN).toInstant(), done)
        assertTrue((yesterday.last().entries.single() as TodayEntry.OfTodo).todo.done)
        assertEquals(emptyList<String>(), today.last().entries.map { it.name })
        assertEquals(listOf("Müll"), app.todos.archived().first().map { it.title })
        assertEquals(1, today.last().doneOn(YESTERDAY))

        heute.catchUp(yesterday.last().entries.single(), done = false)
        runCurrent()
        assertNull(checkNotNull(app.todos.get(rubbish)).doneAt)
        assertEquals(listOf("Müll"), today.last().entries.map { it.name })
    }

    @Test
    fun `one coming again counting from done comes again from yesterday, the day it was done`() = runTest {
        val plants = app.todo("Blumen gießen", due = Due.On(LocalDate.parse("2026-10-21")), recurrence = Recurrence.EveryNDays(3, fromDone = true))
        val seen = collect()

        heute.catchUp(seen.last().entries.single(), done = true)
        runCurrent()

        val next = checkNotNull(app.todos.get(checkNotNull(app.todos.get(plants)?.nextId)))
        assertEquals(Due.On(LocalDate.parse("2026-10-25")), next.due)
    }

    @Test
    fun `a to-do not due by yesterday is not ticked off as done then`() = runBlocking {
        val doctor = app.todo("Arzt", due = Due.On(TODAY))

        app.todos.markDoneYesterday(doctor)

        assertNull(checkNotNull(app.todos.get(doctor)).doneAt)
    }

    @Test
    fun `past midnight yesterday turns to the new day before`() = runTest {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, YESTERDAY_ISO to Missed)
        app.clock.now = Instant.parse("2026-10-23T21:59:30Z") // Friday 23:59:30 in Berlin
        val seen = collect()
        assertEquals(YESTERDAY, seen.last().date)

        app.clock.now = Instant.parse("2026-10-23T22:00:01Z") // Saturday
        advanceTimeBy(31_000)
        runCurrent()

        assertEquals(TODAY, seen.last().date)
        assertEquals(listOf(TODAY), seen.last().routines().map { it.dueAt.toLocalDate() })
    }

    /** Yesterday as [Heute.yesterday] gives it, collected for the rest of the test. */
    private fun TestScope.collect(): List<Yesterday> {
        val seen = mutableListOf<Yesterday>()
        backgroundScope.launch { heute.yesterday().collect { seen += it } }
        runCurrent()
        return seen
    }

    /** Heute itself, collected for the rest of the test. */
    private fun TestScope.collectHeute(): List<HeuteDay> {
        val seen = mutableListOf<HeuteDay>()
        backgroundScope.launch { heute.day().collect { seen += it } }
        runCurrent()
        return seen
    }

    private fun Yesterday.routines() = entries.filterIsInstance<TodayEntry.OfRoutine>().map { it.routine }

    private fun Yesterday.todos() = entries.filterIsInstance<TodayEntry.OfTodo>().map { it.todo }

    private fun HeuteDay.doneOn(date: LocalDate) = heatmap.weeks.flatten().single { it.date == date }.done

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 23)
        val YESTERDAY: LocalDate = TODAY.minusDays(1)
        const val YESTERDAY_ISO = "2026-10-22"
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
