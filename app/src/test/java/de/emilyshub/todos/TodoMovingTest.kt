package de.emilyshub.todos

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.Priority
import de.emilyshub.places.Whereabouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Moving a to-do to another day (SPEC.md §1, Menü je Eintrag; §5; #97): only its due day changes, its time and the rest
 * stay, waking some days before comes along, its reminder is set anew, and it can be undone.
 */
@RunWith(AndroidJUnit4::class)
class TodoMovingTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private var changes = 0
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined, onChange = { changes++ })

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `moving changes only the due day, the time and all else stay`() = runBlocking {
        val id = todos.create(
            TodoDraft("Arzt", description = "Überweisung", due = Due.At(today.minusDays(7), LocalTime.of(9, 0)), priority = Priority.High),
        )

        val move = todos.moveTo(id, today.plusDays(1))

        val moved = checkNotNull(todos.get(id))
        assertEquals(Due.At(today.plusDays(1), LocalTime.of(9, 0)), moved.due)
        assertEquals("Überweisung", moved.description)
        assertEquals(Priority.High, moved.priority)
        assertEquals(TodoMove(id, from = Due.At(today.minusDays(7), LocalTime.of(9, 0)), to = moved.due), move)
    }

    @Test
    fun `one due whenever gets the day, without a time`() = runBlocking {
        val id = todos.create(TodoDraft("Fenster"))

        todos.moveTo(id, today.plusDays(3))

        assertEquals(Due.On(today.plusDays(3)), todos.get(id)?.due)
    }

    @Test
    fun `waking some days before comes along, waking at a fixed moment stays`() = runBlocking {
        val before = todos.create(TodoDraft("Geschenk", due = Due.On(today.plusDays(1)), wake = WakeTime.Before(2, LocalTime.of(18, 0))))
        val fixed = WakeTime.At(today.plusDays(1), LocalTime.of(8, 0))
        val at = todos.create(TodoDraft("Paket", due = Due.On(today.plusDays(2)), wake = fixed))

        todos.moveTo(before, today.plusDays(10))
        todos.moveTo(at, today.plusDays(10))

        assertEquals(LocalDateTime.of(today.plusDays(8), LocalTime.of(18, 0)), todos.get(before)?.let { it.wake?.localMoment(it.due) })
        assertEquals(fixed, todos.get(at)?.wake)
    }

    @Test
    fun `undoing puts the due back as it was, whenever too`() = runBlocking {
        val timed = todos.create(TodoDraft("Arzt", due = Due.At(today, LocalTime.of(9, 0))))
        val whenever = todos.create(TodoDraft("Fenster"))

        todos.undo(checkNotNull(todos.moveTo(timed, today.plusDays(1))))
        todos.undo(checkNotNull(todos.moveTo(whenever, today.plusDays(1))))

        assertEquals(Due.At(today, LocalTime.of(9, 0)), todos.get(timed)?.due)
        assertEquals(Due.None, todos.get(whenever)?.due)
    }

    @Test
    fun `undoing leaves a to-do alone that was changed again meanwhile`() = runBlocking {
        val id = todos.create(TodoDraft("Arzt", due = Due.On(today)))
        val move = checkNotNull(todos.moveTo(id, today.plusDays(1)))
        todos.moveTo(id, today.plusDays(5))

        todos.undo(move)

        assertEquals(Due.On(today.plusDays(5)), todos.get(id)?.due)
    }

    @Test
    fun `a to-do ticked off is not moved`() = runBlocking {
        val id = todos.create(TodoDraft("Müll", due = Due.On(today)))
        todos.markDone(id)

        assertNull(todos.moveTo(id, today.plusDays(1)))
        assertEquals(Due.On(today), todos.get(id)?.due)
    }

    @Test
    fun `a to-do coming again moves alone, and its next comes by its rule from the new day`() = runBlocking {
        val mondays = Recurrence.Weekdays(setOf(DayOfWeek.MONDAY))
        val id = todos.create(TodoDraft("Müll", due = Due.On(LocalDate.parse("2026-10-26")), recurrence = mondays))

        todos.moveTo(id, LocalDate.parse("2026-10-27"))
        todos.markDone(id)

        assertEquals(listOf("Müll"), todos.open().first().map { it.todo.title })
        assertEquals(Due.On(LocalDate.parse("2026-11-02")), todos.open().first().single().todo.due)
        assertEquals(mondays, todos.get(id)?.recurrence)
    }

    @Test
    fun `its reminder is set anew, one shown going and the new time reminding`() = runBlocking {
        val id = todos.create(TodoDraft("Arzt", due = Due.At(today, LocalTime.of(8, 0))))
        clock.now = Instant.parse("2026-10-23T06:00:00Z") // 08:00 in Berlin
        val shown = todos.remindersDue(after = Instant.parse("2026-10-23T05:00:00Z"), until = clock.now) { Whereabouts.Unknown }
        assertEquals(listOf(id), shown.map { it.todoId })
        val before = changes

        todos.moveTo(id, today.plusDays(1))

        assertEquals(emptyList<TodoReminder>(), todos.stillDue(listOf(id)))
        assertEquals(Instant.parse("2026-10-24T06:00:00Z"), todos.nextAlarm(after = clock.now))
        assertEquals("the reminders are told", before + 1, changes)
    }
}
