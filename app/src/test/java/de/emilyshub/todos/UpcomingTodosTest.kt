package de.emilyshub.todos

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * To-dos on Heute some days before they are due (SPEC.md §1, §5): from so many days before on, under "Demnächst",
 * until their day, when they move into the list of the day.
 */
@RunWith(AndroidJUnit4::class)
class UpcomingTodosTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val today = LocalDate.parse("2026-10-23")

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a to-do shows from so many days before its day on, and not before`() = runBlocking {
        todos.create(TodoDraft("Geschenk kaufen", due = Due.On(today.plusDays(3)), showsDaysBefore = 3))
        todos.create(TodoDraft("Reifen wechseln", due = Due.On(today.plusDays(4)), showsDaysBefore = 3))
        todos.create(TodoDraft("Zahnarzt", due = Due.At(today.plusDays(1), LocalTime.of(9, 30)), showsDaysBefore = 1))
        // On its day only, as before.
        todos.create(TodoDraft("Steuer", due = Due.On(today.plusDays(1))))

        assertEquals(listOf("Zahnarzt", "Geschenk kaufen"), upcoming())
        assertEquals(listOf<String>(), todos.dueOn(today, clock.now).first().map { it.title })
    }

    @Test
    fun `on its day it moves into the list of the day`() = runBlocking {
        todos.create(TodoDraft("Geschenk kaufen", due = Due.On(today.plusDays(1)), showsDaysBefore = 3))

        at("2026-10-24T00:00")

        assertEquals(listOf<String>(), upcoming(today.plusDays(1)))
        assertEquals(listOf("Geschenk kaufen"), todos.dueOn(today.plusDays(1), clock.now).first().map { it.title })
    }

    @Test
    fun `the days count by the clock here, from midnight on`() = runBlocking {
        todos.create(TodoDraft("Geschenk kaufen", due = Due.On(LocalDate.parse("2026-10-27")), showsDaysBefore = 3))

        at("2026-10-23T23:59")
        assertEquals(listOf<String>(), upcoming())
        at("2026-10-24T00:00")
        assertEquals(listOf("Geschenk kaufen"), upcoming(LocalDate.parse("2026-10-24")))

        // Gone to New York, it is still the 23rd there: Heute of that day does not show it yet.
        clock.timeZone = ZoneId.of("America/New_York")
        assertEquals(listOf<String>(), upcoming(todos.today()))
        assertEquals(LocalDate.parse("2026-10-23"), todos.today())
    }

    @Test
    fun `ticked off there, it stays there ticked off for the rest of the day`() = runBlocking {
        val gift = todos.create(TodoDraft("Geschenk kaufen", due = Due.On(today.plusDays(2)), showsDaysBefore = 3))

        todos.markDone(gift)

        assertEquals(listOf(true), todos.upcoming(today, clock.now).first().map { it.done })
        at("2026-10-24T00:00")
        assertEquals(listOf<String>(), upcoming(today.plusDays(1)))
    }

    @Test
    fun `one that comes again takes how many days before it shows along to the next`() = runBlocking {
        val watering = todos.create(
            TodoDraft("Pflanzen gießen", due = Due.On(today), recurrence = Recurrence.EveryNDays(7), showsDaysBefore = 2),
        )

        todos.markDone(watering)

        val next = todos.open().first().single().todo
        assertEquals(today.plusDays(7) to 2, next.dueDate to next.showsDaysBefore)
        at("2026-10-28T08:00")
        assertEquals(listOf("Pflanzen gießen"), upcoming(LocalDate.parse("2026-10-28")))
    }

    @Test
    fun `ticked off early, one that comes again stays ticked off there, beside the next if that is near enough too`() = runBlocking {
        val watering = todos.create(
            TodoDraft("Pflanzen gießen", due = Due.On(today.plusDays(1)), recurrence = Recurrence.Daily, showsDaysBefore = 3),
        )

        todos.markDone(watering)

        assertEquals(
            listOf("Pflanzen gießen 2026-10-24 true", "Pflanzen gießen 2026-10-25 false"),
            todos.upcoming(today, clock.now).first().map { "${it.title} ${it.due.date} ${it.done}" },
        )
    }

    @Test
    fun `it shows from 1 to 30 days before, and only with a due date`() {
        assertEquals(setOf(TodoProblem.ShowsDaysBeforeOutOfRange), TodoDraft("A", due = Due.On(today), showsDaysBefore = 0).problems)
        assertEquals(setOf(TodoProblem.ShowsDaysBeforeOutOfRange), TodoDraft("A", due = Due.On(today), showsDaysBefore = 31).problems)
        assertEquals(emptySet<TodoProblem>(), TodoDraft("A", due = Due.On(today), showsDaysBefore = 30).problems)
        assertNull(TodoDraft("A", showsDaysBefore = 3).cleaned().showsDaysBefore)
        assertEquals(emptySet<TodoProblem>(), TodoDraft("A", showsDaysBefore = 0).problems)
    }

    @Test
    fun `kept on editing`() = runBlocking {
        val gift = todos.create(TodoDraft("Geschenk kaufen", due = Due.On(today.plusDays(2)), showsDaysBefore = 3))

        val saved = checkNotNull(todos.get(gift))
        assertEquals(3, saved.toDraft(emptyList(), emptySet(), emptySet()).showsDaysBefore)
    }

    private suspend fun upcoming(date: LocalDate = today) = todos.upcoming(date, clock.now).first().map { it.title }

    private fun at(localDateTime: String) {
        clock.now = LocalDateTime.parse(localDateTime).atZone(clock.zone).toInstant()
    }
}
