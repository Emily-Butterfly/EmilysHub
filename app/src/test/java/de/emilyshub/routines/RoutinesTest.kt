package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RoutinesTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val noon = Instant.parse("2026-10-23T10:00:00Z") // 12:00 in Berlin
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a new routine is due today at its time`() = runBlocking {
        routines.create(draft("Zähne putzen", "08:00"))

        assertEquals(listOf("08:00 Zähne putzen: Open"), dueOn(today))
    }

    @Test
    fun `a routine with several times is due at each of them, each as its own occurrence`() = runBlocking {
        routines.create(draft("Wasser trinken", "20:00", "08:00", "12:00"))

        assertEquals(
            listOf("08:00 Wasser trinken: Open", "12:00 Wasser trinken: Open", "20:00 Wasser trinken: Open"),
            dueOn(today),
        )
    }

    @Test
    fun `a routine created during the day is due today only at its times still ahead`() = runBlocking {
        clock.now = noon

        routines.create(draft("Wasser trinken", "08:00", "20:00"))

        assertEquals(listOf("20:00 Wasser trinken: Open"), dueOn(today))
        assertEquals(listOf("08:00 Wasser trinken: Open", "20:00 Wasser trinken: Open"), dueOn(today.plusDays(1)))
    }

    @Test
    fun `a routine on certain weekdays is due on those days only`() = runBlocking {
        routines.create(draft("Gießen", "18:00", repetition = Repetition.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))))

        val dueDays = (0L..6L).map(today::plusDays).filter { dueOn(it).isNotEmpty() }
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), dueDays.map { it.dayOfWeek })
    }

    @Test
    fun `a routine every X days is due on its start date and every X days after`() = runBlocking {
        routines.create(draft("Bettwäsche", "10:00", repetition = Repetition.EveryNDays(days = 3, from = today.plusDays(1))))

        assertEquals(listOf(1L, 4L), (0L..6L).filter { dueOn(today.plusDays(it)).isNotEmpty() })
    }

    @Test
    fun `a one-time routine is due on its date only`() = runBlocking {
        routines.create(draft("Arzttermin", "09:30", repetition = Repetition.Once(today.plusDays(2))))

        assertEquals(listOf(2L), (0L..6L).filter { dueOn(today.plusDays(it)).isNotEmpty() })
        assertEquals(Repetition.Once(today.plusDays(2)), routines.all().first().single().repetition)
    }

    @Test
    fun `a time added during the day is due today if it is still ahead, otherwise from tomorrow`() = runBlocking {
        val water = routines.create(draft("Wasser trinken", "08:00"))
        dueOn(today)
        clock.now = noon

        routines.update(water, draft("Wasser trinken", "08:00", "10:00", "15:00"))

        assertEquals(listOf("08:00 Wasser trinken: Open", "15:00 Wasser trinken: Open"), dueOn(today))
        assertEquals(listOf("08:00", "10:00", "15:00"), dueOn(today.plusDays(1)).map { it.substringBefore(" ") })
    }

    @Test
    fun `a time taken away no longer comes today, while what was ticked off there stays`() = runBlocking {
        val water = routines.create(draft("Wasser trinken", "08:00", "12:00", "20:00"))
        routines.markDone(occurrenceAt("08:00"))

        routines.update(water, draft("Wasser trinken", "12:00"))

        assertEquals(listOf("08:00 Wasser trinken: Done", "12:00 Wasser trinken: Open"), dueOn(today))
    }

    @Test
    fun `a time moved to one already past today stays where it was today and moves from tomorrow`() = runBlocking {
        val walk = routines.create(draft("Spaziergang", "18:00"))
        dueOn(today)
        clock.now = noon

        routines.update(walk, draft("Spaziergang", "09:00"))

        assertEquals(listOf("18:00 Spaziergang: Open"), dueOn(today))
        assertEquals(listOf("09:00 Spaziergang: Open"), dueOn(today.plusDays(1)))
    }

    @Test
    fun `when all times move, ticked off ones stand in for the earlier new ones and open ones move on`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        routines.markDone(occurrenceAt("08:00"))
        clock.now = noon

        routines.update(pills, draft("Tabletten", "09:00", "21:00"))

        assertEquals(listOf("08:00 Tabletten: Done", "21:00 Tabletten: Open"), dueOn(today))
    }

    @Test
    fun `an open occurrence moves to a new time still ahead rather than to one gone by`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "20:00"))
        dueOn(today)
        clock.now = noon

        routines.update(pills, draft("Tabletten", "09:00", "21:00"))

        assertEquals(listOf("21:00 Tabletten: Open"), dueOn(today))
    }

    @Test
    fun `an overdue occurrence stays overdue when its time moves to another one gone by`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        dueOn(today)
        clock.now = noon

        routines.update(pills, draft("Tabletten", "09:00", "21:00"))

        assertEquals(listOf("08:00 Tabletten: Open", "21:00 Tabletten: Open"), dueOn(today))
    }

    @Test
    fun `a change of weekdays that leaves out today takes away its open occurrences, not what was ticked off`() = runBlocking {
        val stretch = routines.create(draft("Dehnen", "08:00", "18:00"))
        routines.skip(occurrenceAt("08:00"))

        routines.update(stretch, draft("Dehnen", "08:00", "18:00", repetition = Repetition.Weekdays(setOf(DayOfWeek.SATURDAY))))

        assertEquals(listOf("08:00 Dehnen: Skipped"), dueOn(today))
        assertEquals(listOf("08:00 Dehnen: Open", "18:00 Dehnen: Open"), dueOn(today.plusDays(1)))
    }

    @Test
    fun `a change that makes a routine due today adds only the times still ahead`() = runBlocking {
        val saturdays = Repetition.Weekdays(setOf(DayOfWeek.SATURDAY))
        val laundry = routines.create(draft("Wäsche", "08:00", "18:00", repetition = saturdays))
        clock.now = noon

        routines.update(laundry, draft("Wäsche", "08:00", "18:00"))

        assertEquals(listOf("18:00 Wäsche: Open"), dueOn(today))
    }

    @Test
    fun `a change that makes a routine due today shows in today's list right away`() = runBlocking {
        val saturdays = Repetition.Weekdays(setOf(DayOfWeek.SATURDAY))
        val laundry = routines.create(draft("Wäsche", "18:00", repetition = saturdays))
        val shown = mutableListOf<List<String>>()
        val watching = launch(Dispatchers.Unconfined) {
            routines.dueOn(today).collect { due -> shown += due.map { it.name } }
        }

        routines.update(laundry, draft("Wäsche", "18:00"))

        assertEquals(listOf("Wäsche"), shown.last())
        watching.cancel()
    }

    @Test
    fun `a change that makes a routine due today at a time already past first comes tomorrow`() = runBlocking {
        val laundry = routines.create(draft("Wäsche", "08:00", repetition = Repetition.Weekdays(setOf(DayOfWeek.SATURDAY))))
        clock.now = noon

        routines.update(laundry, draft("Wäsche", "08:00"))

        assertEquals(emptyList<String>(), dueOn(today))
        assertEquals(listOf("08:00 Wäsche: Open"), dueOn(today.plusDays(1)))
    }

    @Test
    fun `undoing a tick on a time taken away meanwhile takes the occurrence away while it is still to come`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        routines.markDone(occurrenceAt("08:00"))
        routines.markDone(occurrenceAt("20:00"))
        routines.update(pills, draft("Tabletten", "08:00"))

        routines.reopen(occurrenceAt("20:00"))

        assertEquals(listOf("08:00 Tabletten: Done"), dueOn(today))
    }

    @Test
    fun `undoing a tick on a time taken away meanwhile leaves it open once it is past`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        routines.markDone(occurrenceAt("08:00"))
        routines.update(pills, draft("Tabletten", "20:00"))
        clock.now = noon

        routines.reopen(occurrenceAt("08:00"))

        assertEquals(listOf("08:00 Tabletten: Open", "20:00 Tabletten: Open"), dueOn(today))
    }

    @Test
    fun `a routine created after its time today is first due tomorrow`() = runBlocking {
        clock.now = Instant.parse("2026-10-23T20:00:00Z") // 22:00 in Berlin

        routines.create(draft("Vitamine", "08:00"))

        assertEquals(emptyList<String>(), dueOn(today))
        assertEquals(listOf("08:00 Vitamine: Open"), dueOn(today.plusDays(1)))
    }

    @Test
    fun `a routine created while today's list is showing appears in it right away`() = runBlocking {
        val shown = mutableListOf<List<String>>()
        val watching = launch(Dispatchers.Unconfined) {
            routines.dueOn(today).collect { due -> shown += due.map { it.name } }
        }

        routines.create(draft("Zähne putzen", "08:00"))

        assertEquals(listOf("Zähne putzen"), shown.last())
        watching.cancel()
    }

    @Test
    fun `today's routines come in the order they are due`() = runBlocking {
        routines.create(draft("Tagebuch", "20:00"))
        routines.create(draft("Vitamine", "07:30"))
        routines.create(draft("Spaziergang", "12:00"))
        routines.create(draft("Blumen gießen", "12:00"))

        assertEquals(
            listOf("07:30 Vitamine: Open", "12:00 Blumen gießen: Open", "12:00 Spaziergang: Open", "20:00 Tagebuch: Open"),
            dueOn(today),
        )
    }

    @Test
    fun `a routine is due from the day it was created and again every day after`() = runBlocking {
        routines.create(draft("Zähne putzen", "08:00"))
        routines.markDone(routines.dueOn(today).first().single().occurrenceId)

        assertEquals(emptyList<String>(), dueOn(today.minusDays(1)))
        assertEquals(listOf("08:00 Zähne putzen: Done"), dueOn(today))
        assertEquals(listOf("08:00 Zähne putzen: Open"), dueOn(today.plusDays(1)))
    }

    @Test
    fun `an occurrence can be skipped, and done or skipped can be undone`() = runBlocking {
        routines.create(draft("Joggen", "18:00"))
        val occurrence = routines.dueOn(today).first().single().occurrenceId

        routines.skip(occurrence)
        assertEquals(listOf("18:00 Joggen: Skipped"), dueOn(today))

        routines.reopen(occurrence)
        assertEquals(listOf("18:00 Joggen: Open"), dueOn(today))

        routines.markDone(occurrence)
        routines.reopen(occurrence)
        assertEquals(listOf("18:00 Joggen: Open"), dueOn(today))
    }

    @Test
    fun `all routines are listed by time, then by name`() = runBlocking {
        routines.create(draft("Tagebuch", "20:00", description = "Drei gute Dinge"))
        routines.create(draft("Übungen", "07:30"))
        routines.create(draft("Atmen", "07:30"))

        val listed = routines.all().first()

        assertEquals(listOf("Atmen", "Übungen", "Tagebuch"), listed.map { it.name })
        assertEquals("Drei gute Dinge", listed.last().description)
    }

    @Test
    fun `a routine is saved with cleaned texts, and an invalid one is refused`() = runBlocking {
        routines.create(draft("  Zähne\nputzen ", "08:00", description = " Gründlich "))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { routines.create(draft("   ", "09:00")) }
        }
        assertEquals(listOf("Zähne putzen" to "Gründlich"), routines.all().first().map { it.name to it.description })
    }

    @Test
    fun `editing moves today's open occurrence to the new time, while a done one stays as it was`() = runBlocking {
        val vitamins = routines.create(draft("Vitamine", "08:00"))
        val walk = routines.create(draft("Spaziergang", "09:00"))
        routines.markDone(routines.dueOn(today).first().single { it.routineId == walk }.occurrenceId)

        routines.update(vitamins, draft("Vitamin D", "10:00", description = "Mit dem Frühstück"))
        routines.update(walk, draft("Spaziergang", "07:00"))

        assertEquals(listOf("09:00 Spaziergang: Done", "10:00 Vitamin D: Open"), dueOn(today))
        assertEquals(listOf("07:00 Spaziergang: Open", "10:00 Vitamin D: Open"), dueOn(today.plusDays(1)))
        assertEquals("Mit dem Frühstück", routines.get(vitamins)?.description)
    }

    @Test
    fun `undoing a done occurrence after the time was changed reopens it at the new time`() = runBlocking {
        val walk = routines.create(draft("Spaziergang", "09:00"))
        val occurrence = routines.dueOn(today).first().single().occurrenceId
        routines.markDone(occurrence)
        routines.update(walk, draft("Spaziergang", "18:00"))

        routines.reopen(occurrence)

        assertEquals(listOf("18:00 Spaziergang: Open"), dueOn(today))
    }

    @Test
    fun `a deleted routine is gone from the list and from today`() = runBlocking {
        val walk = routines.create(draft("Spaziergang", "09:00"))
        routines.create(draft("Lesen", "21:00"))
        dueOn(today)

        routines.delete(walk)

        assertEquals(listOf("Lesen"), routines.all().first().map { it.name })
        assertEquals(listOf("21:00 Lesen: Open"), dueOn(today))
    }

    @Test
    fun `a routine created just after local midnight starts on the local date`() = runBlocking {
        clock.now = Instant.parse("2026-10-22T22:30:00Z") // 00:30 on the 23rd in Berlin

        routines.create(draft("Schlafen gehen", "23:00"))

        assertEquals(emptyList<String>(), dueOn(LocalDate.parse("2026-10-22")))
        assertEquals(listOf("23:00 Schlafen gehen: Open"), dueOn(LocalDate.parse("2026-10-23")))
    }

    @Test
    fun `after flying to another time zone, routines are due at their times there`() = runBlocking {
        routines.create(draft("Zähne putzen", "08:00"))

        clock.timeZone = ZoneId.of("America/New_York")

        val due = routines.dueOn(today).first().single().dueAt
        assertEquals(LocalTime.of(8, 0), due.toLocalTime())
        assertEquals(Instant.parse("2026-10-23T12:00:00Z"), due.toInstant())
    }

    @Test
    fun `the day the clocks spring forward lasts 23 hours, the day they fall back 25`() {
        clock.now = Instant.parse("2027-03-27T23:00:00Z") // midnight starting Sunday, 28 March, in Berlin
        assertEquals(Duration.ofHours(23), routines.timeUntilTomorrow())

        clock.now = Instant.parse("2026-10-24T22:00:00Z") // midnight starting Sunday, 25 October, in Berlin
        assertEquals(Duration.ofHours(25), routines.timeUntilTomorrow())
    }

    @Test
    fun `on the night the clocks spring forward, a routine in the skipped hour comes after one at three`() = runBlocking {
        val springForward = LocalDate.parse("2027-03-28")
        clock.now = Instant.parse("2027-03-27T23:00:00Z") // midnight in Berlin
        routines.create(draft("Tabletten", "02:30"))
        routines.create(draft("Fenster schließen", "03:00"))

        assertEquals(listOf("03:00 Fenster schließen: Open", "03:30 Tabletten: Open"), dueOn(springForward))
    }

    @Test
    fun `on the night the clocks fall back, a routine created in the repeated hour does not start out overdue`() = runBlocking {
        val fallBack = LocalDate.parse("2026-10-25")
        clock.now = Instant.parse("2026-10-25T01:15:00Z") // 02:15 for the second time in Berlin

        routines.create(draft("Tabletten", "02:30")) // 02:30 came first time round, three quarters of an hour ago

        assertEquals(emptyList<String>(), dueOn(fallBack))
        assertEquals(listOf("02:30 Tabletten: Open"), dueOn(fallBack.plusDays(1)))
    }

    @Test
    fun `a one-time routine comes due only while one of its times is still ahead`() {
        clock.now = noon

        assertEquals(false, routines.comesDue(listOf(LocalTime.of(8, 0)), Repetition.Once(today)))
        assertEquals(true, routines.comesDue(listOf(LocalTime.of(8, 0), LocalTime.of(18, 0)), Repetition.Once(today)))
        assertEquals(true, routines.comesDue(listOf(LocalTime.of(8, 0)), Repetition.Once(today.plusDays(1))))
        assertEquals(false, routines.comesDue(listOf(LocalTime.of(23, 0)), Repetition.Once(today.minusDays(1))))
        assertEquals(true, routines.comesDue(listOf(LocalTime.of(8, 0)), Repetition.Daily))
    }

    private fun draft(name: String, vararg times: String, description: String = "", repetition: Repetition = Repetition.Daily) =
        RoutineDraft(name = name, description = description, times = times.map(LocalTime::parse), repetition = repetition)

    /** The id of today's occurrence at [time]. */
    private suspend fun occurrenceAt(time: String) =
        routines.dueOn(today).first().single { it.dueAt.toLocalTime() == LocalTime.parse(time) }.occurrenceId

    /** What the Heute list shows on [date], in its order. */
    private suspend fun dueOn(date: LocalDate) =
        routines.dueOn(date).first().map { "${it.dueAt.toLocalTime()} ${it.name}: ${it.status}" }
}
