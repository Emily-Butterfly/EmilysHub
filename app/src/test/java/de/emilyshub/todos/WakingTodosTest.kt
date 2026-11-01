package de.emilyshub.todos

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

/**
 * A to-do's own wake time (SPEC.md §5): its reminder comes then, and again when it is due at a time, while it is open.
 * Some days before comes along when the due moves or the to-do comes again; a fixed moment is for that to-do only.
 */
@RunWith(AndroidJUnit4::class)
class WakingTodosTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val places = Places({ database }, Dispatchers.Unconfined)
    private val elsewhere = Whereabouts.At(emptySet())

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `it wakes at its wake time, and reminds again when it is due at a time`() = runBlocking {
        todo("Müll", Due.At(today.plusDays(1), time("09:00")), WakeTime.Before(1, time("18:00")))

        assertEquals(berlin("2026-10-23T18:00"), todos.nextAlarm(after = clock.now))
        assertEquals(listOf("Müll"), dueBetween("2026-10-23T07:00", "2026-10-23T18:00"))

        assertEquals(berlin("2026-10-24T09:00"), todos.nextAlarm(after = clock.now))
        assertEquals(listOf("Müll"), dueBetween("2026-10-23T18:00", "2026-10-24T09:00"))
        assertEquals(null, todos.nextAlarm(after = clock.now))
    }

    @Test
    fun `due on a day without a time, it wakes only at its wake time`() = runBlocking {
        todo("Müll", Due.On(today.plusDays(1)), WakeTime.Before(0, time("08:00")))

        assertEquals(emptyList<String>(), dueBetween("2026-10-23T07:00", "2026-10-24T07:59"))
        assertEquals(listOf("Müll"), dueBetween("2026-10-24T07:59", "2026-10-24T08:00"))
        assertEquals(null, todos.nextAlarm(after = clock.now))
    }

    @Test
    fun `a fixed moment wakes also without a due`() = runBlocking {
        todo("Geschenk", wake = WakeTime.At(today, time("12:00")))

        assertEquals(listOf("Geschenk"), dueBetween("2026-10-23T07:00", "2026-10-23T12:00"))
        assertEquals(1, todos.stillDue(todos.open().first().map { it.todo.id }).size)
    }

    @Test
    fun `a to-do done wakes no more`() = runBlocking {
        val id = todo("Müll", Due.At(today.plusDays(1), time("09:00")), WakeTime.Before(1, time("18:00")))

        todos.markDone(id)

        assertEquals(null, todos.nextAlarm(after = clock.now))
    }

    @Test
    fun `a wake time gone by when it is saved does not wake after all, its due time still reminds`() = runBlocking {
        todo("Müll", Due.At(today, time("09:00")), WakeTime.At(today, time("06:00")))

        assertEquals(berlin("2026-10-23T09:00"), todos.nextAlarm(after = berlin("2026-10-23T05:00")))
        assertEquals(listOf("Müll"), dueBetween("2026-10-23T05:00", "2026-10-23T09:00"))
    }

    @Test
    fun `put off after waking, it comes back then, and again when it is due`() = runBlocking {
        val id = todo("Müll", Due.At(today.plusDays(1), time("09:00")), WakeTime.Before(1, time("18:00")))
        dueBetween("2026-10-23T07:00", "2026-10-23T18:00")
        clock.now = berlin("2026-10-23T18:05")

        todos.putOff(id, until = berlin("2026-10-23T18:35"))

        assertEquals("not up till then", emptyList<TodoReminder>(), todos.stillDue(listOf(id)))
        assertEquals(berlin("2026-10-23T18:35"), todos.nextAlarm(after = clock.now))
        assertEquals(listOf("Müll"), dueBetween("2026-10-23T18:05", "2026-10-23T18:35"))
        assertEquals(berlin("2026-10-24T09:00"), todos.nextAlarm(after = clock.now))
        assertEquals(listOf("Müll"), dueBetween("2026-10-23T18:35", "2026-10-24T09:00"))
    }

    @Test
    fun `put off past its due time, it comes back once, when it was put off to`() = runBlocking {
        val id = todo("Anrufen", Due.At(today, time("18:30")), WakeTime.Before(0, time("18:00")))
        dueBetween("2026-10-23T07:00", "2026-10-23T18:00")
        clock.now = berlin("2026-10-23T18:05")

        todos.putOff(id, until = berlin("2026-10-23T19:05"))

        assertEquals(berlin("2026-10-23T19:05"), todos.nextAlarm(after = clock.now))
        assertEquals(emptyList<String>(), dueBetween("2026-10-23T18:05", "2026-10-23T18:30"))
    }

    @Test
    fun `days before moves along with the due, and a new wake time sets it anew`() = runBlocking {
        val id = todo("Müll", Due.On(today.plusDays(2)), WakeTime.Before(1, time("18:00")))

        todos.update(id, draftOf(id).copy(due = Due.On(today.plusDays(5))))
        assertEquals(berlin("2026-10-27T18:00"), todos.nextAlarm(after = clock.now))

        todos.update(id, draftOf(id).copy(wake = WakeTime.At(today, time("20:00"))))
        assertEquals(berlin("2026-10-23T20:00"), todos.nextAlarm(after = clock.now))
    }

    @Test
    fun `coming again, days before comes along with the next, a fixed moment does not`() = runBlocking {
        val bins = todo("Müll", Due.On(today), WakeTime.Before(0, time("06:00")), recurrence = Recurrence.Daily)
        val gift = todo("Blumen", Due.On(today), WakeTime.At(today, time("20:00")), recurrence = Recurrence.Daily)

        todos.markDone(bins)
        todos.markDone(gift)

        val next = todos.open().first().associate { it.todo.title to it.todo.wake }
        assertEquals(mapOf("Müll" to WakeTime.Before(0, time("06:00")), "Blumen" to null), next)
        assertEquals("the next of Müll wakes", berlin("2026-10-24T06:00"), todos.nextAlarm(after = clock.now))
    }

    @Test
    fun `bound to a place, it wakes there, and elsewhere waits to pop up on arrival from its wake time on`() = runBlocking {
        val home = places.create(PlaceDraft("Zuhause", listOf("FritzBox")))
        val atHome = Whereabouts.At(setOf(home))
        todo("Müll", Due.On(today.plusDays(1)), WakeTime.Before(1, time("18:00")), places = setOf(home))

        assertEquals("not yet", emptyList<String>(), arrive(atHome))
        assertEquals(emptyList<String>(), dueBetween("2026-10-23T07:00", "2026-10-23T18:00"))

        clock.now = berlin("2026-10-23T19:00")
        assertEquals(listOf("Müll"), arrive(atHome))
    }

    @Test
    fun `waking some days before needs a due day, at most 30 days`() {
        val draft = TodoDraft("Müll", wake = WakeTime.Before(1, time("18:00")))

        assertEquals(null, draft.cleaned().wake)
        assertEquals(
            setOf(TodoProblem.WakeDaysOutOfRange),
            draft.copy(due = Due.On(today), wake = WakeTime.Before(31, time("18:00"))).problems,
        )
    }

    private suspend fun todo(
        title: String,
        due: Due = Due.None,
        wake: WakeTime? = null,
        places: Set<Long> = emptySet(),
        recurrence: Recurrence? = null,
    ): Long = todos.create(TodoDraft(title, due = due, placeIds = places, recurrence = recurrence, wake = wake))

    private suspend fun draftOf(id: Long): TodoDraft =
        checkNotNull(todos.get(id)).toDraft(todos.checklist(id), todos.tagIdsOf(id), todos.placeIdsOf(id))

    /** The to-dos that pop up after [after] and up to [until], elsewhere than any place, at the time until then. */
    private suspend fun dueBetween(after: String, until: String): List<String> {
        clock.now = berlin(until)
        return todos.remindersDue(berlin(after), berlin(until)) { elsewhere }.map { it.title }
    }

    private suspend fun arrive(whereabouts: Whereabouts): List<String> = todos.arrive { whereabouts }.map { it.title }

    private fun time(text: String) = LocalTime.parse(text)

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(clock.timeZone).toInstant()
}
