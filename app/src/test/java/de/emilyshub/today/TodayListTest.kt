package de.emilyshub.today

import de.emilyshub.routines.ChecklistEntry
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Priority
import de.emilyshub.todos.Due
import de.emilyshub.todos.DueTodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Heute (SPEC.md §1): routines and to-dos by time, the overdue to-dos at the very top. */
class TodayListTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `overdue to-dos come first, then the to-dos of the day without a time, then routines and to-dos by their time`() {
        val routines = listOf(routine("Tabletten", "08:00"), routine("Lesen", "21:00"))
        val todos = listOf(
            todo("Steuer abends", Due.At(today, LocalTime.of(19, 0))),
            todo("Müll", Due.On(today)),
            todo("Seit gestern", Due.On(today.minusDays(1)), overdue = true),
            todo("Seit früh", Due.At(today, LocalTime.of(6, 30)), overdue = true),
            todo("Arzt", Due.At(today, LocalTime.of(8, 0))),
        )

        assertEquals(
            listOf("Seit gestern", "Seit früh", "Müll", "Tabletten", "Arzt", "Steuer abends", "Lesen"),
            todayList(routines, todos, today).map { it.title },
        )
    }

    @Test
    fun `a to-do ticked off today stays where it was due, an overdue one among those of the day without a time`() {
        val todos = listOf(
            todo("Gestern fällig, heute erledigt", Due.On(today.minusDays(1)), done = true),
            todo("Heute fällig", Due.On(today)),
            todo("Heute früh erledigt", Due.At(today, LocalTime.of(7, 0)), done = true),
        )

        assertEquals(
            listOf("Gestern fällig, heute erledigt", "Heute fällig", "Heute früh erledigt"),
            todayList(listOf(routine("Lesen", "21:00")), todos, today).map { it.title }.dropLast(1),
        )
    }

    @Test
    fun `each entry is in its block by its status`() {
        val routines = listOf(
            routine("Offen", "08:00"),
            routine("Erledigt", "08:00", OccurrenceStatus.Done),
            routine("Übersprungen", "08:00", OccurrenceStatus.Skipped),
            routine("Gekoppelt", "08:00", OccurrenceStatus.Skipped, coveredBy = "Offen"),
            routine("Verpasst", "08:00", OccurrenceStatus.Missed),
            routine("Ausgelassen", "08:00", OccurrenceStatus.Dropped),
        )
        val todos = listOf(
            todo("Überfällig", Due.On(today.minusDays(1)), overdue = true),
            todo("Offenes To-do", Due.On(today)),
            todo("Erledigtes To-do", Due.On(today), done = true),
        )

        val blocks = todayBlocks(routines, todos, today)

        assertEquals(listOf("Überfällig"), blocks.overdue.map { it.title })
        assertEquals(listOf("Offenes To-do", "Offen"), blocks.upNext.map { it.title })
        assertEquals(
            setOf("Erledigt", "Übersprungen", "Gekoppelt", "Verpasst", "Ausgelassen", "Erledigtes To-do"),
            blocks.past.map { it.title }.toSet(),
        )
    }

    @Test
    fun `what is open stays up next past its time, and yesterday's routine still in its time window comes first`() {
        val yesterdays = routine("Lesen", "23:00", date = today.minusDays(1))
        val routines = listOf(routine("Abends", "21:00"), routine("Früh", "07:00"), yesterdays)
        val todos = listOf(todo("Müll", Due.On(today)), todo("Arzt", Due.At(today, LocalTime.of(8, 0))))

        assertEquals(
            listOf("Müll", "Lesen", "Früh", "Arzt", "Abends"),
            todayBlocks(routines, todos, today).upNext.map { it.title },
        )
    }

    @Test
    fun `what is over comes by its time, a routine first at the same time`() {
        val routines = listOf(
            routine("Abends", "21:00", OccurrenceStatus.Done),
            routine("Früh", "07:00", OccurrenceStatus.Missed),
        )
        val todos = listOf(
            todo("Arzt", Due.At(today, LocalTime.of(7, 0)), done = true),
            todo("Müll", Due.On(today), done = true),
        )

        assertEquals(listOf("Müll", "Früh", "Arzt", "Abends"), todayBlocks(routines, todos, today).past.map { it.title })
    }

    @Test
    fun `the overdue to-dos come the longest overdue first`() {
        val todos = listOf(
            todo("Seit gestern", Due.On(today.minusDays(1)), overdue = true),
            todo("Seit früh", Due.At(today, LocalTime.of(6, 30)), overdue = true),
            todo("Seit letzter Woche", Due.On(today.minusDays(7)), overdue = true),
        )

        assertEquals(
            listOf("Seit letzter Woche", "Seit gestern", "Seit früh"),
            todayBlocks(emptyList(), todos, today).overdue.map { it.title },
        )
    }

    @Test
    fun `the stand counts what is done of all but the skipped ones, by hand or by a coupled routine`() {
        val routines = listOf(
            routine("Offen", "08:00"),
            routine("Erledigt", "08:00", OccurrenceStatus.Done),
            routine("Übersprungen", "08:00", OccurrenceStatus.Skipped),
            routine("Gekoppelt", "08:00", OccurrenceStatus.Skipped, coveredBy = "Erledigt"),
            routine("Verpasst", "08:00", OccurrenceStatus.Missed),
            routine("Ausgelassen", "08:00", OccurrenceStatus.Dropped),
        )
        val todos = listOf(
            todo("Überfällig", Due.On(today.minusDays(1)), overdue = true),
            todo("Erledigtes To-do", Due.On(today), done = true),
        )

        assertEquals(DayStand(done = 2, of = 6), todayBlocks(routines, todos, today).stand)
    }

    @Test
    fun `the summary of what is over counts each status that occurs`() {
        val routines = listOf(
            routine("Erledigt", "08:00", OccurrenceStatus.Done),
            routine("Übersprungen", "09:00", OccurrenceStatus.Skipped),
            routine("Gekoppelt", "09:00", OccurrenceStatus.Skipped, coveredBy = "Erledigt"),
            routine("Verpasst", "10:00", OccurrenceStatus.Missed),
        )
        val todos = listOf(todo("Erledigtes To-do", Due.On(today), done = true))

        assertEquals(PastSummary(done = 2, skipped = 2, missed = 1, dropped = 0), todayBlocks(routines, todos, today).summary)
    }

    @Test
    fun `a routine with its checklist partly ticked off stays up next`() {
        val routine = routine("Morgens", "07:00").copy(
            checklist = listOf(ChecklistEntry(1, "Zähne", ticked = true), ChecklistEntry(2, "Duschen", ticked = false)),
        )

        assertEquals(listOf("Morgens"), todayBlocks(listOf(routine), emptyList(), today).upNext.map { it.title })
    }

    @Test
    fun `without entries the blocks are empty`() {
        val blocks = todayBlocks(emptyList(), emptyList(), today)

        assertTrue(blocks.isEmpty)
        assertEquals(DayStand(done = 0, of = 0), blocks.stand)
    }

    private fun routine(
        name: String,
        time: String,
        status: OccurrenceStatus = OccurrenceStatus.Open,
        coveredBy: String? = null,
        date: LocalDate = today,
    ) = DueRoutine(
        occurrenceId = name.hashCode().toLong(),
        routineId = 1,
        name = name,
        dueAt = ZonedDateTime.of(date, LocalTime.parse(time), zone),
        status = status,
        priority = Priority.Normal,
        coveredBy = coveredBy,
    )

    private fun todo(title: String, due: Due, overdue: Boolean = false, done: Boolean = false) =
        DueTodo(title.hashCode().toLong(), title, due, Priority.Normal, done = done, overdue = overdue)

    private val TodayEntry.title: String
        get() = when (this) {
            is TodayEntry.OfRoutine -> routine.name
            is TodayEntry.OfTodo -> todo.title
        }
}
