package de.emilyshub.todos

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.PlaceDraft
import de.emilyshub.places.Places
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.Priority
import de.emilyshub.tags.Tags
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.WishStatus
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

/**
 * To-dos that come again (SPEC.md §5): ticked off into the archive, followed at once by the next, due as the rule says;
 * undoing the tick takes the next away again.
 */
@RunWith(AndroidJUnit4::class)
class RecurringTodosTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val tags = Tags({ database }, Dispatchers.Unconfined)
    private val places = Places({ database }, Dispatchers.Unconfined)
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)

    private val mondays = Recurrence.Weekdays(setOf(DayOfWeek.MONDAY))

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `ticked off, it goes to the archive and the next is there, due by its rule, with all of it but the ticks`() = runBlocking {
        val home = tags.create("Zuhause")
        val house = places.create(PlaceDraft("Haus", listOf("FritzBox")))
        val trash = todos.create(
            TodoDraft(
                title = "Müll rausstellen",
                description = "Vor 7 Uhr",
                due = Due.At(day("2026-10-19"), LocalTime.of(6, 30)),
                priority = Priority.High,
                checklist = listOf(ChecklistDraftItem(text = "Gelber Sack"), ChecklistDraftItem(text = "Restmüll")),
                tagIds = setOf(home),
                placeIds = setOf(house),
                recurrence = mondays,
            ),
        )
        todos.tick(trash, todos.checklist(trash).first().id, ticked = true)

        todos.markDone(trash)

        assertEquals(listOf(trash), todos.archived().first().map { it.id })
        val next = todos.open().first().single()
        assertEquals("Müll rausstellen" to "Vor 7 Uhr", next.todo.title to next.todo.description)
        assertEquals("Done late, on Friday: the next Monday", Due.At(day("2026-10-26"), LocalTime.of(6, 30)), next.todo.due)
        assertEquals(Priority.High, next.todo.priority)
        assertEquals(mondays, next.todo.recurrence)
        assertEquals(listOf("Gelber Sack" to false, "Restmüll" to false), next.checklist().map { it.text to it.ticked })
        assertEquals(setOf(home), next.tagIds)
        assertEquals(setOf(house), next.placeIds)
        assertEquals(next.todo.id, todos.get(trash)?.nextId)
    }

    @Test
    fun `the next reminds at its time, and waits for its places, as one just saved`() = runBlocking {
        val house = places.create(PlaceDraft("Haus", listOf("FritzBox")))
        val due = Due.At(day("2026-10-19"), LocalTime.of(6, 30))
        val trash = todos.create(TodoDraft("Müll", due = due, placeIds = setOf(house), recurrence = mondays))

        todos.markDone(trash)

        val next = todos.open().first().single().todo
        assertTrue(next.reminding.remindsAtDueTime)
        assertTrue(next.reminding.waiting)
    }

    @Test
    fun `the next comes from the day it was done, if so chosen`() = runBlocking {
        val fortnightFromDone = Recurrence.EveryNDays(14, fromDone = true)
        val plants = todos.create(TodoDraft("Blumen düngen", due = Due.On(day("2026-10-20")), recurrence = fortnightFromDone))

        todos.markDone(plants)

        assertEquals(Due.On(day("2026-11-06")), todos.open().first().single().todo.due)
    }

    @Test
    fun `undoing the tick takes the next away again, and the to-do is open as it was, coming again`() = runBlocking {
        val goal = wishes.create(WishDraft("Ordentliche Wohnung"))
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-26")), goalId = goal, recurrence = mondays))

        todos.markDone(trash)
        val next = todos.open().first().single().todo
        assertEquals("The next hangs under the goal too", goal, next.goalId)
        assertEquals("The one done stays in the goal, as before", goal, todos.get(trash)?.goalId)
        assertEquals("The goal shows the one to do now only", listOf(next.id), wishes.goal(goal).first()?.todos?.map { it.id })

        todos.reopen(trash)

        assertNull(todos.get(next.id))
        val reopened = checkNotNull(todos.get(trash))
        assertEquals(listOf(trash), todos.open().first().map { it.todo.id })
        assertEquals(goal to mondays, reopened.goalId to reopened.recurrence)
        assertNull(reopened.nextId)

        todos.markDone(trash)
        assertEquals(listOf(Due.On(day("2026-11-02"))), todos.open().first().map { it.todo.due })
    }

    @Test
    fun `opened again after the next was changed, it is a to-do of its own, and the next stays as changed`() = runBlocking {
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-19")), recurrence = mondays))
        todos.markDone(trash)
        val next = todos.open().first().single().todo.id
        todos.update(next, TodoDraft("Müll und Altpapier", due = Due.On(day("2026-10-26")), recurrence = mondays))

        todos.reopen(trash)

        assertNull(todos.get(trash)?.recurrence)
        assertEquals("Müll und Altpapier", todos.get(next)?.title)
        assertEquals(setOf(trash, next), todos.open().first().map { it.todo.id }.toSet())
    }

    @Test
    fun `opened again after the next was deleted, it comes again as before, under its goal`() = runBlocking {
        val goal = wishes.create(WishDraft("Ordentliche Wohnung"))
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-19")), goalId = goal, recurrence = mondays))
        todos.markDone(trash)
        todos.delete(todos.open().first().single().todo.id)

        todos.reopen(trash)

        val reopened = checkNotNull(todos.get(trash))
        assertEquals(goal to mondays, reopened.goalId to reopened.recurrence)
        assertEquals(listOf(trash), wishes.goal(goal).first()?.todos?.map { it.id })
    }

    @Test
    fun `a goal with a to-do coming again is done once the series is ended by deleting the next, its last one done`() =
        runBlocking {
            val goal = wishes.create(WishDraft("Kurs abschließen"))
            val lesson = todos.create(TodoDraft("Lektion", due = Due.On(day("2026-10-19")), goalId = goal, recurrence = mondays))

            todos.markDone(lesson)
            assertEquals(WishStatus.Open, wishes.get(goal)?.status)

            todos.delete(todos.open().first().single().todo.id)
            assertEquals(WishStatus.Done, wishes.get(goal)?.status)
        }

    @Test
    fun `ticked off by an edit of its checklist, it brings the next with the tags and places of that edit`() = runBlocking {
        val home = tags.create("Zuhause")
        val house = places.create(PlaceDraft("Haus", listOf("FritzBox")))
        val bags = listOf(ChecklistDraftItem(text = "Gelber Sack"), ChecklistDraftItem(text = "Restmüll"))
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-26")), checklist = bags, recurrence = mondays))
        val items = todos.checklist(trash)
        todos.tick(trash, items.first().id, ticked = true)

        val kept = listOf(ChecklistDraftItem(items.first().id, "Gelber Sack"))
        val edited = TodoDraft("Müll", due = Due.On(day("2026-10-26")), checklist = kept, recurrence = mondays)
        todos.update(trash, edited.copy(tagIds = setOf(home), placeIds = setOf(house)))

        val next = todos.open().first().single()
        assertEquals(setOf(home) to setOf(house), next.tagIds to next.placeIds)
    }

    @Test
    fun `chosen in the editor, a recurrence follows the day the to-do is due, and one saved keeps its day when put off`() {
        val due = Due.On(day("2026-11-01"))
        val rent = TodoDraft("Miete", due = due, recurrence = Recurrence.Monthly(1))
        val putOff = Due.On(day("2026-11-03"))

        assertEquals(Recurrence.Monthly(3), rent.dueOn(putOff, recurrenceFollows = true).recurrence)
        assertEquals(Recurrence.Monthly(1), rent.dueOn(putOff, recurrenceFollows = false).recurrence)
        assertEquals(putOff, rent.dueOn(putOff, recurrenceFollows = false).due)
        val sameDayAtNoon = Due.At(day("2026-11-01"), LocalTime.NOON)
        assertEquals("Due on the same day", Recurrence.Monthly(1), rent.dueOn(sameDayAtNoon, recurrenceFollows = true).recurrence)
    }

    @Test
    fun `opened again after the next was ticked off too, it is a to-do of its own, coming again no more`() = runBlocking {
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-19")), recurrence = mondays))
        todos.markDone(trash)
        val next = todos.open().first().single().todo.id
        todos.markDone(next)
        val afterNext = todos.open().first().single().todo.id

        todos.reopen(trash)

        assertNull(todos.get(trash)?.recurrence)
        assertEquals(setOf(trash, afterNext), todos.open().first().map { it.todo.id }.toSet())
        assertEquals(afterNext, todos.get(next)?.nextId)
    }

    @Test
    fun `the last item of its checklist ticked off brings the next, and one unticked takes it away again`() = runBlocking {
        val bags = listOf(ChecklistDraftItem(text = "Gelber Sack"))
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-26")), checklist = bags, recurrence = mondays))
        val item = todos.checklist(trash).single().id

        todos.tick(trash, item, ticked = true)
        val next = checkNotNull(todos.get(trash)?.nextId)
        assertEquals(Due.On(day("2026-11-02")), todos.get(next)?.due)

        todos.tick(trash, item, ticked = false)
        assertNull(todos.get(next))
        assertEquals(listOf(trash), todos.open().first().map { it.todo.id })
    }

    @Test
    fun `ticked off twice, it brings one next only, and without a recurrence none`() = runBlocking {
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-26")), recurrence = mondays))
        todos.markDone(trash)
        todos.markDone(trash)
        assertEquals(1, todos.open().first().size)

        val next = todos.open().first().single().todo.id
        todos.update(next, TodoDraft("Müll", due = Due.On(day("2026-11-02")), recurrence = null))
        todos.markDone(next)
        assertEquals(emptyList<TodoWithDetails>(), todos.open().first())
    }

    @Test
    fun `each recurrence is kept as saved`() = runBlocking {
        val kinds = listOf(
            Recurrence.Daily,
            Recurrence.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            Recurrence.EveryNDays(14),
            Recurrence.EveryNDays(3, fromDone = true),
            Recurrence.Monthly(31),
            Recurrence.Monthly(1, fromDone = true),
            Recurrence.Yearly(MonthDay.of(2, 29)),
            Recurrence.Yearly(MonthDay.of(5, 31), fromDone = true),
        )

        val saved = kinds.map { kind ->
            todos.get(todos.create(TodoDraft("Wiederholt", due = Due.On(day("2026-10-30")), recurrence = kind)))?.recurrence
        }

        assertEquals(kinds, saved)
    }

    @Test
    fun `a recurrence needs a day it is due, chosen weekdays, and an interval from 2 to 365 days`() {
        assertNull(TodoDraft("Müll", recurrence = mondays).cleaned().recurrence)
        val due = Due.On(day("2026-10-26"))
        fun problemsOf(recurrence: Recurrence) = TodoDraft("Müll", due = due, recurrence = recurrence).problems
        assertEquals(setOf(TodoProblem.WeekdaysMissing), problemsOf(Recurrence.Weekdays(emptySet())))
        assertEquals(setOf(TodoProblem.IntervalOutOfRange), problemsOf(Recurrence.EveryNDays(1)))
        assertEquals(setOf(TodoProblem.IntervalOutOfRange), problemsOf(Recurrence.EveryNDays(366)))
        assertEquals(emptySet<TodoProblem>(), problemsOf(Recurrence.EveryNDays(365)))
    }

    @Test
    fun `after N times the series ends, counting to-dos, also when a day was left out by doing one late`() = runBlocking {
        val saturdays = Recurrence.Weekdays(setOf(DayOfWeek.SATURDAY))
        todos.create(TodoDraft("Putzen", due = Due.On(day("2026-10-24")), recurrence = saturdays, end = RecurrenceEnd.AfterTimes(4)))

        // The 2nd done only after the 3rd Saturday: the 3rd Saturday is left out, the series runs a Saturday longer.
        doneOn("2026-10-24")
        doneOn("2026-11-08")
        doneOn("2026-11-14")
        assertEquals(listOf(Due.On(day("2026-11-21")) to 4), todos.open().first().map { it.todo.due to it.todo.seriesNumber })
        doneOn("2026-11-21")

        assertEquals(emptyList<TodoWithDetails>(), todos.open().first())
        assertEquals(4, todos.archived().first().size)
    }

    @Test
    fun `after N times counts from the day it was done too`() = runBlocking {
        val fortnight = Recurrence.EveryNDays(14, fromDone = true)
        todos.create(TodoDraft("Düngen", due = Due.On(day("2026-10-23")), recurrence = fortnight, end = RecurrenceEnd.AfterTimes(2)))

        doneOn("2026-10-23")
        doneOn("2026-11-10")

        assertEquals(emptyList<TodoWithDetails>(), todos.open().first())
    }

    @Test
    fun `on a date the series ends with the last one due by then, that day included`() = runBlocking {
        val saturdays = Recurrence.Weekdays(setOf(DayOfWeek.SATURDAY))
        todos.create(TodoDraft("Markt", due = Due.On(day("2026-10-24")), recurrence = saturdays, end = RecurrenceEnd.OnDate(day("2026-11-07"))))

        doneOn("2026-10-24")
        doneOn("2026-10-31")
        assertEquals(listOf(Due.On(day("2026-11-07"))), todos.open().first().map { it.todo.due })
        doneOn("2026-11-07")

        assertEquals(emptyList<TodoWithDetails>(), todos.open().first())
    }

    @Test
    fun `the end can be changed later, N still counting from the first, and one below those come makes this the last`() =
        runBlocking {
            val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-26")), recurrence = mondays))
            doneOn("2026-10-26")
            doneOn("2026-11-02")
            val third = todos.open().first().single().todo
            assertEquals(3, third.seriesNumber)

            todos.update(third.id, TodoDraft("Müll", due = third.due, recurrence = mondays, end = RecurrenceEnd.AfterTimes(2)))
            doneOn("2026-11-09")

            assertEquals(emptyList<TodoWithDetails>(), todos.open().first())
            assertEquals(RecurrenceEnd.AfterTimes(2), todos.get(third.id)?.end)
            assertEquals(RecurrenceEnd.Never, todos.get(trash)?.end)
        }

    @Test
    fun `undoing the last of a series takes it back as it was, and done again it ends the series again`() = runBlocking {
        val trash = todos.create(TodoDraft("Müll", due = Due.On(day("2026-10-26")), recurrence = mondays, end = RecurrenceEnd.AfterTimes(2)))
        doneOn("2026-10-26")
        val second = todos.open().first().single().todo.id
        todos.reopen(trash)
        assertEquals(listOf(trash), todos.open().first().map { it.todo.id })
        assertNull(todos.get(second))

        doneOn("2026-10-26")
        doneOn("2026-11-02")
        assertEquals(emptyList<TodoWithDetails>(), todos.open().first())
        val last = todos.archived().first().first()
        todos.reopen(last.id)
        assertEquals(listOf(last.id), todos.open().first().map { it.todo.id })
    }

    @Test
    fun `an end needs at least 1 time, and a date not before the day it is due, and goes without a recurrence`() {
        val due = Due.On(day("2026-10-26"))
        fun problemsOf(end: RecurrenceEnd) = TodoDraft("Müll", due = due, recurrence = mondays, end = end).problems
        assertEquals(setOf(TodoProblem.EndTimesOutOfRange), problemsOf(RecurrenceEnd.AfterTimes(0)))
        assertEquals(setOf(TodoProblem.EndTimesOutOfRange), problemsOf(RecurrenceEnd.AfterTimes(1000)))
        assertEquals(setOf(TodoProblem.EndDateTooEarly), problemsOf(RecurrenceEnd.OnDate(day("2026-10-25"))))
        assertEquals(emptySet<TodoProblem>(), problemsOf(RecurrenceEnd.OnDate(day("2026-10-26"))))
        assertEquals(RecurrenceEnd.Never, TodoDraft("Müll", due = due, end = RecurrenceEnd.AfterTimes(3)).cleaned().end)
    }

    /** Ticks off the one open to-do on the morning of [date]. */
    private suspend fun doneOn(date: String) {
        clock.now = day(date).atTime(8, 0).atZone(clock.zone).toInstant()
        todos.markDone(todos.open().first().single().todo.id)
    }

    private fun day(text: String) = LocalDate.parse(text)
}
