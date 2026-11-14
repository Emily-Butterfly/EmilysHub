package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The Ablauf-Modus: a routine's checklist, step by step (SPEC.md §2; #101, #102). */
@RunWith(AndroidJUnit4::class)
class RoutineRunsTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)
    private var changes = 0
    private val runs = runsOn()

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a run shows the first item not ticked off, as step so-and-so of all`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        routines.tick(occurrence, itemId("Wasser"), ticked = true)

        val step = step(start(occurrence))

        assertEquals("Morgens", step.name)
        assertEquals("Zähne", step.item.text)
        assertEquals(2, step.number)
        assertEquals(3, step.count)
    }

    @Test
    fun `Fertig ticks the step off, as on Heute, and the next one not ticked off shows`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        routines.tick(occurrence, itemId("Zähne"), ticked = true)
        start(occurrence)

        done(occurrence)

        assertEquals(listOf("Wasser" to true, "Zähne" to true, "Dehnen" to false), checklist())
        assertEquals("Dehnen", step(occurrence).item.text)
    }

    @Test
    fun `Schritt zurueck unticks the last item ticked off in the run, and shows it again`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        start(occurrence)
        done(occurrence)
        done(occurrence)

        runs.back(occurrence)

        assertEquals(listOf("Wasser" to true, "Zähne" to false, "Dehnen" to false), checklist())
        assertEquals("Zähne", step(occurrence).item.text)
        assertEquals(true, step(occurrence).canGoBack)
    }

    @Test
    fun `Fertig for a step no longer shown, as on a second tap, ticks nothing`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        start(occurrence)
        val first = step(occurrence).item.itemId

        runs.done(occurrence, first)
        runs.done(occurrence, first)

        assertEquals(listOf("Wasser" to true, "Zähne" to false, "Dehnen" to false), checklist())
        assertNull(runs.current()?.finishedAt)
    }

    @Test
    fun `Schritt zurueck leaves what was ticked off before the run, and is not offered then`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        routines.tick(occurrence, itemId("Wasser"), ticked = true)
        start(occurrence)

        assertEquals(false, step(occurrence).canGoBack)
        runs.back(occurrence)

        assertEquals(listOf("Wasser" to true, "Zähne" to false), checklist())
    }

    @Test
    fun `Schritt zurueck passes over an item unticked on Heute meanwhile`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        start(occurrence)
        done(occurrence)
        done(occurrence)
        routines.tick(occurrence, itemId("Zähne"), ticked = false)

        runs.back(occurrence)

        assertEquals(listOf("Wasser" to false, "Zähne" to false, "Dehnen" to false), checklist())
        assertEquals(false, step(occurrence).canGoBack)
    }

    @Test
    fun `the last step does the routine, and the run shows it done, with how long it took and the routine's streak`() =
        runBlocking {
            val occurrence = routine("Morgens", "Wasser", "Zähne")
            start(occurrence)
            done(occurrence)
            clock.now = clock.now.plus(Duration.ofMinutes(7))
            done(occurrence)
            clock.now = clock.now.plus(Duration.ofMinutes(5))

            val done = view(occurrence) as RunView.Done

            assertEquals(OccurrenceStatus.Done, status(occurrence))
            assertEquals("Morgens", done.name)
            assertEquals(1, done.streak)
            assertEquals(Duration.ofMinutes(7), done.took)
        }

    @Test
    fun `the run takes up the checklist as it is, once ended and started again`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", "Dehnen")
        start(occurrence)
        done(occurrence)
        runs.stop()

        start(occurrence)

        assertEquals("Zähne", step(occurrence).item.text)
    }

    @Test
    fun `the run taken up again counts on from its first start`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        start(occurrence)
        val first = step(occurrence).runStartedAt
        runs.stop()
        clock.now = clock.now.plus(Duration.ofMinutes(3))

        start(occurrence)

        assertEquals(first, step(occurrence).runStartedAt)
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

        assertEquals(clock.now, step(start(evening)).runStartedAt)
    }

    @Test
    fun `one run at a time, starting another ends the first, its ticks staying`() = runBlocking {
        val morning = routine("Morgens", "Wasser", "Zähne")
        val evening = routine("Abends", "Lesen", time = "20:00")
        start(morning)
        done(morning)

        assertEquals(null, runs.other(morning))
        assertEquals(morning, runs.other(evening)?.occurrenceId)
        start(evening)

        assertEquals(evening, runs.current()?.occurrenceId)
        assertEquals(RunEnd.Stopped, (view(morning) as RunView.Ended).why)
        assertEquals(listOf("Wasser" to true, "Zähne" to false), checklist())
    }

    @Test
    fun `a run done or ended is no other to end`() = runBlocking {
        val morning = routine("Morgens", "Wasser")
        val evening = routine("Abends", "Lesen", time = "20:00")
        start(morning)
        done(morning)
        assertEquals(null, runs.other(evening))

        val noon = routine("Mittags", "Essen", time = "12:00")
        start(noon)
        routines.skip(noon)
        assertEquals(null, runs.other(evening))
    }

    @Test
    fun `Beenden ends the run, its ticks staying, and tells its notification`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        start(occurrence)
        done(occurrence)
        val before = changes

        runs.stop()

        assertNull(runs.current())
        assertEquals(RunEnd.Stopped, (view(occurrence) as RunView.Ended).why)
        assertEquals(listOf("Wasser" to true, "Zähne" to false), checklist())
        assertEquals(before + 1, changes)
    }

    @Test
    fun `the run ends, saying why, once the occurrence ends otherwise`() = runBlocking {
        val skipped = routine("Morgens", "Wasser")
        start(skipped)
        routines.skip(skipped)
        assertEquals(RunEnd.Skipped, (view(skipped) as RunView.Ended).why)

        val dropped = routine("Abends", "Wasser", time = "20:00")
        start(dropped)
        routines.dropForToday(dropped)
        assertEquals(RunEnd.Dropped, (view(dropped) as RunView.Ended).why)

        val paused = routine("Mittags", "Wasser", time = "12:00")
        val pausedRoutine = routineIdOf(paused)
        start(paused)
        routines.pause(pausedRoutine)
        assertEquals(RunEnd.Paused, runs.view(paused, pausedRoutine).first().let { (it as RunView.Ended).why })

        val deleted = routine("Nachmittags", "Wasser", time = "15:00")
        val deletedRoutine = routineIdOf(deleted)
        start(deleted)
        routines.delete(deletedRoutine)
        assertEquals(RunEnd.Gone, runs.view(deleted, deletedRoutine).first().let { (it as RunView.Ended).why })
    }

    @Test
    fun `the run ends once another routine of its group is done, naming it`() = runBlocking {
        val occurrence = routine("Dehnen", "Arme", "Beine", time = "18:00")
        start(occurrence)
        val jogging = routines.create(
            RoutineDraft(
                "Laufen", description = "", listOf(LocalTime.parse("18:00")), Repetition.Daily, coupledWith = setOf(routineIdOf(occurrence)),
            ),
        )
        routines.markDone(routines.dueOn(today).first().single { it.routineId == jogging }.occurrenceId)

        assertEquals(RunEnd.Covered("Laufen"), (view(occurrence) as RunView.Ended).why)
    }

    @Test
    fun `the run ends once the time window is over`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        start(occurrence)
        clock.now = Instant.parse("2026-10-23T12:00:00Z")

        assertEquals(RunEnd.Missed, (view(occurrence) as RunView.Ended).why)

        routines.markMissed(since = Instant.parse("2026-10-23T05:00:00Z"))
        assertEquals(RunEnd.Missed, (view(occurrence) as RunView.Ended).why)
    }

    @Test
    fun `a step showing ends as the time window does, with nothing written`() = runTest {
        val occurrence = routine("Morgens", "Wasser", time = "07:30")
        start(occurrence)
        val views = mutableListOf<RunView>()
        val collecting = launch { runs.view(occurrence, routineIdOf(occurrence)).collect { views += it } }
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
    fun `a run follows the occurrence as it changes, a step ticked off on Heute giving the next its own time`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne")
        start(occurrence)
        assertEquals("Wasser", step(occurrence).item.text)
        clock.now = clock.now.plus(Duration.ofMinutes(2))

        routines.tick(occurrence, itemId("Wasser"), ticked = true)

        assertEquals("Zähne", step(occurrence).item.text)
        assertEquals(clock.now, step(occurrence).startedAt)
    }

    @Test
    fun `a step with minutes runs down, then counts on as overtime`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", minutes = listOf(5, null))
        start(occurrence)
        val step = step(occurrence)

        assertEquals(StepTime.Countdown(left = Duration.ofMinutes(5), of = Duration.ofMinutes(5)), step.time(clock.now))
        assertEquals(
            StepTime.Countdown(left = Duration.ofSeconds(15), of = Duration.ofMinutes(5)),
            step.time(clock.now + Duration.ofSeconds(285)),
        )
        assertEquals(StepTime.Overtime(Duration.ofSeconds(45)), step.time(clock.now + Duration.ofSeconds(345)))
        assertEquals(clock.now + Duration.ofMinutes(5), step.endsAt)
    }

    @Test
    fun `a step without minutes shows the run's stopwatch`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", minutes = listOf(1, null))
        start(occurrence)
        clock.now = clock.now.plus(Duration.ofMinutes(2))
        done(occurrence)

        val step = step(occurrence)

        assertNull(step.endsAt)
        assertEquals(StepTime.Stopwatch(Duration.ofMinutes(3)), step.time(clock.now + Duration.ofMinutes(1)))
    }

    @Test
    fun `fertig gegen shows only with minutes for all that is left, counting on from now once a step is over`() = runBlocking {
        val timed = routine("Morgens", "Wasser", "Zähne", "Dehnen", minutes = listOf(2, 3, 10))
        start(timed)
        val step = step(timed)

        assertEquals(Outlook(steps = 3, doneBy = clock.now + Duration.ofMinutes(15)), step.outlook(clock.now))
        // Two minutes over the first step's time: the others still take their thirteen.
        val later = clock.now + Duration.ofMinutes(4)
        assertEquals(Outlook(steps = 3, doneBy = later + Duration.ofMinutes(13)), step.outlook(later))

        runs.stop()
        val partly = routine("Abends", "Lesen", "Licht aus", time = "20:00", minutes = listOf(5, null))
        start(partly)
        assertEquals(Outlook(steps = 2, doneBy = null), step(partly).outlook(clock.now))
    }

    @Test
    fun `a step's time begins when it shows, and anew with Schritt zurueck`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", minutes = listOf(5, 5))
        val startedAt = clock.now
        start(occurrence)
        assertEquals(startedAt, step(occurrence).startedAt)

        clock.now = clock.now.plus(Duration.ofMinutes(4))
        done(occurrence)
        assertEquals(clock.now, step(occurrence).startedAt)

        clock.now = clock.now.plus(Duration.ofMinutes(1))
        runs.back(occurrence)
        assertEquals(clock.now, step(occurrence).startedAt)
    }

    @Test
    fun `the run outlives the app, started anew it is at the same step since the same time`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", minutes = listOf(5, 5))
        start(occurrence)
        done(occurrence)
        val shown = step(occurrence)
        clock.now = clock.now.plus(Duration.ofMinutes(2))

        // A new process: the same data, another RoutineRuns.
        val restarted = runsOn()

        val again = restarted.view(occurrence, routineIdOf(occurrence)).first() as RunView.Step
        assertEquals(shown.item, again.item)
        assertEquals(shown.startedAt, again.startedAt)
        assertEquals(StepTime.Countdown(left = Duration.ofMinutes(3), of = Duration.ofMinutes(5)), again.time(clock.now))
    }

    @Test
    fun `starting the run running goes on as it is, its step's time too`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", minutes = listOf(5, 5))
        start(occurrence)
        val shown = step(occurrence).startedAt
        clock.now = clock.now.plus(Duration.ofMinutes(2))

        start(occurrence)

        assertEquals(shown, step(occurrence).startedAt)
    }

    @Test
    fun `the time running out is alerted once`() = runBlocking {
        val occurrence = routine("Morgens", "Wasser", "Zähne", minutes = listOf(1, 1))
        start(occurrence)
        assertEquals(false, runs.current()?.alerted)

        runs.alerted(occurrence)
        assertEquals(true, runs.current()?.alerted)

        done(occurrence)
        assertEquals(false, runs.current()?.alerted)
    }

    /** A routine due at [time] with a checklist of [items], each with its [minutes] if given, and its occurrence today. */
    private suspend fun routine(name: String, vararg items: String, time: String = "08:00", minutes: List<Int?> = emptyList()): Long {
        val id = routines.create(
            RoutineDraft(
                name, description = "", listOf(LocalTime.parse(time)), Repetition.Daily,
                checklist = items.mapIndexed { i, text -> ChecklistDraftItem(text = text, minutes = minutes.getOrNull(i)) },
            ),
        )
        return routines.dueOn(today).first().single { it.routineId == id }.occurrenceId
    }

    private fun runsOn() = RoutineRuns(routines, RunStore({ database }, Dispatchers.Unconfined), clock, onChange = { changes++ })

    /** Starts the run through [occurrence], and gives it back. */
    private fun start(occurrence: Long): Long = runBlocking {
        runs.start(occurrence, routineIdOf(occurrence))
        occurrence
    }

    /** "Fertig" for the step shown. */
    private suspend fun done(occurrence: Long) = runs.done(occurrence, step(occurrence).item.itemId)

    private suspend fun view(occurrence: Long): RunView = runs.view(occurrence, routineIdOf(occurrence)).first()

    private suspend fun step(occurrence: Long): RunView.Step = view(occurrence) as RunView.Step

    private suspend fun itemId(text: String): Long =
        routines.dueOn(today).first().flatMap { it.checklist }.first { it.text == text }.itemId

    private suspend fun checklist() = routines.dueOn(today).first().single { it.name == "Morgens" }.checklist.map { it.text to it.ticked }

    private suspend fun status(occurrence: Long) = routines.dueOn(today).first().single { it.occurrenceId == occurrence }.status

    private fun routineIdOf(occurrence: Long) = runBlocking { database.routines().occurrence(occurrence)!!.routineId }
}
