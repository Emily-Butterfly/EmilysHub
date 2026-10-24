package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.PlaceDraft
import de.emilyshub.places.Places
import de.emilyshub.places.Whereabouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Routines bound to places (SPEC.md §4): when one pops up, by its time, where the device is, and its time window. */
@RunWith(AndroidJUnit4::class)
class PlaceBoundTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)
    private val places = Places({ database }, Dispatchers.Unconfined)
    private val home = runBlocking { places.create(PlaceDraft("Zuhause", listOf("FritzBox"))) }
    private val work = runBlocking { places.create(PlaceDraft("Arbeit", listOf("Firma"))) }
    private val gym = runBlocking { places.create(PlaceDraft("Gym", listOf("FitX"))) }
    private val elsewhere = Whereabouts.At(emptySet())

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a routine is saved with its places, which change with it, and loses one deleted`() = runBlocking {
        val pills = routines.create(pills(at = setOf(home, work)))
        assertEquals(setOf(home, work), routines.placeIdsOf(pills))

        routines.update(pills, pills(at = setOf(work, gym)))
        places.delete(gym)

        assertEquals(setOf(work), routines.placeIdsOf(pills))
    }

    @Test
    fun `at one of its places at its time, a routine pops up at once`() = runBlocking {
        routines.create(pills(at = setOf(home, work)))

        assertEquals(listOf("Tabletten"), dueAt("08:00", Whereabouts.At(setOf(work))))
        assertEquals(listOf<List<String>>(emptyList()), waitingFor())
    }

    @Test
    fun `elsewhere at its time, it waits, and Heute says for which of its places`() = runBlocking {
        routines.create(pills(at = setOf(home, work)))

        assertEquals(emptyList<String>(), dueAt("08:00", elsewhere))
        assertEquals(listOf(listOf("Arbeit", "Zuhause")), waitingFor())
    }

    @Test
    fun `arriving at one of its places while its time window is open, it pops up then, its follow-ups counted from there`() =
        runBlocking {
            routines.create(pills(at = setOf(home, work)))
            dueAt("08:00", elsewhere)
            clock.now = berlin("2026-10-23T09:10")

            assertEquals(emptyList<String>(), routines.arrive(Whereabouts.At(setOf(gym))).map { it.name })
            assertEquals(listOf("Tabletten"), routines.arrive(Whereabouts.At(setOf(home))).map { it.name })

            assertEquals(listOf<List<String>>(emptyList()), waitingFor())
            assertEquals(berlin("2026-10-23T09:40"), routines.nextAlarm(after = clock.now))
        }

    @Test
    fun `arriving once its time window has ended, it is missed and nothing pops up`() = runBlocking {
        routines.create(pills(at = setOf(home), window = TimeWindow(hours = 2)))
        dueAt("08:00", elsewhere)
        clock.now = berlin("2026-10-23T10:30")

        routines.markMissed(since = berlin("2026-10-23T08:00"))

        assertEquals(emptyList<String>(), routines.arrive(Whereabouts.At(setOf(home))).map { it.name })
        assertEquals(listOf(OccurrenceStatus.Missed), routines.dueOn(today).first().map { it.status })
    }

    @Test
    fun `arriving once its time window has ended, nothing pops up, also before it is marked missed`() = runBlocking {
        routines.create(pills(at = setOf(home), window = TimeWindow(hours = 2)))
        dueAt("08:00", elsewhere)
        clock.now = berlin("2026-10-23T10:30")

        assertEquals(emptyList<String>(), routines.arrive(Whereabouts.At(setOf(home))).map { it.name })
    }

    @Test
    fun `caught up on until midnight, it pops up on arrival before midnight, and after it no more`() = runBlocking {
        routines.create(draft("Tabletten", "20:00", TimeWindow(untilMidnight = true)).copy(placeIds = setOf(home)))
        dueAt("20:00", elsewhere)

        clock.now = berlin("2026-10-24T00:10")
        assertEquals(emptyList<String>(), routines.arrive(Whereabouts.At(setOf(home))).map { it.name })

        clock.now = berlin("2026-10-23T23:50")
        assertEquals(listOf("Tabletten"), routines.arrive(Whereabouts.At(setOf(home))).map { it.name })
    }

    @Test
    fun `with a time window past midnight, it pops up on arrival after midnight, and Heute shows it from the day before`() =
        runBlocking {
            routines.create(draft("Lesen", "22:00").copy(placeIds = setOf(home)))
            dueAt("22:00", elsewhere)
            clock.now = berlin("2026-10-24T01:30")

            val fromTheDayBefore = routines.dueOn(today.plusDays(1)).first().single { it.dueAt.toLocalDate() == today }
            assertEquals(listOf("Zuhause"), fromTheDayBefore.waitingFor)
            assertEquals(listOf("Lesen"), routines.arrive(Whereabouts.At(setOf(home))).map { it.name })
        }

    @Test
    fun `joining a WLAN whose name Android keeps back, what waits pops up, as it is not to be held back for want of it`() =
        runBlocking {
            routines.create(pills(at = setOf(home)))
            dueAt("08:00", elsewhere)

            assertEquals(listOf("Tabletten"), routines.arrive(Whereabouts.Unknown).map { it.name })
            assertEquals(listOf<List<String>>(emptyList()), waitingFor())
        }

    @Test
    fun `put off once it has popped up, it comes back also away from its places`() = runBlocking {
        routines.create(pills(at = setOf(home)))
        dueAt("08:00", Whereabouts.At(setOf(home)))
        routines.putOff(occurrence(), until = berlin("2026-10-23T08:10"))

        assertEquals(listOf("Tabletten"), dueAt("08:10", elsewhere))
    }

    @Test
    fun `bound to no place any more, what waited pops up now`() = runBlocking {
        val pills = routines.create(pills(at = setOf(home)))
        dueAt("08:00", elsewhere)
        clock.now = berlin("2026-10-23T08:05")

        routines.update(pills, pills(at = emptySet()))
        routines.releaseFromPlaces()

        assertEquals(listOf<List<String>>(emptyList()), waitingFor())
        assertEquals(listOf("Tabletten"), dueAt("08:05", elsewhere))
    }

    @Test
    fun `with its only place deleted, what waited for it pops up now`() = runBlocking {
        routines.create(pills(at = setOf(home)))
        dueAt("08:00", elsewhere)
        clock.now = berlin("2026-10-23T08:05")

        places.delete(home)
        routines.releaseFromPlaces()

        assertEquals(listOf("Tabletten"), dueAt("08:05", elsewhere))
    }

    @Test
    fun `waiting, it pops up at a follow-up once it is at its place, should the arrival have gone unnoticed`() = runBlocking {
        routines.create(pills(at = setOf(home)))
        dueAt("08:00", elsewhere)

        assertEquals(listOf("Tabletten"), dueAt("08:30", Whereabouts.At(setOf(home))))
        assertEquals(listOf<List<String>>(emptyList()), waitingFor())
    }

    @Test
    fun `waiting, its follow-ups away from its places come no more than the first reminder`() = runBlocking {
        routines.create(pills(at = setOf(home)))
        dueAt("08:00", elsewhere)

        assertEquals(emptyList<String>(), dueAt("08:30", elsewhere))
        assertEquals(listOf(listOf("Zuhause")), waitingFor())
    }

    @Test
    fun `once it has popped up, its follow-ups come also away from its places`() = runBlocking {
        routines.create(pills(at = setOf(home)))
        dueAt("08:00", Whereabouts.At(setOf(home)))

        assertEquals(listOf("Tabletten"), dueAt("08:30", elsewhere))
    }

    @Test
    fun `a routine bound to no place pops up everywhere, and one bound to places too while the app cannot tell where it is`() =
        runBlocking {
            routines.create(draft("Lesen", "08:00").copy(followUps = FollowUps(count = 0)))
            routines.create(draft("Tabletten", "09:00").copy(placeIds = setOf(home)))

            assertEquals(listOf("Lesen"), dueAt("08:00", elsewhere))
            assertEquals(listOf("Tabletten"), dueAt("09:00", Whereabouts.Unknown))
        }

    @Test
    fun `where the device is, is only asked once a routine bound to a place comes due`() = runBlocking {
        routines.create(draft("Lesen", "08:00"))
        var asked = 0

        routines.remindersDue(after = berlin("2026-10-23T07:59"), until = berlin("2026-10-23T08:00")) { asked++; elsewhere }

        assertEquals(0, asked)
    }

    /** What pops up at [time] on the 23rd, with the device at [whereabouts]. */
    private suspend fun dueAt(time: String, whereabouts: Whereabouts): List<String> {
        val until = berlin("2026-10-23T$time")
        return routines.remindersDue(after = until.minusSeconds(60), until = until) { whereabouts }.map { it.name }
    }

    /** Today's only occurrence. */
    private suspend fun occurrence() = routines.dueOn(today).first().single().occurrenceId

    /** For each occurrence on Heute, the places it waits for. */
    private suspend fun waitingFor() = routines.dueOn(today).first().map { it.waitingFor }

    private fun pills(at: Set<Long>, window: TimeWindow = TimeWindow()) = draft("Tabletten", "08:00", window).copy(placeIds = at)

    private fun draft(name: String, time: String, window: TimeWindow = TimeWindow()) =
        RoutineDraft(name, description = "", listOf(LocalTime.parse(time)), Repetition.Daily, window = window)

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(clock.zone).toInstant()
}
