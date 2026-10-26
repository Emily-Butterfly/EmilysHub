package de.emilyshub.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.todos.Due
import de.emilyshub.todos.Recurrence
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.WishStatus
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Notes and folders pinned, notes on Heute, and notes linked to goals, to-dos and routines, seen from both sides (SPEC.md
 * §7): a link goes with either side deleted, and one to a to-do coming again goes on with it.
 */
@RunWith(AndroidJUnit4::class)
class NotePinsAndLinksTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `notes and folders are pinned and loosened, a note pinned on Heute shows there, and saving it keeps its pins`() =
        runBlocking {
            val grammar = notes.createFolder("Grammatik", Container())
            val verbs = notes.create(NoteDraft("Verben"))
            notes.create(NoteDraft("Alphabet"))

            notes.pinFolder(grammar, pinned = true)
            notes.pinNote(verbs, pinned = true)
            notes.putOnToday(verbs, onToday = true)
            notes.update(verbs, NoteDraft("Verben", "- kataba"))

            val book = notes.notebook().first()
            assertEquals(true to true, book.folder(grammar)?.pinned to book.note(verbs)?.pinned)
            assertEquals(listOf("Verben"), notes.onToday().first().map { it.title })

            notes.putOnToday(verbs, onToday = false)
            notes.pinFolder(grammar, pinned = false)
            assertEquals(emptyList<Note>(), notes.onToday().first())
            assertEquals(false, notes.notebook().first().folder(grammar)?.pinned)
        }

    @Test
    fun `a note is linked to goals, to-dos and routines, which show it in turn, and a link goes with either side`() =
        runBlocking {
            val vocabulary = wishes.create(WishDraft("Wortschatz"))
            val book = todos.create(TodoDraft("Wörterbuch kaufen"))
            val routine = routines.create(RoutineDraft("Vokabeln", description = "", listOf(EIGHT), Repetition.Daily))
            val links = setOf(NoteLink.Goal(vocabulary), NoteLink.Todo(book), NoteLink.Routine(routine))
            val verbs = notes.create(NoteDraft("Verben", links = links))
            notes.create(NoteDraft("Alphabet", links = setOf(NoteLink.Goal(vocabulary))))

            assertEquals(links, notes.linksOf(verbs))
            assertEquals(listOf("Alphabet", "Verben"), notes.notesLinkedTo(NoteLink.Goal(vocabulary)).first().map { it.title })
            assertEquals(listOf("Verben"), notes.notesLinkedTo(NoteLink.Routine(routine)).first().map { it.title })

            todos.delete(book)
            assertEquals(links - NoteLink.Todo(book), notes.linksOf(verbs))
            notes.update(verbs, NoteDraft("Verben", links = setOf(NoteLink.Routine(routine))))
            assertEquals(listOf("Alphabet"), notes.notesLinkedTo(NoteLink.Goal(vocabulary)).first().map { it.title })
            notes.delete(verbs)
            assertEquals(emptyList<Note>(), notes.notesLinkedTo(NoteLink.Routine(routine)).first())
        }

    @Test
    fun `a link to something deleted meanwhile is left out on saving`() = runBlocking {
        val gone = todos.create(TodoDraft("Weg"))
        todos.delete(gone)

        val note = notes.create(NoteDraft("Notiz", links = setOf(NoteLink.Todo(gone))))

        assertEquals(emptySet<NoteLink>(), notes.linksOf(note))
    }

    @Test
    fun `what can be linked is named, and offered while not done nor archived`() = runBlocking {
        val done = wishes.create(WishDraft("Marathon", status = WishStatus.Done, doneOn = LocalDate.parse("2026-10-03")))
        val milk = todos.create(TodoDraft("Milch kaufen"))
        todos.markDone(milk)
        val yoga = routines.create(RoutineDraft("Yoga", description = "", listOf(EIGHT), Repetition.Daily))
        routines.archive(yoga)
        val open = todos.create(TodoDraft("Steuer machen"))

        val candidates = notes.linkCandidates().first().associateBy { it.link }

        assertEquals("Marathon" to false, candidates.getValue(NoteLink.Goal(done)).let { it.name to it.open })
        assertEquals(false, candidates.getValue(NoteLink.Todo(milk)).open)
        assertEquals(false, candidates.getValue(NoteLink.Routine(yoga)).open)
        assertEquals("Steuer machen" to true, candidates.getValue(NoteLink.Todo(open)).let { it.name to it.open })
    }

    @Test
    fun `a to-do coming again hands its notes on to the next, and takes them back when ticked off by mistake`() = runBlocking {
        val monthly = TodoDraft("Miete überweisen", due = Due.On(LocalDate.parse("2026-10-01")), recurrence = Recurrence.Monthly(1))
        val rent = todos.create(monthly)
        val note = notes.create(NoteDraft("Kontonummer", links = setOf(NoteLink.Todo(rent))))

        todos.markDone(rent)
        val next = checkNotNull(todos.get(rent)?.nextId)
        assertEquals(setOf(NoteLink.Todo(next)), notes.linksOf(note))

        todos.reopen(rent)
        assertEquals(setOf(NoteLink.Todo(rent)), notes.linksOf(note))
    }

    private companion object {
        val EIGHT: LocalTime = LocalTime.of(8, 0)
    }
}
