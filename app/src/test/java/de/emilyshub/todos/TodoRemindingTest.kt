package de.emilyshub.todos

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.PlaceDraft
import de.emilyshub.places.Places
import de.emilyshub.places.Whereabouts
import de.emilyshub.routines.ChecklistDraftItem
import kotlinx.coroutines.Dispatchers
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

/**
 * When a to-do reminds (SPEC.md §3, §4, §5): at its time, and bound to places at its time if there, otherwise on
 * arriving at one, also without a time. It waits until it is done, and is never missed.
 */
@RunWith(AndroidJUnit4::class)
class TodoRemindingTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val places = Places({ database }, Dispatchers.Unconfined)
    private val home = runBlocking { places.create(PlaceDraft("Zuhause", listOf("FritzBox"))) }
    private val work = runBlocking { places.create(PlaceDraft("Arbeit", listOf("Firma"))) }
    private val atHome = Whereabouts.At(setOf(home))
    private val elsewhere = Whereabouts.At(emptySet())

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a to-do with a time still ahead reminds at it, one saved after its time does not`() = runBlocking {
        todo("Arzt", Due.At(today, LocalTime.of(9, 0)))
        todo("Zu spät", Due.At(today, LocalTime.of(6, 30)))

        assertEquals(berlin("2026-10-23T09:00"), todos.nextAlarm(after = clock.now))
        assertEquals(listOf("Arzt"), dueBetween("2026-10-23T06:00", "2026-10-23T09:00", elsewhere))
    }

    @Test
    fun `a to-do due on a day or whenever, bound to no place, does not pop up`() = runBlocking {
        todo("Müll", Due.On(today))
        todo("Fenster")

        assertEquals(null, todos.nextAlarm(after = clock.now))
        assertEquals(emptyList<String>(), dueBetween("2026-10-23T06:00", "2026-10-24T09:00", elsewhere))
        assertEquals(emptyList<String>(), arrive(atHome))
    }

    @Test
    fun `bound to a place, at its time it pops up there, and elsewhere waits to pop up on arrival`() = runBlocking {
        todo("Pakete", Due.At(today, LocalTime.of(9, 0)), places = setOf(home))
        todo("Akten", Due.At(today, LocalTime.of(9, 0)), places = setOf(work))

        assertEquals(listOf("Akten"), dueBetween("2026-10-23T07:00", "2026-10-23T09:00", Whereabouts.At(setOf(work))))
        assertEquals(emptyList<String>(), arrive(Whereabouts.At(setOf(work))))

        clock.now = berlin("2026-10-23T18:00")
        assertEquals(listOf("Pakete"), arrive(atHome))
        assertEquals("it pops up by the alarm, anywhere", clock.now, todos.nextAlarm(after = berlin("2026-10-23T09:00")))
        assertEquals(listOf("Pakete"), dueBetween("2026-10-23T09:00", "2026-10-23T18:00", elsewhere))
    }

    @Test
    fun `bound to a place without a time, it pops up on arriving there once its day has come, and only once`() = runBlocking {
        todo("Post", places = setOf(home))
        todo("Müll", Due.On(today.plusDays(1)), places = setOf(home))

        assertEquals(emptyList<String>(), arrive(elsewhere))
        assertEquals(listOf("Post"), arrive(atHome))
        assertEquals(emptyList<String>(), arrive(atHome))

        clock.now = berlin("2026-10-24T08:00")
        assertEquals(listOf("Müll"), arrive(atHome))
    }

    @Test
    fun `a to-do waits for its place until it is done, days after its time, and is never missed`() = runBlocking {
        val parcels = todo("Pakete", Due.At(today, LocalTime.of(9, 0)), places = setOf(home))
        val post = todo("Post", places = setOf(home))
        assertEquals(emptyList<String>(), dueBetween("2026-10-23T07:00", "2026-10-23T09:00", elsewhere))
        assertEquals(true, todos.anyWaiting())

        clock.now = berlin("2026-10-26T18:00")
        todos.markDone(post)

        assertEquals(listOf("Pakete"), arrive(atHome))
        assertEquals(listOf(parcels), todos.stillDue(listOf(parcels, post)).map { it.todoId })
        assertEquals(false, todos.anyWaiting())
    }

    @Test
    fun `put off, it comes back then, anywhere, and is not due till then`() = runBlocking {
        val id = todo("Pakete", Due.At(today, LocalTime.of(9, 0)), places = setOf(home))
        dueBetween("2026-10-23T07:00", "2026-10-23T09:00", atHome)
        clock.now = berlin("2026-10-23T09:05")

        todos.putOff(id, until = berlin("2026-10-23T09:35"))

        assertEquals(emptyList<TodoReminder>(), todos.stillDue(listOf(id)))
        assertEquals(berlin("2026-10-23T09:35"), todos.nextAlarm(after = clock.now))
        assertEquals(listOf("Pakete"), dueBetween("2026-10-23T09:05", "2026-10-23T09:35", elsewhere))
    }

    @Test
    fun `a reminder is due while its to-do is open, has popped up, and is not put off or set anew`() = runBlocking {
        val doctor = todo("Arzt", Due.At(today, LocalTime.of(9, 0)))
        val bins = todo("Müll", Due.At(today, LocalTime.of(9, 0)))
        val later = todo("Später", Due.At(today, LocalTime.of(10, 0)))
        dueBetween("2026-10-23T07:00", "2026-10-23T09:00", elsewhere)
        clock.now = berlin("2026-10-23T09:05")

        todos.markDone(bins)

        val reminder = todos.stillDue(listOf(doctor, bins, later)).single()
        assertEquals(TodoReminder(doctor, "Arzt", Due.At(today, LocalTime.of(9, 0)), places = emptyList()), reminder)
    }

    @Test
    fun `changing a to-do's time or places sets its reminder anew, other changes keep it as it is`() = runBlocking {
        val id = todo("Pakete", places = setOf(home))
        arrive(atHome)
        clock.now = berlin("2026-10-23T07:10")

        todos.update(id, TodoDraft("Pakete abholen", placeIds = setOf(home)))
        assertEquals(listOf(id), todos.stillDue(listOf(id)).map { it.todoId })
        assertEquals(emptyList<String>(), arrive(atHome))

        todos.update(id, TodoDraft("Pakete abholen", placeIds = setOf(work)))
        assertEquals(emptyList<TodoReminder>(), todos.stillDue(listOf(id)))
        assertEquals(listOf("Pakete abholen"), arrive(Whereabouts.At(setOf(work))))

        todos.update(id, TodoDraft("Pakete abholen", due = Due.At(today, LocalTime.of(12, 0))))
        assertEquals(berlin("2026-10-23T12:00"), todos.nextAlarm(after = clock.now))
        assertEquals(setOf<Long>(), todos.placeIdsOf(id))
    }

    @Test
    fun `once its places are deleted, a to-do waiting for them pops up, and one whose time is ahead reminds anywhere`() =
        runBlocking {
            todo("Post", places = setOf(home))
            todo("Pakete", Due.At(today, LocalTime.of(9, 0)), places = setOf(home))
            places.delete(home)

            todos.releaseFromPlaces()

            assertEquals(clock.now, todos.nextAlarm(after = berlin("2026-10-23T06:00")))
            assertEquals(listOf("Post", "Pakete"), dueBetween("2026-10-23T06:00", "2026-10-23T09:00", elsewhere))
            assertEquals(false, todos.anyWaiting())
        }

    @Test
    fun `brought back from the archive or by unticking its checklist, a to-do reminds anew`() = runBlocking {
        val post = todo("Post", places = setOf(home))
        val pickUp = listOf(ChecklistDraftItem(text = "Abholen"))
        val parcels = todos.create(TodoDraft("Pakete", checklist = pickUp, placeIds = setOf(home)))
        arrive(atHome)
        todos.markDone(post)
        val item = todos.checklist(parcels).single()
        todos.tick(parcels, item.id, ticked = true)

        todos.reopen(post)
        todos.tick(parcels, item.id, ticked = false)

        assertEquals(true, todos.anyWaiting())
        assertEquals(listOf("Pakete", "Post"), arrive(atHome))
    }

    @Test
    fun `looking around, a to-do pops up on arriving at its place once seen away from it, not while it stays there`() =
        runBlocking {
            todo("Post", places = setOf(home))

            assertEquals(emptyList<String>(), lookAround(atHome))
            assertEquals(emptyList<String>(), lookAround(elsewhere))
            assertEquals(listOf("Post"), lookAround(atHome))
            assertEquals("it pops up by the alarm", clock.now, todos.nextAlarm(after = berlin("2026-10-23T06:00")))
        }

    @Test
    fun `looking around, being maybe there changes nothing, and arriving before its day is not arriving on it`() = runBlocking {
        todo("Müll", Due.On(today.plusDays(1)), places = setOf(home))
        lookAround(elsewhere)

        assertEquals(emptyList<String>(), lookAround(Whereabouts.At(setOf(home), surely = emptySet())))
        assertEquals(emptyList<String>(), lookAround(Whereabouts.Unknown))
        assertEquals(emptyList<String>(), lookAround(atHome))
        clock.now = berlin("2026-10-24T00:30")
        assertEquals(emptyList<String>(), lookAround(atHome))

        lookAround(elsewhere)
        assertEquals(listOf("Müll"), lookAround(atHome))
    }

    @Test
    fun `the places of a to-do are kept, but one deleted meanwhile`() = runBlocking {
        val gone = places.create(PlaceDraft("Gym", listOf("FitX")))
        places.delete(gone)

        val id = todo("Post", places = setOf(home, gone))

        assertEquals(setOf(home), todos.placeIdsOf(id))
    }

    private suspend fun todo(title: String, due: Due = Due.None, places: Set<Long> = emptySet()): Long =
        todos.create(TodoDraft(title, due = due, placeIds = places))

    /** The to-dos that pop up after [after] and up to [until] at [whereabouts], at the time until then. */
    private suspend fun dueBetween(after: String, until: String, whereabouts: Whereabouts): List<String> {
        clock.now = berlin(until)
        return todos.remindersDue(berlin(after), berlin(until)) { whereabouts }.map { it.title }
    }

    private suspend fun arrive(whereabouts: Whereabouts): List<String> = todos.arrive { whereabouts }.map { it.title }

    private suspend fun lookAround(whereabouts: Whereabouts): List<String> = todos.lookAround { whereabouts }.map { it.title }

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(clock.timeZone).toInstant()
}
