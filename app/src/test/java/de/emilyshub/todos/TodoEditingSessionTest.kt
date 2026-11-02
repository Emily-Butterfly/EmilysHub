package de.emilyshub.todos

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.ChecklistDraftItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Editing a to-do (SPEC.md §5): what the editor opens with, and what saving does; without the editor. */
@RunWith(AndroidJUnit4::class)
class TodoEditingSessionTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a to-do opens to edit as it was saved, with its checklist and which of its series it is`() = runBlocking {
        val draft = TodoDraft(
            "Müll", due = Due.On(LocalDate.parse("2026-10-23")),
            checklist = listOf(ChecklistDraftItem(text = "Gelber Sack")), recurrence = Recurrence.Daily,
        )
        val first = saved(draft)
        todos.markDone(first)
        val next = todos.open().first().single().todo.id

        val editing = checkNotNull(todos.editing(next))

        assertEquals(next, editing.id)
        assertEquals(2, editing.seriesNumber)
        assertEquals("Müll", editing.original?.title)
        assertEquals(listOf("Gelber Sack"), editing.original?.checklist?.map { it.text })
    }

    @Test
    fun `a to-do deleted meanwhile does not open`() = runBlocking {
        val id = saved(TodoDraft("Müll"))
        todos.delete(id)

        assertNull(todos.editing(id))
    }

    @Test
    fun `a new to-do is saved, and one opened saves its changes`() = runBlocking {
        val id = saved(TodoDraft("Müll"))
        val editing = checkNotNull(todos.editing(id))

        assertEquals(Saving.Saved(id), todos.save(editing, checkNotNull(editing.original).copy(title = "Müll raus")))

        assertEquals("Müll raus", todos.get(id)?.title)
    }

    @Test
    fun `a draft with problems is refused, and nothing is saved`() = runBlocking {
        assertEquals(Saving.Refused(setOf(TodoProblem.TitleMissing)), todos.save(TodoEditing.New, TodoDraft(" ")))
        assertTrue(todos.open().first().isEmpty())
    }

    private suspend fun saved(draft: TodoDraft): Long = (todos.save(TodoEditing.New, draft) as Saving.Saved).id
}
