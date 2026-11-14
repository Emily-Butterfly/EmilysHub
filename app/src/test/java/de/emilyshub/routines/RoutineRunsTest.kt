package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The Ablauf-Modus: a routine's checklist, step by step (SPEC.md §2; #101). */
@RunWith(AndroidJUnit4::class)
class RoutineRunsTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)
    private val runs = RoutineRuns(routines, clock)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a run shows the first item not ticked off, as step so-and-so of all`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        routines.tick(occurrence, itemId("Wasser"), ticked = true)

        val step = view(start(occurrence)) as RunView.Step

        assertEquals("Morgens", step.name)
        assertEquals("Zähne", step.item.text)
        assertEquals(2, step.number)
        assertEquals(3, step.count)
    }

    @Test
    fun `Fertig ticks the step off, as on Heute, and the next one not ticked off shows`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        routines.tick(occurrence, itemId("Zähne"), ticked = true)
        var run = start(occurrence)

        run = runs.done(run, view(run) as RunView.Step)

        assertEquals(listOf("Wasser" to true, "Zähne" to true, "Dehnen" to false), checklist())
        assertEquals("Dehnen", (view(run) as RunView.Step).item.text)
    }

    @Test
    fun `Schritt zurueck unticks the last item ticked off in the run, and shows it again`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        var run = start(occurrence)
        run = runs.done(run, view(run) as RunView.Step)
        run = runs.done(run, view(run) as RunView.Step)

        run = runs.back(run, view(run) as RunView.Step)

        assertEquals(listOf("Wasser" to true, "Zähne" to false, "Dehnen" to false), checklist())
        assertEquals("Zähne", (view(run) as RunView.Step).item.text)
        assertEquals(true, run.canGoBack(view(run) as RunView.Step))
    }

    @Test
    fun `a run gone back a step can still be kept, as Android keeps a screen's state`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        var run = start(occurrence)
        run = runs.done(run, view(run) as RunView.Step)
        run = runs.done(run, view(run) as RunView.Step)
        run = runs.back(run, view(run) as RunView.Step)

        ObjectOutputStream(ByteArrayOutputStream()).writeObject(run)
    }

    @Test
    fun `Fertig tapped twice on the same step ticks it off once`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        var run = start(occurrence)
        val step = view(run) as RunView.Step

        run = runs.done(runs.done(run, step), step)

        assertEquals(1, run.tickedHere.size)
        assertNull(run.finishedAt)
    }

    @Test
    fun `Schritt zurueck leaves what was ticked off before the run, and is not offered then`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        routines.tick(occurrence, itemId("Wasser"), ticked = true)
        val run = start(occurrence)
        val step = view(run) as RunView.Step

        assertEquals(false, run.canGoBack(step))
        runs.back(run, step)

        assertEquals(listOf("Wasser" to true, "Zähne" to false), checklist())
    }

    @Test
    fun `Schritt zurueck passes over an item unticked on Heute meanwhile`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        var run = start(occurrence)
        run = runs.done(run, view(run) as RunView.Step)
        run = runs.done(run, view(run) as RunView.Step)
        routines.tick(occurrence, itemId("Zähne"), ticked = false)

        run = runs.back(run, view(run) as RunView.Step)

        assertEquals(listOf("Wasser" to false, "Zähne" to false, "Dehnen" to false), checklist())
        assertEquals(false, run.canGoBack(view(run) as RunView.Step))
    }

    @Test
    fun `the last step does the routine, and the run shows it done, with how long it took and the routine's streak`() =
        runBlocking {
            val occurrence = routine("Morgens", "Wasser", "Zähne")
            var run = start(occurrence)
            run = runs.done(run, view(run) as RunView.Step)
            clock.now = clock.now.plus(Duration.ofMinutes(7))
            run = runs.done(run, view(run) as RunView.Step)
            clock.now = clock.now.plus(Duration.ofMinutes(5))

            val done = view(run) as RunView.Done

            assertEquals(OccurrenceStatus.Done, status(occurrence))
            assertEquals("Morgens", done.name)
            assertEquals(1, done.streak)
            assertEquals(Duration.ofMinutes(7), run.took(clock.now))
        }

    @Test
    fun `the run takes up the checklist as it is, once left and started again`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        val first = start(occurrence)
        runs.done(first, view(first) as RunView.Step)

        val again = start(occurrence)

        assertEquals("Zähne", (view(again) as RunView.Step).item.text)
    }

    @Test
    fun `the run taken up again counts on from its first start`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        val first = start(occurrence)
        clock.now = clock.now.plus(Duration.ofMinutes(3))

        val again = start(occurrence)

        assertEquals(first.startedAt, again.startedAt)
        assertEquals(Duration.ofMinutes(3), again.took(clock.now))
    }

    @Test
    fun `a run of another occurrence of the routine starts afresh`() = runBlocking {
        routines.create(
            RoutineDraft(
                "Zähne", description = "", listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), Repetition.Daily,
                checklist = listOf(ChecklistDraftItem(text = "Putzen")),
            ),
        )
        val (morning, evening) = routines.dueOn(today).first().map { it.occurrenceId }
        start(morning)
        clock.now = clock.now.plus(Duration.ofMinutes(3))

        assertEquals(clock.now, start(evening).startedAt)
    }

    @Test
    fun `the run ends, saying why, once the occurrence ends otherwise`() = runBlocking {
        val skipped = routine("Morgens", "Wasser")
        routines.skip(skipped)
        assertEquals(RunEnd.Skipped, (view(start(skipped)) as RunView.Ended).why)

        val dropped = routine("Abends", "Wasser", time = "20:00")
        routines.dropForToday(dropped)
        assertEquals(RunEnd.Dropped, (view(start(dropped)) as RunView.Ended).why)

        val paused = routine("Mittags", "Wasser", time = "12:00")
        val pausedRun = start(paused)
        routines.pause(pausedRun.routineId)
        assertEquals(RunEnd.Paused, (view(pausedRun) as RunView.Ended).why)

        val deleted = routine("Nachmittags", "Wasser", time = "15:00")
        val deletedRun = start(deleted)
        routines.delete(deletedRun.routineId)
        assertEquals(RunEnd.Gone, (view(deletedRun) as RunView.Ended).why)
    }

    @Test
    fun `the run ends once another routine of its group is done, naming it`() = runBlocking {
        val occurrence = routine("Dehnen", "Arme", "Beine", time = "18:00")
        val run = start(occurrence)
        val jogging = routines.create(
            RoutineDraft("Laufen", description = "", listOf(LocalTime.parse("18:00")), Repetition.Daily, coupledWith = setOf(run.routineId)),
        )
        routines.markDone(routines.dueOn(today).first().single { it.routineId == jogging }.occurrenceId)

        assertEquals(RunEnd.Covered("Laufen"), (view(run) as RunView.Ended).why)
    }

    @Test
    fun `the run ends once the time window is over`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        val run = start(occurrence)
        clock.now = Instant.parse("2026-10-23T12:00:00Z")

        assertEquals(RunEnd.Missed, (view(run) as RunView.Ended).why)

        routines.markMissed(since = Instant.parse("2026-10-23T05:00:00Z"))
        assertEquals(RunEnd.Missed, (view(run) as RunView.Ended).why)
    }

    @Test
    fun `a step showing ends as the time window does, with nothing written`() = runTest {
        val occurrence = routine("Morgens", "Wasser", time = "07:30")
        val views = mutableListOf<RunView>()
        val collecting = launch { runs.view(start(occurrence)).collect { views += it } }
        runCurrent()
        assertEquals(1, views.size)

        // The window of four hours ends at 11:30, four and a half hours from now.
        advanceTimeBy(Duration.ofMinutes(270).toMillis() - 1)
        assertEquals(1, views.size)
        clock.now = Instant.parse("2026-10-23T09:30:00Z")
        advanceTimeBy(2)
        assertEquals(RunEnd.Missed, (views.last() as RunView.Ended).why)
        collecting.cancel()
    }

    @Test
    fun `a run follows the occurrence as it changes`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        val views = runs.view(start(occurrence))
        assertEquals("Wasser", (views.first() as RunView.Step).item.text)

        routines.tick(occurrence, itemId("Wasser"), ticked = true)

        assertEquals("Zähne", (views.first() as RunView.Step).item.text)
    }

    /** A routine due at [time] with a checklist of [items], and its occurrence today. */
    private suspend fun routine(name: String, vararg items: String, time: String = "08:00"): Long {
        val id = routines.create(
            RoutineDraft(
                name, description = "", listOf(LocalTime.parse(time)), Repetition.Daily,
                checklist = items.map { ChecklistDraftItem(text = it) },
            ),
        )
        return routines.dueOn(today).first().single { it.routineId == id }.occurrenceId
    }

    private fun start(occurrence: Long) = runBlocking { runs.start(occurrence, routineIdOf(occurrence)) }

    private suspend fun view(run: Run): RunView = runs.view(run).first()

    private suspend fun itemId(text: String): Long =
        routines.dueOn(today).first().flatMap { it.checklist }.first { it.text == text }.itemId

    private suspend fun checklist() = routines.dueOn(today).first().single { it.name == "Morgens" }.checklist.map { it.text to it.ticked }

    private suspend fun status(occurrence: Long) = routines.dueOn(today).first().single { it.occurrenceId == occurrence }.status

    private fun routineIdOf(occurrence: Long) = runBlocking { database.routines().occurrence(occurrence)!!.routineId }
}
