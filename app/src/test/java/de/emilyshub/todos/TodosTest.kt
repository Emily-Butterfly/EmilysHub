package de.emilyshub.todos

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.ChecklistEntry
import de.emilyshub.routines.Priority
import de.emilyshub.tags.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** To-dos (SPEC.md §5): one-off tasks, due or not, ticked off into the archive. */
@RunWith(AndroidJUnit4::class)
class TodosTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private var changes = 0
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined, onChange = { changes++ })
    private val tags = Tags({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a to-do is saved with its title, description, due date and time, priority, checklist and tags, as typed`() = runBlocking {
        val errands = tags.create("Erledigungen")
        val draft = TodoDraft(
            title = " Steuererklärung ",
            description = "Belege sammeln ",
            due = Due.At(today.plusDays(3), LocalTime.of(18, 0)),
            priority = Priority.High,
            checklist = items("Belege", " ", "Abschicken"),
            tagIds = setOf(errands),
        )

        val id = todos.create(draft)

        val saved = checkNotNull(todos.get(id))
        assertEquals("Steuererklärung", saved.title)
        assertEquals("Belege sammeln", saved.description)
        assertEquals(Due.At(today.plusDays(3), LocalTime.of(18, 0)), saved.due)
        assertEquals(Priority.High, saved.priority)
        assertEquals(listOf("Belege", "Abschicken"), todos.checklist(id).map { it.text })
        assertEquals(setOf(errands), todos.tagIdsOf(id))
        assertEquals(1, changes)
    }

    @Test
    fun `a to-do is due on a day, on a day at a time, or not at all`() = runBlocking {
        val onTheDay = todos.create(TodoDraft("Müll", due = Due.On(today)))
        val whenever = todos.create(TodoDraft("Fenster putzen"))

        assertEquals(Due.On(today), todos.get(onTheDay)?.due)
        assertEquals(Due.None, todos.get(whenever)?.due)
    }

    @Test
    fun `a to-do needs a title of up to 80 characters, and keeps to the limits of description and checklist`() {
        assertEquals(setOf(TodoProblem.TitleMissing), TodoDraft(" ").problems)
        assertEquals(setOf(TodoProblem.TitleTooLong), TodoDraft("a".repeat(81)).problems)
        assertEquals(emptySet<TodoProblem>(), TodoDraft("a".repeat(80)).problems)
        assertEquals(setOf(TodoProblem.DescriptionTooLong), TodoDraft("Steuer", description = "a".repeat(1001)).problems)
        val items = (1..21).map { ChecklistDraftItem(text = "Punkt $it") }
        assertEquals(setOf(TodoProblem.ChecklistTooLong), TodoDraft("Steuer", checklist = items).problems)
        val long = listOf(ChecklistDraftItem(text = "a".repeat(81)))
        assertEquals(setOf(TodoProblem.ChecklistItemTooLong), TodoDraft("Steuer", checklist = long).problems)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { todos.create(TodoDraft(" ")) } }
    }

    @Test
    fun `a to-do is changed, the ticks of the checklist items kept staying, and deleted with them for good`() = runBlocking {
        val id = todos.create(TodoDraft("Umzug", checklist = items("Kartons", "Wagen")))
        val (boxes, van) = todos.checklist(id)
        todos.tick(id, boxes.id, ticked = true)

        val changed = listOf(ChecklistDraftItem(boxes.id, "Kartons"), ChecklistDraftItem(text = "Helfer"))
        todos.update(id, TodoDraft("Umzug planen", checklist = changed))

        assertEquals("Umzug planen", todos.get(id)?.title)
        assertEquals(listOf("Kartons" to true, "Helfer" to false), todos.checklist(id).map { it.text to it.ticked })
        assertEquals(false, van.id in todos.checklist(id).map { it.id })

        todos.delete(id)
        assertEquals(null, todos.get(id))
        assertEquals(emptyList<String>(), todos.checklist(id).map { it.text })
    }

    @Test
    fun `ticked off, a to-do goes to the archive, the one done last first, and restored, it is open again`() = runBlocking {
        val taxes = todos.create(TodoDraft("Steuer"))
        val window = todos.create(TodoDraft("Fenster"))
        todos.markDone(taxes)
        clock.now = clock.now.plusSeconds(60)
        todos.markDone(window)

        assertEquals(emptyList<String>(), todos.open().first().map { it.todo.title })
        assertEquals(listOf("Fenster", "Steuer"), todos.archived().first().map { it.title })

        todos.reopen(taxes)
        assertEquals(listOf("Steuer"), todos.open().first().map { it.todo.title })
        assertEquals(listOf("Fenster"), todos.archived().first().map { it.title })
    }

    @Test
    fun `with all its checklist ticked off, a to-do is done, which ticking says, and unticking one opens it again`() =
        runBlocking {
            val id = todos.create(TodoDraft("Umzug", checklist = items("Kartons", "Wagen")))
            val (boxes, van) = todos.checklist(id)

            assertEquals(false, todos.tick(id, boxes.id, ticked = true))
            assertEquals(null, todos.get(id)?.doneAt)
            assertEquals(true, todos.tick(id, van.id, ticked = true))
            assertEquals(clock.now, todos.get(id)?.doneAt)

            todos.tick(id, boxes.id, ticked = false)
            assertEquals(null, todos.get(id)?.doneAt)
            assertEquals(listOf(false, true), todos.checklist(id).map { it.ticked })
        }

    @Test
    fun `restored, a to-do keeps what was ticked off, unless all of it was, as that made it done`() = runBlocking {
        val partly = todos.create(TodoDraft("Umzug", checklist = items("Kartons", "Wagen")))
        val (boxes, van) = todos.checklist(partly)
        todos.tick(partly, boxes.id, ticked = true)
        todos.markDone(partly)
        todos.reopen(partly)
        assertEquals(listOf(true, false), todos.checklist(partly).map { it.ticked })

        todos.tick(partly, van.id, ticked = true)
        todos.reopen(partly)
        assertEquals(listOf(false, false), todos.checklist(partly).map { it.ticked })
    }

    @Test
    fun `by due, the soonest due first, so the overdue ones at the top, a day after its times, then by priority and title`() =
        runBlocking {
            todos.create(TodoDraft("Ohne", priority = Priority.High))
            todos.create(TodoDraft("Morgen abends", due = Due.At(today.plusDays(1), LocalTime.of(20, 0))))
            todos.create(TodoDraft("Morgen", due = Due.On(today.plusDays(1))))
            todos.create(TodoDraft("Heute", due = Due.On(today)))
            todos.create(TodoDraft("Heute früh", due = Due.At(today, LocalTime.of(6, 30))))
            todos.create(TodoDraft("Gestern", due = Due.On(today.minusDays(1))))
            val tomorrowAtEight = Due.At(today.plusDays(1), LocalTime.of(8, 0))
            todos.create(TodoDraft("Morgen früh wichtig", due = tomorrowAtEight, priority = Priority.High))
            todos.create(TodoDraft("Morgen früh", due = tomorrowAtEight))

            assertEquals(
                listOf("Gestern", "Heute früh", "Heute", "Morgen früh wichtig", "Morgen früh", "Morgen abends", "Morgen", "Ohne"),
                todos.open().first().sortedWith(TodoOrder.ByDue.comparator).map { it.todo.title },
            )
        }

    @Test
    fun `by priority, the high ones come first, then by due`() = runBlocking {
        todos.create(TodoDraft("Niedrig", due = Due.On(today), priority = Priority.Low))
        todos.create(TodoDraft("Normal später", due = Due.On(today.plusDays(2))))
        todos.create(TodoDraft("Normal bald", due = Due.On(today)))
        todos.create(TodoDraft("Hoch ohne", priority = Priority.High))

        assertEquals(
            listOf("Hoch ohne", "Normal bald", "Normal später", "Niedrig"),
            todos.open().first().sortedWith(TodoOrder.ByPriority.comparator).map { it.todo.title },
        )
    }

    @Test
    fun `on Heute, the to-dos due by today, open or ticked off today, the overdue ones marked so`() = runBlocking {
        todos.create(TodoDraft("Seit gestern", due = Due.On(today.minusDays(1))))
        todos.create(TodoDraft("Heute früh", due = Due.At(today, LocalTime.of(6, 30))))
        todos.create(TodoDraft("Heute abends", due = Due.At(today, LocalTime.of(20, 0))))
        todos.create(TodoDraft("Heute", due = Due.On(today)))
        todos.create(TodoDraft("Morgen", due = Due.On(today.plusDays(1))))
        todos.create(TodoDraft("Ohne"))
        val doneBefore = todos.create(TodoDraft("Vorgestern erledigt", due = Due.On(today.minusDays(2))))
        clock.now = berlin("2026-10-22T09:00")
        todos.markDone(doneBefore)
        clock.now = berlin("2026-10-23T07:00")
        val doneToday = todos.create(TodoDraft("Heute erledigt", due = Due.On(today)))
        todos.markDone(doneToday)

        val due = todos.dueOn(today, clock.now).first().associate { it.title to (it.overdue to it.done) }

        assertEquals(
            mapOf(
                "Seit gestern" to (true to false),
                "Heute früh" to (true to false),
                "Heute abends" to (false to false),
                "Heute" to (false to false),
                "Heute erledigt" to (false to true),
            ),
            due,
        )
    }

    @Test
    fun `a to-do is overdue once the minute of its time is over, or its day, and only while it is open`() = runBlocking {
        val doctor = todos.create(TodoDraft("Arzt", due = Due.At(today, LocalTime.of(9, 30))))
        val bins = todos.create(TodoDraft("Müll", due = Due.On(today)))
        suspend fun overdue(at: String) =
            todos.open().first().filter { todos.isOverdue(it.todo, berlin(at)) }.map { it.todo.title }.sorted()

        assertEquals(emptyList<String>(), overdue("2026-10-23T09:30:59"))
        assertEquals(listOf("Arzt"), overdue("2026-10-23T09:31"))
        assertEquals(listOf("Arzt"), overdue("2026-10-23T23:59:59"))
        assertEquals(listOf("Arzt", "Müll"), overdue("2026-10-24T00:00"))
        val onHeute = todos.dueOn(today, berlin("2026-10-23T09:31")).first().map { it.title to it.overdue }
        assertEquals(setOf("Arzt" to true, "Müll" to false), onHeute.toSet())

        todos.markDone(doctor)
        todos.markDone(bins)
        assertEquals(false, todos.dueOn(today, berlin("2026-10-24T00:00")).first().any { it.overdue })
    }

    @Test
    fun `a to-do on Heute shows its checklist, all ticked once it is done`() = runBlocking {
        val id = todos.create(
            TodoDraft("Umzug", due = Due.On(today), checklist = items("Kartons", "Wagen")),
        )
        val (boxes) = todos.checklist(id)
        todos.tick(id, boxes.id, ticked = true)
        assertEquals(listOf(true, false), todos.dueOn(today, clock.now).first().single().checklist.map(ChecklistEntry::ticked))

        todos.markDone(id)
        assertEquals(listOf(true, true), todos.dueOn(today, clock.now).first().single().checklist.map(ChecklistEntry::ticked))
    }

    @Test
    fun `a tag deleted is taken off the to-dos`() = runBlocking {
        val errands = tags.create("Erledigungen")
        val id = todos.create(TodoDraft("Post", tagIds = setOf(errands)))

        tags.delete(errands)

        assertEquals(emptySet<Long>(), todos.tagIdsOf(id))
    }

    private fun items(vararg texts: String) = texts.map { ChecklistDraftItem(text = it) }

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(clock.timeZone).toInstant()
}
