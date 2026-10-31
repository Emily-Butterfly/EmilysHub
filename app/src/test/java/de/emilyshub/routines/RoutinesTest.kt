package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.Whereabouts
import de.emilyshub.tags.Tags
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RoutinesTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val noon = Instant.parse("2026-10-23T10:00:00Z") // 12:00 in Berlin
    private var changes = 0
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined, onChange = { changes++ })

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
        assertEquals(Repetition.Once(today.plusDays(2)), routines.inUse().first().single().repetition)
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
    fun `Heute nicht drops every open occurrence of the routine on Heute, the later times too, and leaves the others`() =
        runBlocking {
            routines.create(draft("Wasser trinken", "08:00", "12:00", "20:00", window = TimeWindow(hours = 2)))
            routines.create(draft("Lesen", "21:00"))
            routines.markDone(occurrenceAt("08:00"))
            clock.now = berlin("2026-10-23T13:00")

            routines.dropForToday(occurrenceAt("12:00"))

            assertEquals(
                listOf(
                    "08:00 Wasser trinken: Done", "12:00 Wasser trinken: Dropped", "20:00 Wasser trinken: Dropped",
                    "21:00 Lesen: Open",
                ),
                dueOn(today),
            )
            assertEquals(emptyList<Reminder>(), routines.remindersDue(after = clock.now, until = berlin("2026-10-23T20:30")).filter {
                it.name == "Wasser trinken"
            })
        }

    @Test
    fun `Heute nicht also drops the occurrence of yesterday still in its time window, and leaves one missed as it is`() =
        runBlocking {
            routines.create(draft("Lesen", "08:00", "22:00", window = TimeWindow(hours = 4)))
            clock.now = berlin("2026-10-24T01:00")
            routines.markMissed(since = berlin("2026-10-23T05:00"))
            val tomorrow = today.plusDays(1)
            val yesterdayLate = routines.dueOn(tomorrow).first().first { it.dueAt.toLocalDate() == today }.occurrenceId

            routines.dropForToday(yesterdayLate)

            assertEquals(listOf("22:00 Lesen: Dropped", "08:00 Lesen: Dropped", "22:00 Lesen: Dropped"), dueOn(tomorrow))
            assertEquals(listOf("08:00 Lesen: Missed", "22:00 Lesen: Dropped"), dueOn(today))

            // Undone once yesterday's time window has ended: that one is missed, today's are open again.
            clock.now = berlin("2026-10-24T02:30")
            routines.reopen(routines.dueOn(tomorrow).first().first().occurrenceId)
            assertEquals(listOf("08:00 Lesen: Open", "22:00 Lesen: Open"), dueOn(tomorrow))
            assertEquals(listOf("08:00 Lesen: Missed", "22:00 Lesen: Missed"), dueOn(today))
        }

    @Test
    fun `Heute nicht leaves skipped occurrences as they are, and does nothing from one already answered`() = runBlocking {
        routines.create(draft("Wasser trinken", "08:00", "12:00", "20:00"))
        routines.skip(occurrenceAt("08:00"))
        routines.markDone(occurrenceAt("12:00"))

        routines.dropForToday(occurrenceAt("12:00"))
        assertEquals(
            listOf("08:00 Wasser trinken: Skipped", "12:00 Wasser trinken: Done", "20:00 Wasser trinken: Open"),
            dueOn(today),
        )

        routines.dropForToday(occurrenceAt("20:00"))
        assertEquals(
            listOf("08:00 Wasser trinken: Skipped", "12:00 Wasser trinken: Done", "20:00 Wasser trinken: Dropped"),
            dueOn(today),
        )
    }

    @Test
    fun `a time added to a routine dropped for the day is dropped too, and comes from tomorrow`() = runBlocking {
        val water = routines.create(draft("Wasser trinken", "12:00"))
        routines.dropForToday(occurrenceAt("12:00"))

        routines.update(water, draft("Wasser trinken", "12:00", "18:00"))

        assertEquals(listOf("12:00 Wasser trinken: Dropped", "18:00 Wasser trinken: Dropped"), dueOn(today))
        assertEquals(listOf("12:00 Wasser trinken: Open", "18:00 Wasser trinken: Open"), dueOn(today.plusDays(1)))
    }

    @Test
    fun `undoing Heute nicht opens again what is still in its time window and misses the rest`() = runBlocking {
        routines.create(draft("Wasser trinken", "12:00", "20:00", window = TimeWindow(hours = 2)))
        clock.now = berlin("2026-10-23T13:00")
        routines.dropForToday(occurrenceAt("12:00"))
        clock.now = berlin("2026-10-23T15:00")

        routines.reopen(occurrenceAt("20:00"))

        assertEquals(listOf("12:00 Wasser trinken: Missed", "20:00 Wasser trinken: Open"), dueOn(today))
    }

    @Test
    fun `a dropped occurrence is not missed later, and its checklist cannot be ticked`() = runBlocking {
        routines.create(draft("Yoga", "18:00", checklist = listOf("Matte")))
        routines.dropForToday(occurrenceAt("18:00"))
        val item = routines.dueOn(today).first().single().checklist.single().itemId

        routines.tick(occurrenceAt("18:00"), item, ticked = true)
        clock.now = berlin("2026-10-24T08:00")
        routines.markMissed(since = berlin("2026-10-23T07:00"))

        assertEquals(listOf("18:00 Yoga: Dropped"), dueOn(today))
        assertEquals(listOf("Matte" to false), checklistOn(today))
    }

    @Test
    fun `a routine ending on a date is due up to that day, that day included, and not after`() = runBlocking {
        routines.create(draft("Kur", "08:00", endsOn = today.plusDays(1)))

        assertEquals(listOf("08:00 Kur: Open"), dueOn(today))
        assertEquals(listOf("08:00 Kur: Open"), dueOn(today.plusDays(1)))
        assertEquals(emptyList<String>(), dueOn(today.plusDays(2)))
    }

    @Test
    fun `after its last day, once its occurrences have ended, a routine goes to the archive, keeping its history`() =
        runBlocking {
            val cure = routines.create(draft("Kur", "22:00", endsOn = today, window = TimeWindow(hours = 4)))
            routines.markDone(occurrenceAt("22:00"))

            clock.now = berlin("2026-10-23T23:00")
            routines.markMissed(since = clock.now)
            assertEquals(RoutineStatus.Active, routines.get(cure)?.status)

            clock.now = berlin("2026-10-24T00:30")
            routines.markMissed(since = clock.now)
            assertEquals(RoutineStatus.Archived, routines.get(cure)?.status)
            assertEquals(listOf(OccurrenceStatus.Done), routines.history().first().map { it.status })
        }

    @Test
    fun `a routine still open from its last day goes to the archive once that is missed`() = runBlocking {
        val cure = routines.create(draft("Kur", "22:00", endsOn = today, window = TimeWindow(hours = 4)))

        clock.now = berlin("2026-10-24T01:00")
        routines.markMissed(since = berlin("2026-10-23T07:00"))
        assertEquals("Its time window runs on past midnight", RoutineStatus.Active, routines.get(cure)?.status)

        clock.now = berlin("2026-10-24T02:00")
        routines.markMissed(since = berlin("2026-10-24T01:00"))
        assertEquals(RoutineStatus.Archived, routines.get(cure)?.status)
        assertEquals(listOf(OccurrenceStatus.Missed), routines.history().first().map { it.status })
    }

    @Test
    fun `an end is not before today, and a one-time routine has none`() = runBlocking {
        assertEquals(setOf(DraftProblem.EndDateTooEarly), draft("Kur", "08:00", endsOn = today.minusDays(1)).problemsOn(today))
        assertEquals(emptySet<DraftProblem>(), draft("Kur", "08:00", endsOn = today).problemsOn(today))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { routines.create(draft("Kur", "08:00", endsOn = today.minusDays(1))) }
        }
        val once = draft("Arzt", "08:00", repetition = Repetition.Once(today), endsOn = today.plusDays(3))
        assertEquals(null, once.cleaned().endsOn)
    }

    @Test
    fun `all routines are listed by time, then by name`() = runBlocking {
        routines.create(draft("Tagebuch", "20:00", description = "Drei gute Dinge"))
        routines.create(draft("Übungen", "07:30"))
        routines.create(draft("Atmen", "07:30"))

        val listed = routines.inUse().first()

        assertEquals(listOf("Atmen", "Übungen", "Tagebuch"), listed.map { it.name })
        assertEquals("Drei gute Dinge", listed.last().description)
    }

    @Test
    fun `a routine is saved with cleaned texts, and an invalid one is refused`() = runBlocking {
        routines.create(draft("  Zähne\nputzen ", "08:00", description = " Gründlich "))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { routines.create(draft("   ", "09:00")) }
        }
        assertEquals(listOf("Zähne putzen" to "Gründlich"), routines.inUse().first().map { it.name to it.description })
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

        assertEquals(listOf("Lesen"), routines.inUse().first().map { it.name })
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

    @Test
    fun `the next reminder is the earliest of all routines`() = runBlocking {
        assertEquals(null, routines.nextAlarm(after = clock.now))

        routines.create(draft("Tagebuch", "20:00"))
        routines.create(draft("Vitamine", "07:30"))

        assertEquals(berlin("2026-10-23T07:30"), routines.nextAlarm(after = clock.now))
        // After the three follow-ups of 07:30 and the end of its time window.
        assertEquals(berlin("2026-10-23T20:00"), routines.nextAlarm(after = berlin("2026-10-23T11:30")))
    }

    @Test
    fun `an occurrence put off is reminded of again then, and not at its time any more`() = runBlocking {
        routines.create(draft("Vitamine", "07:30"))
        clock.now = berlin("2026-10-23T07:31")

        routines.putOff(occurrenceAt("07:30"), until = berlin("2026-10-23T07:41"))

        assertEquals(berlin("2026-10-23T07:41"), routines.nextAlarm(after = clock.now))
        val atItsTime = routines.remindersDue(after = berlin("2026-10-23T07:29"), until = berlin("2026-10-23T07:31"))
        val putOffTo = routines.remindersDue(after = berlin("2026-10-23T07:40"), until = berlin("2026-10-23T07:42"))
        assertEquals(emptyList<String>(), atItsTime.map { it.name })
        assertEquals(listOf("Vitamine"), putOffTo.map { it.name })
    }

    @Test
    fun `the reminders due are the open occurrences come due in that time, on a day not planned yet too`() = runBlocking {
        routines.create(draft("Vitamine", "07:30"))
        routines.create(draft("Lüften", "07:30", "12:00"))
        routines.create(draft("Blumen", "07:00", followUps = FollowUps(count = 0)))
        routines.markDone(occurrenceAt("07:30") { it.name == "Lüften" })
        val tomorrow = berlin("2026-10-24T07:30")

        val due = routines.remindersDue(after = berlin("2026-10-23T07:00"), until = berlin("2026-10-23T07:31"))
        val dueTomorrow = routines.remindersDue(after = tomorrow.minusSeconds(1), until = tomorrow)

        assertEquals(listOf("Vitamine" to "07:30"), due.map { it.name to it.dueAt.toLocalTime().toString() })
        assertEquals(listOf("Lüften", "Vitamine"), dueTomorrow.map { it.name })
    }

    @Test
    fun `a follow-up is due like the first reminder, also one on the day after`() = runBlocking {
        routines.create(draft("Lesen", "23:50"))

        val first = routines.remindersDue(after = berlin("2026-10-23T23:49"), until = berlin("2026-10-23T23:50"))
        val followUp = routines.remindersDue(after = berlin("2026-10-24T00:19"), until = berlin("2026-10-24T00:20"))

        assertEquals(listOf("Lesen"), first.map { it.name })
        assertEquals(listOf(LocalDate.parse("2026-10-23")), followUp.map { it.dueAt.toLocalDate() })
        assertEquals(berlin("2026-10-24T00:50"), routines.nextAlarm(after = berlin("2026-10-24T00:20")))
    }

    @Test
    fun `a routine is saved with how it reminds and how long it stays open, and that changes with it`() = runBlocking {
        val fiveTimes = FollowUps(count = 5, everyMinutes = 10)
        val untilMidnight = TimeWindow(untilMidnight = true)
        val pills = routines.create(draft("Tabletten", "08:00", followUps = fiveTimes, fullScreen = true, window = untilMidnight))
        assertEquals(Triple(fiveTimes, true, untilMidnight), howItReminds(pills))

        val once = FollowUps(count = 1, everyMinutes = 60)
        val twoHours = TimeWindow(hours = 2)
        routines.update(pills, draft("Tabletten", "08:00", followUps = once, fullScreen = false, window = twoHours))

        assertEquals(Triple(once, false, twoHours), howItReminds(pills))
    }

    @Test
    fun `a paused routine leaves Heute with nothing to come, its open occurrences go, while what was ticked off stays`() =
        runBlocking {
            val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
            routines.markDone(occurrenceAt("08:00"))
            clock.now = noon

            routines.pause(pills)

            assertEquals(RoutineStatus.Paused, routines.get(pills)?.status)
            assertEquals(emptyList<String>(), dueOn(today))
            assertEquals(emptyList<String>(), dueOn(today.plusDays(1)))
            assertEquals(listOf(OccurrenceStatus.Done), database.routines().occurrencesOn(pills, today).map { it.status })
        }

    @Test
    fun `pausing marks missed what ran out of its time window before, instead of taking it away`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        // The alarm at the end of the 08:00 window has not gone off yet.
        clock.now = berlin("2026-10-23T12:30")

        routines.pause(pills)

        assertEquals(listOf(OccurrenceStatus.Missed), database.routines().occurrencesOn(pills, today).map { it.status })
    }

    @Test
    fun `what a paused routine did cannot be undone or unticked until it is resumed, so nothing comes due while it is paused`() =
        runBlocking {
            val pills = routines.create(draft("Tabletten", "08:00", "20:00", checklist = listOf("Wasser")))
            val morning = occurrenceAt("08:00")
            routines.markDone(morning)
            clock.now = noon
            routines.pause(pills)

            routines.reopen(morning)
            routines.tick(morning, routines.checklist(pills).single().id, ticked = false)

            assertEquals(listOf(OccurrenceStatus.Done), database.routines().occurrencesOn(pills, today).map { it.status })
            assertEquals(null, routines.nextAlarm(after = clock.now))
        }

    @Test
    fun `a paused routine is not reminded of, and its paused days are not missed`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00"))
        routines.pause(pills)
        clock.now = berlin("2026-10-26T12:00")

        routines.markMissed(since = berlin("2026-10-23T07:00"))

        assertEquals(null, routines.nextAlarm(after = clock.now))
        assertEquals(emptyList<String>(), (0L..3L).flatMap { dueOn(today.plusDays(it)) })
    }

    @Test
    fun `a routine resumed is due again from then on, with nothing overdue`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        routines.pause(pills)
        clock.now = berlin("2026-10-24T12:00")

        routines.resume(pills)

        assertEquals(RoutineStatus.Active, routines.get(pills)?.status)
        assertEquals(listOf("20:00 Tabletten: Open"), dueOn(today.plusDays(1)))
        assertEquals(berlin("2026-10-24T20:00"), routines.nextAlarm(after = clock.now))
    }

    @Test
    fun `a routine resumed the same day is due again at its times still ahead`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        routines.markDone(occurrenceAt("08:00"))
        clock.now = noon
        routines.pause(pills)

        routines.resume(pills)

        assertEquals(listOf("08:00 Tabletten: Done", "20:00 Tabletten: Open"), dueOn(today))
    }

    @Test
    fun `an archived routine leaves the list and Heute, and once restored, what it had done is still there`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00", "20:00"))
        routines.create(draft("Lesen", "21:00"))
        routines.markDone(occurrenceAt("08:00") { it.name == "Tabletten" })
        clock.now = noon

        routines.archive(pills)

        assertEquals(listOf("Lesen"), routines.inUse().first().map { it.name })
        assertEquals(listOf("Tabletten"), routines.archived().first().map { it.name })
        assertEquals(listOf("21:00 Lesen: Open"), dueOn(today))
        assertEquals(listOf("21:00 Lesen: Open"), dueOn(today.plusDays(1)))

        routines.restore(pills)

        assertEquals(listOf("Tabletten", "Lesen"), routines.inUse().first().map { it.name })
        assertEquals(emptyList<Routine>(), routines.archived().first())
        assertEquals(listOf("08:00 Tabletten: Done", "20:00 Tabletten: Open", "21:00 Lesen: Open"), dueOn(today))
    }

    @Test
    fun `the history has every routine's occurrences as they ended, an archived one's too, but not a deleted one's`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00"))
        val reading = routines.create(draft("Lesen", "21:00"))
        routines.markDone(occurrenceAt("08:00"))
        routines.skip(occurrenceAt("21:00"))
        routines.archive(reading)

        val history = routines.history().first().map { it.routineId to it.status }
        assertEquals(setOf(pills to OccurrenceStatus.Done, reading to OccurrenceStatus.Skipped), history.toSet())

        routines.delete(reading)
        assertEquals(listOf(pills to OccurrenceStatus.Done), routines.history().first().map { it.routineId to it.status })
    }

    @Test
    fun `a routine is saved with its checklist, whose items keep their ticks while it is changed`() = runBlocking {
        val teeth = routines.create(draft("Zähne", "08:00", checklist = listOf("Putzen", "Zahnseide", "Spülen")))
        val (brush, floss, rinse) = routines.checklist(teeth)
        routines.tick(occurrenceAt("08:00"), brush.id, ticked = true)

        val changed = listOf(
            ChecklistDraftItem(rinse.id, "Mundspülung"), ChecklistDraftItem(brush.id, "Putzen"), ChecklistDraftItem(text = "Zunge"),
        )
        routines.update(teeth, draft("Zähne", "08:00").copy(checklist = changed))

        assertEquals(listOf("Mundspülung", "Putzen", "Zunge"), routines.checklist(teeth).map { it.text })
        assertEquals(listOf("Mundspülung" to false, "Putzen" to true, "Zunge" to false), checklistOn(today))
        assertEquals(false, floss.id in routines.checklist(teeth).map { it.id })
    }

    @Test
    fun `ticking off every item of its checklist ticks the routine off, and unticking one opens it again`() = runBlocking {
        val teeth = routines.create(draft("Zähne", "08:00", checklist = listOf("Putzen", "Zahnseide")))
        val (brush, floss) = routines.checklist(teeth)
        val morning = occurrenceAt("08:00")

        routines.tick(morning, brush.id, ticked = true)
        assertEquals(listOf("08:00 Zähne: Open"), dueOn(today))
        routines.tick(morning, floss.id, ticked = true)
        assertEquals(listOf("08:00 Zähne: Done"), dueOn(today))

        routines.tick(morning, brush.id, ticked = false)
        assertEquals(listOf("08:00 Zähne: Open"), dueOn(today))
        assertEquals(listOf("Putzen" to false, "Zahnseide" to true), checklistOn(today))
    }

    @Test
    fun `a routine ticked off as a whole counts its checklist as ticked off, unticking an item leaves the others`() = runBlocking {
        val teeth = routines.create(draft("Zähne", "08:00", checklist = listOf("Putzen", "Zahnseide")))
        val (brush, _) = routines.checklist(teeth)
        routines.markDone(occurrenceAt("08:00"))
        assertEquals(listOf("Putzen" to true, "Zahnseide" to true), checklistOn(today))

        routines.tick(occurrenceAt("08:00"), brush.id, ticked = false)

        assertEquals(listOf("08:00 Zähne: Open"), dueOn(today))
        assertEquals(listOf("Putzen" to false, "Zahnseide" to true), checklistOn(today))
    }

    @Test
    fun `unticking a routine ticked off through its checklist unticks the checklist too`() = runBlocking {
        val teeth = routines.create(draft("Zähne", "08:00", checklist = listOf("Putzen", "Zahnseide")))
        routines.checklist(teeth).forEach { routines.tick(occurrenceAt("08:00"), it.id, ticked = true) }

        routines.reopen(occurrenceAt("08:00"))

        assertEquals(listOf("08:00 Zähne: Open"), dueOn(today))
        assertEquals(listOf("Putzen" to false, "Zahnseide" to false), checklistOn(today))
    }

    @Test
    fun `taking away the only item left unticked ticks the routine off`() = runBlocking {
        val teeth = routines.create(draft("Zähne", "08:00", checklist = listOf("Putzen", "Zahnseide")))
        val (brush, _) = routines.checklist(teeth)
        routines.tick(occurrenceAt("08:00"), brush.id, ticked = true)

        routines.update(teeth, draft("Zähne", "08:00").copy(checklist = listOf(ChecklistDraftItem(brush.id, "Putzen"))))

        assertEquals(listOf("08:00 Zähne: Done"), dueOn(today))
    }

    @Test
    fun `the checklist of a routine skipped or missed cannot be ticked`() = runBlocking {
        val teeth = routines.create(draft("Zähne", "08:00", "20:00", checklist = listOf("Putzen")))
        val (brush) = routines.checklist(teeth)
        routines.skip(occurrenceAt("20:00"))
        clock.now = berlin("2026-10-23T12:00")
        routines.markMissed(since = clock.now)

        routines.tick(occurrenceAt("08:00"), brush.id, ticked = true)
        routines.tick(occurrenceAt("20:00"), brush.id, ticked = true)

        assertEquals(listOf("08:00 Zähne: Missed", "20:00 Zähne: Skipped"), dueOn(today))
        assertEquals(listOf("Putzen" to false, "Putzen" to false), checklistOn(today))
    }

    @Test
    fun `a routine is saved with its tags, which change with it, leaving out those deleted meanwhile`() = runBlocking {
        val tags = Tags({ database }, Dispatchers.Unconfined)
        val work = tags.create("Arbeit")
        val health = tags.create("Gesundheit")
        val gone = tags.create("Alt")
        tags.delete(gone)

        val pills = routines.create(draft("Tabletten", "08:00", tags = setOf(health, gone)))
        assertEquals(setOf(health), routines.tagIdsOf(pills))

        routines.update(pills, draft("Tabletten", "08:00", tags = setOf(work, health)))
        assertEquals(setOf(work, health), routines.tagIdsOf(pills))
        assertEquals(mapOf(pills to setOf(work, health)), routines.tagIdsByRoutine().first())
    }

    @Test
    fun `a routine has normal priority unless it is given another, which changes with it`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00"))
        assertEquals(Priority.Normal, routines.get(pills)?.priority)

        routines.update(pills, draft("Tabletten", "08:00", priority = Priority.High))

        assertEquals(Priority.High, routines.get(pills)?.priority)
        assertEquals(listOf(Priority.High), routines.dueOn(today).first().map { it.priority })
    }

    @Test
    fun `an occurrence left open until its time window ends is missed, while those ticked off or skipped stay`() = runBlocking {
        routines.create(draft("Tabletten", "08:00", "09:00", "10:00", window = TimeWindow(hours = 2)))
        routines.markDone(occurrenceAt("08:00"))
        routines.skip(occurrenceAt("09:00"))

        clock.now = berlin("2026-10-23T11:59")
        routines.markMissed(since = clock.now)
        assertEquals(listOf("08:00 Tabletten: Done", "09:00 Tabletten: Skipped", "10:00 Tabletten: Open"), dueOn(today))

        clock.now = berlin("2026-10-23T12:00")
        routines.markMissed(since = clock.now)
        assertEquals(listOf("08:00 Tabletten: Done", "09:00 Tabletten: Skipped", "10:00 Tabletten: Missed"), dueOn(today))
    }

    @Test
    fun `a missed occurrence stays missed, whether ticked off, skipped or undone`() = runBlocking {
        routines.create(draft("Vitamine", "08:00"))
        val vitamins = occurrenceAt("08:00")
        clock.now = berlin("2026-10-23T12:00")
        routines.markMissed(since = clock.now)

        routines.markDone(vitamins)
        routines.skip(vitamins)
        routines.reopen(vitamins)

        assertEquals(listOf("08:00 Vitamine: Missed"), dueOn(today))
    }

    @Test
    fun `until midnight, an occurrence is missed once its day is over`() = runBlocking {
        routines.create(draft("Lesen", "21:00", window = TimeWindow(untilMidnight = true)))

        clock.now = berlin("2026-10-23T23:59")
        routines.markMissed(since = clock.now)
        assertEquals(listOf("21:00 Lesen: Open"), dueOn(today))

        clock.now = berlin("2026-10-24T00:00")
        routines.markMissed(since = clock.now)
        assertEquals(listOf("21:00 Lesen: Missed"), dueOn(today))
    }

    @Test
    fun `the days since the time given count too, say while the phone was off, with what is still open left open`() = runBlocking {
        routines.create(draft("Vitamine", "08:00"))
        clock.now = berlin("2026-10-26T10:00") // Monday; off since Friday 07:00

        routines.markMissed(since = berlin("2026-10-23T07:00"))

        val statuses = (0L..3L).map { dueOn(today.plusDays(it)).single().substringAfter(": ") }
        assertEquals(listOf("Missed", "Missed", "Missed", "Open"), statuses)
    }

    @Test
    fun `marking occurrences missed is not reported as a change, since the reminders do it themselves`() = runBlocking {
        routines.create(draft("Vitamine", "08:00"))
        val before = changes
        clock.now = berlin("2026-10-23T12:00")

        routines.markMissed(since = clock.now)

        assertEquals(before, changes)
    }

    @Test
    fun `the next alarm is also when the time window of an open occurrence ends`() = runBlocking {
        routines.create(draft("Vitamine", "08:00", followUps = FollowUps(count = 0), window = TimeWindow(hours = 3)))

        assertEquals(berlin("2026-10-23T08:00"), routines.nextAlarm(after = clock.now))
        assertEquals(berlin("2026-10-23T11:00"), routines.nextAlarm(after = berlin("2026-10-23T08:00")))

        routines.markDone(occurrenceAt("08:00"))
        assertEquals(berlin("2026-10-24T08:00"), routines.nextAlarm(after = berlin("2026-10-23T08:00")))
    }

    @Test
    fun `no reminder is due once the time window has ended`() = runBlocking {
        val hourly = FollowUps(count = 5, everyMinutes = 60)
        routines.create(draft("Vitamine", "08:00", followUps = hourly, window = TimeWindow(hours = 2)))

        val followUps = routines.remindersDue(after = berlin("2026-10-23T08:00"), until = berlin("2026-10-23T09:00"))
        val pastTheWindow = routines.remindersDue(after = berlin("2026-10-23T09:00"), until = berlin("2026-10-23T13:00"))

        assertEquals(listOf("Vitamine"), followUps.map { it.name })
        assertEquals(emptyList<Reminder>(), pastTheWindow)
    }

    @Test
    fun `of the occurrences given, those still open and come due are the ones to remind of`() = runBlocking {
        routines.create(draft("Tabletten", "07:00", "07:30", "20:00"))
        val (early, done, later) = routines.dueOn(today).first().map { it.occurrenceId }
        routines.markDone(done)
        clock.now = berlin("2026-10-23T08:00")

        assertEquals(listOf(early), routines.stillDue(listOf(early, done, later, 999)).map { it.occurrenceId })
    }

    @Test
    fun `an occurrence moved to another time is reminded of at that time, even if it was put off`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "07:30"))
        routines.putOff(occurrenceAt("07:30"), until = berlin("2026-10-23T07:40"))

        routines.update(pills, draft("Tabletten", "09:00"))

        assertEquals(berlin("2026-10-23T09:00"), routines.nextAlarm(after = clock.now))
    }

    @Test
    fun `every change to routines and occurrences is reported`() = runBlocking {
        val pills = routines.create(draft("Tabletten", "08:00"))
        val occurrence = occurrenceAt("08:00")
        routines.update(pills, draft("Tabletten", "08:30"))
        routines.markDone(occurrence)
        routines.reopen(occurrence)
        routines.skip(occurrence)
        routines.putOff(occurrence, until = berlin("2026-10-23T09:00"))
        routines.delete(pills)

        assertEquals(7, changes)
    }

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(clock.zone).toInstant()

    /** The reminders due, for routines bound to no place, so that where the device is does not matter. */
    private suspend fun Routines.remindersDue(after: Instant, until: Instant) = remindersDue(after, until) { Whereabouts.Unknown }

    private fun draft(
        name: String,
        vararg times: String,
        description: String = "",
        repetition: Repetition = Repetition.Daily,
        followUps: FollowUps = FollowUps(),
        fullScreen: Boolean = false,
        window: TimeWindow = TimeWindow(),
        priority: Priority = Priority.Normal,
        checklist: List<String> = emptyList(),
        tags: Set<Long> = emptySet(),
        endsOn: LocalDate? = null,
    ) = RoutineDraft(
        name, description, times.map(LocalTime::parse), repetition, followUps, fullScreen, window, priority,
        checklist.map { ChecklistDraftItem(text = it) }, tags, endsOn = endsOn,
    )

    /** Each checklist item of the occurrences on Heute on [date], and whether it is ticked off there. */
    private suspend fun checklistOn(date: LocalDate) =
        routines.dueOn(date).first().flatMap { routine -> routine.checklist.map { it.text to it.ticked } }

    /** The routine's follow-ups, full-screen alarm and time window, as saved. */
    private suspend fun howItReminds(id: Long) = routines.get(id)?.let { Triple(it.followUps, it.fullScreen, it.window) }

    /** The id of today's occurrence at [time]. */
    private suspend fun occurrenceAt(time: String, which: (DueRoutine) -> Boolean = { true }) =
        routines.dueOn(today).first().single { it.dueAt.toLocalTime() == LocalTime.parse(time) && which(it) }.occurrenceId

    /** What the Heute list shows on [date], in its order. */
    private suspend fun dueOn(date: LocalDate) =
        routines.dueOn(date).first().map { "${it.dueAt.toLocalTime()} ${it.name}: ${it.status}" }
}
