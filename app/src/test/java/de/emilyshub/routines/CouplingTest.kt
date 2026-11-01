package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Coupled routines (SPEC.md §2): one of the group done is enough for the day, the others count as skipped then; one
 * whose time window ended is missed only once none of the group can be done that day any more.
 */
@RunWith(AndroidJUnit4::class)
class CouplingTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `coupled routines form a group, which each of them sees and changes`() = runBlocking {
        val home = routines.create(draft("Tabletten zu Hause", "08:00"))
        val work = routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home)))
        assertEquals(setOf(work), routines.coupledWith(home))
        assertEquals(setOf(home), routines.coupledWith(work))

        val gym = routines.create(draft("Tabletten im Gym", "18:00"))
        routines.update(home, draft("Tabletten zu Hause", "08:00", coupledWith = setOf(work, gym)))
        assertEquals(setOf(home, gym), routines.coupledWith(work))

        routines.update(gym, draft("Tabletten im Gym", "18:00", coupledWith = setOf(home)))
        assertEquals("out of the group", emptySet<Long>(), routines.coupledWith(work))
        assertEquals(setOf(gym), routines.coupledWith(home))
    }

    @Test
    fun `a group left with one routine is none, also when the other is deleted`() = runBlocking {
        val home = routines.create(draft("Tabletten zu Hause", "08:00"))
        val work = routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home)))

        routines.update(work, draft("Tabletten auf der Arbeit", "09:00"))
        assertEquals(emptySet<Long>(), routines.coupledWith(home))

        routines.update(work, draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home)))
        routines.delete(work)
        assertEquals(emptySet<Long>(), routines.coupledWith(home))
    }

    @Test
    fun `a routine coupled with one of another group leaves its own, which is none if one is left`() = runBlocking {
        val a = routines.create(draft("Laufen", "07:30"))
        val b = routines.create(draft("Yoga", "07:30", coupledWith = setOf(a)))
        val c = routines.create(draft("Rad", "18:00"))

        routines.create(draft("Schwimmen", "19:00", coupledWith = setOf(b, c)))

        assertEquals(emptySet<Long>(), routines.coupledWith(a))
        assertEquals(2, routines.coupledWith(c).size)
    }

    @Test
    fun `only routines with one time a day can be coupled`() = runBlocking {
        val home = routines.create(draft("Tabletten zu Hause", "08:00"))
        val water = routines.create(draft("Wasser", "08:00", "12:00"))

        val twice = draft("Tabletten", "09:00", "20:00", coupledWith = setOf(home))
        assertTrue(DraftProblem.CoupledNeedsOneTime in twice.problems)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { routines.create(twice) } }

        val work = routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home, water)))
        assertEquals("one with several times is left out", setOf(home), routines.coupledWith(work))
    }

    @Test
    fun `one done, the others of the group that day are skipped by it, even once their time window is over`() = runBlocking {
        clock.now = berlin("2026-10-23T05:30")
        val home = routines.create(draft("Tabletten zu Hause", "08:00", window = TimeWindow(hours = 2)))
        routines.create(draft("Tabletten auf der Arbeit", "06:00", coupledWith = setOf(home), window = TimeWindow(hours = 1)))
        clock.now = berlin("2026-10-23T08:05")

        routines.markDone(occurrenceOf("Tabletten zu Hause"))

        assertEquals(listOf("06:00 Tabletten auf der Arbeit: Skipped", "08:00 Tabletten zu Hause: Done"), dueOn(today))
        assertEquals("Tabletten zu Hause", routines.dueOn(today).first().first().coveredBy)
        assertEquals("tomorrow is a day of its own", listOf("Open", "Open"), statusesOn(today.plusDays(1)))

        clock.now = berlin("2026-10-23T12:00")
        routines.markMissed(since = clock.now)
        assertEquals(listOf("Skipped", "Done"), statusesOn(today))
    }

    @Test
    fun `ticked off through its checklist, it skips the others too`() = runBlocking {
        val home = routines.create(draft("Tabletten zu Hause", "08:00", checklist = listOf("Vitamin D")))
        routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home)))
        val occurrence = occurrenceOf("Tabletten zu Hause")
        val item = routines.checklist(home).single().id

        routines.tick(occurrence, item, ticked = true)

        assertEquals(listOf("Done", "Skipped"), statusesOn(today))
    }

    @Test
    fun `undoing it opens the others again, and they are missed only once none of the group can be done any more`() =
        runBlocking {
                clock.now = berlin("2026-10-23T05:30")
            val home = routines.create(draft("Tabletten zu Hause", "08:00", window = TimeWindow(hours = 2)))
            routines.create(draft("Tabletten auf der Arbeit", "06:00", coupledWith = setOf(home), window = TimeWindow(hours = 1)))
            clock.now = berlin("2026-10-23T08:05")
            routines.markDone(occurrenceOf("Tabletten zu Hause"))

            routines.reopen(occurrenceOf("Tabletten zu Hause"))
            routines.markMissed(since = clock.now)

            assertEquals("its window is over, but the other can still be done", listOf("Open", "Open"), statusesOn(today))
            assertEquals(null, routines.dueOn(today).first().first().coveredBy)

            clock.now = berlin("2026-10-23T10:00")
            routines.markMissed(since = clock.now)
            assertEquals(listOf("Missed", "Missed"), statusesOn(today))
        }

    @Test
    fun `undone after every time window of the group is over, the others are missed with it`() = runBlocking {
        val home = routines.create(draft("Tabletten zu Hause", "08:00", window = TimeWindow(hours = 2)))
        routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home), window = TimeWindow(hours = 2)))
        clock.now = berlin("2026-10-23T08:05")
        routines.markDone(occurrenceOf("Tabletten zu Hause"))
        clock.now = berlin("2026-10-23T12:00")

        routines.reopen(occurrenceOf("Tabletten zu Hause"))
        routines.markMissed(since = clock.now)

        assertEquals(listOf("Missed", "Missed"), statusesOn(today))
    }

    @Test
    fun `a coupled routine whose time window is over reminds no more while another of the group can still be done`() =
        runBlocking {
            val home = routines.create(draft("Tabletten zu Hause", "08:00", window = TimeWindow(hours = 1)))
            routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home), window = TimeWindow(hours = 1)))
            clock.now = berlin("2026-10-23T09:30")
            routines.markMissed(since = clock.now)

            val home8 = occurrenceOf("Tabletten zu Hause")
            assertEquals(listOf("Open", "Open"), statusesOn(today))
            assertEquals(listOf("Tabletten auf der Arbeit"), routines.stillDue(listOf(home8, occurrenceOf("Tabletten auf der Arbeit"))).map { it.name })
        }

    @Test
    fun `skipping one, or dropping it for today, leaves the others of the group as they are`() = runBlocking {
        val home = routines.create(draft("Tabletten zu Hause", "08:00"))
        routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home)))
        routines.create(draft("Tabletten im Gym", "18:00", coupledWith = setOf(home)))

        routines.skip(occurrenceOf("Tabletten zu Hause"))
        routines.dropForToday(occurrenceOf("Tabletten auf der Arbeit"))

        assertEquals(listOf("Skipped", "Dropped", "Open"), statusesOn(today))
    }

    @Test
    fun `a paused routine stays in the group but does not count, it is not skipped nor keeps the others from being missed`() =
        runBlocking {
            val home = routines.create(draft("Tabletten zu Hause", "08:00", window = TimeWindow(hours = 2)))
            val work = routines.create(draft("Tabletten auf der Arbeit", "11:00", coupledWith = setOf(home)))
            routines.pause(work)
            assertEquals(setOf(work), routines.coupledWith(home))

            clock.now = berlin("2026-10-23T10:00")
            routines.markMissed(since = clock.now)

            assertEquals(listOf("08:00 Tabletten zu Hause: Missed"), dueOn(today))
        }

    @Test
    fun `the statistics count one skipped by another of its group as skipped`() = runBlocking {
        val home = routines.create(draft("Tabletten zu Hause", "08:00"))
        val work = routines.create(draft("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home)))

        routines.markDone(occurrenceOf("Tabletten zu Hause"))

        assertEquals(listOf(OccurrenceStatus.Skipped), routines.history(work).first().filter { it.date == today }.map { it.status })
    }

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(clock.zone).toInstant()

    private fun draft(
        name: String,
        vararg times: String,
        window: TimeWindow = TimeWindow(),
        checklist: List<String> = emptyList(),
        coupledWith: Set<Long> = emptySet(),
    ) = RoutineDraft(
        name, description = "", times.map(LocalTime::parse), Repetition.Daily, window = window,
        checklist = checklist.map { ChecklistDraftItem(text = it) }, coupledWith = coupledWith,
    )

    /** The id of today's occurrence of the routine [name]d so. */
    private suspend fun occurrenceOf(name: String) = routines.dueOn(today).first().single { it.name == name }.occurrenceId

    private suspend fun statusesOn(date: LocalDate) = routines.dueOn(date).first().map { it.status.name }

    /** What the Heute list shows on [date], in its order. */
    private suspend fun dueOn(date: LocalDate) =
        routines.dueOn(date).first().map { "${it.dueAt.toLocalTime()} ${it.name}: ${it.status}" }
}
