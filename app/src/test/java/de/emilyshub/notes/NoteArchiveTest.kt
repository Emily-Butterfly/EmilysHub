package de.emilyshub.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.search.Search
import de.emilyshub.search.SearchArea
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The archive of notes (SPEC.md §7): notes and folders archived show nowhere but there, lose their pins, come back where
 * they were or as near as there is still, are deleted there for good, and outlast the folder or tab they were in.
 */
@RunWith(AndroidJUnit4::class)
class NoteArchiveTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val search = Search({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a note archived shows nowhere but in the archive, pinned nowhere, and comes back where it was, unpinned`() =
        runBlocking {
            val arabic = notes.createTab("Arabisch")
            val grammar = notes.createFolder("Grammatik", Container(arabic))
            val verbs = notes.create(NoteDraft("Verben", "kataba", Container(arabic, grammar)))
            notes.create(NoteDraft("Nomen", container = Container(arabic, grammar)))
            notes.pinNote(verbs, pinned = true)
            notes.pinOnToday(verbs, pinned = true)

            notes.archiveNote(verbs)

            var book = book()
            assertEquals(listOf("Nomen"), book.notesIn(Container(arabic, grammar)).map { it.title })
            assertEquals(listOf("Nomen"), book.all().map { it.title })
            assertEquals(listOf(NoteEntry.OfNote(checkNotNull(book.note(verbs)))), book.archived())
            assertEquals(emptyList<Note>(), notes.onToday().first())

            notes.restoreNote(verbs)

            book = book()
            val back = checkNotNull(book.note(verbs))
            assertEquals(Container(arabic, grammar), book.containerOf(back))
            assertEquals(false to false, back.pinned to back.onToday)
            assertEquals(emptyList<NoteEntry>(), book.archived())
        }

    @Test
    fun `a folder archived takes all in it along, its notes off Heute, and comes back with it`() = runBlocking {
        val grammar = notes.createFolder("Grammatik", Container())
        val verbs = notes.createFolder("Verben", Container(null, grammar))
        val kataba = notes.create(NoteDraft("kataba", container = Container(null, verbs)))
        notes.pinFolder(grammar, pinned = true)
        notes.pinOnToday(kataba, pinned = true)

        notes.archiveFolder(grammar)

        var book = book()
        assertEquals(emptyList<NoteFolder>(), book.foldersIn(Container()))
        assertTrue(book.isEmpty(Container()))
        assertEquals(emptyList<Note>(), book.all())
        assertEquals(listOf("Grammatik"), book.archived().map { (it as NoteEntry.OfFolder).folder.name })
        assertTrue(book.isArchived(checkNotNull(book.note(kataba))))
        assertEquals(listOf("Verben"), book.foldersIn(Container(null, grammar), withArchived = true).map { it.name })
        assertEquals(emptyList<Note>(), notes.onToday().first())

        notes.restoreFolder(grammar)

        book = book()
        assertEquals(listOf("Grammatik"), book.foldersIn(Container()).map { it.name })
        assertEquals(false, book.folder(grammar)?.pinned)
        assertEquals(listOf("kataba"), book.all().map { it.title })
    }

    @Test
    fun `archived notes are not found by the search nor shown linked, and are again once restored`() = runBlocking {
        val dictionary = todos.create(TodoDraft("Wörterbuch kaufen"))
        val grammar = notes.createFolder("Grammatik", Container())
        val verbs = notes.create(NoteDraft("Verben", "kataba", Container(null, grammar), links = setOf(NoteLink.Todo(dictionary))))
        val nouns = notes.create(NoteDraft("Nomen", "kitab", links = setOf(NoteLink.Todo(dictionary))))

        notes.archiveNote(nouns)
        notes.archiveFolder(grammar)

        assertEquals(emptyList<Note>(), notes.notesLinkedTo(NoteLink.Todo(dictionary)).first())
        assertTrue(search.find("kataba").none { it.area == SearchArea.Notes })
        assertTrue(search.find("kitab").none { it.area == SearchArea.Notes })

        notes.restoreFolder(grammar)
        notes.restoreNote(nouns)

        assertEquals(listOf("Nomen", "Verben"), notes.notesLinkedTo(NoteLink.Todo(dictionary)).first().map { it.title })
        assertEquals(setOf(NoteLink.Todo(dictionary)), notes.linksOf(verbs))
        assertEquals(listOf("Verben"), search.find("kataba").single { it.area == SearchArea.Notes }.hits.map { it.title })
    }

    @Test
    fun `the archive has the last archived first, those in a folder archived only in it, and they stay once it comes back`() =
        runBlocking {
            val grammar = notes.createFolder("Grammatik", Container())
            val verbs = notes.create(NoteDraft("Verben", container = Container(null, grammar)))
            val alphabet = notes.create(NoteDraft("Alphabet"))
            notes.archiveNote(verbs)
            clock.now += Duration.ofMinutes(1)
            notes.archiveNote(alphabet)
            clock.now += Duration.ofMinutes(1)
            notes.archiveFolder(grammar)

            assertEquals(listOf("Grammatik", "Alphabet"), book().archived().map(::nameOf))
            assertEquals(listOf("Verben"), book().entriesIn(Container(null, grammar), withArchived = true).map(::nameOf))

            notes.restoreFolder(grammar)

            assertEquals(listOf("Alphabet", "Verben"), book().archived().map(::nameOf))
            assertEquals(emptyList<Note>(), book().notesIn(Container(null, grammar)))
        }

    @Test
    fun `restored without its folder, a note goes to the nearest folder above still out of the archive, else the top`() =
        runBlocking {
            val arabic = notes.createTab("Arabisch")
            val grammar = notes.createFolder("Grammatik", Container(arabic))
            val verbs = notes.createFolder("Verben", Container(arabic, grammar))
            val kataba = notes.create(NoteDraft("kataba", container = Container(arabic, verbs)))
            val qaraa = notes.create(NoteDraft("qaraa", container = Container(arabic, verbs)))
            notes.archiveNote(kataba)
            notes.archiveNote(qaraa)
            notes.archiveFolder(verbs)

            notes.restoreNote(kataba)
            assertEquals(Container(arabic, grammar), book().containerOf(checkNotNull(book().note(kataba))))

            notes.archiveFolder(grammar)
            notes.restoreNote(qaraa)
            assertEquals(Container(arabic), book().containerOf(checkNotNull(book().note(qaraa))))

            notes.deleteTabMovingContents(arabic)
            notes.restoreFolder(grammar)
            assertEquals(listOf("Grammatik"), book().foldersIn(Container()).map { it.name })
        }

    @Test
    fun `a folder deleted with all in it keeps what is in the archive, a level up, still archived`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val grammar = notes.createFolder("Grammatik", Container(arabic))
        val verbs = notes.createFolder("Verben", Container(arabic, grammar))
        val forms = notes.createFolder("Formen", Container(arabic, verbs))
        notes.create(NoteDraft("Form I", container = Container(arabic, forms)))
        val kataba = notes.create(NoteDraft("kataba", container = Container(arabic, verbs)))
        notes.create(NoteDraft("qaraa", container = Container(arabic, verbs)))
        notes.archiveNote(kataba)
        notes.archiveFolder(forms)

        notes.deleteFolderWithContents(verbs)

        val book = book()
        assertNull(book.folder(verbs))
        assertEquals(listOf("Formen", "kataba"), book.archived().map(::nameOf))
        assertEquals(Container(arabic, grammar), book.containerOf(checkNotNull(book.folder(forms))))
        assertEquals(Container(arabic, grammar), book.containerOf(checkNotNull(book.note(kataba))))
        assertEquals(emptyList<Note>(), book.all())
    }

    @Test
    fun `a folder deleted moving up takes what is in the archive up too`() = runBlocking {
        val grammar = notes.createFolder("Grammatik", Container())
        val kataba = notes.create(NoteDraft("kataba", container = Container(null, grammar)))
        notes.archiveNote(kataba)

        notes.deleteFolderMovingUp(grammar)

        assertEquals(Container(), book().containerOf(checkNotNull(book().note(kataba))))
        assertTrue(book().isArchived(checkNotNull(book().note(kataba))))
    }

    @Test
    fun `a tab deleted with all in it keeps what is in the archive, in Allgemein`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val grammar = notes.createFolder("Grammatik", Container(arabic))
        val verbs = notes.createFolder("Verben", Container(arabic, grammar))
        notes.create(NoteDraft("kataba", container = Container(arabic, verbs)))
        val alphabet = notes.create(NoteDraft("Alphabet", container = Container(arabic)))
        val nouns = notes.create(NoteDraft("Nomen", container = Container(arabic, grammar)))
        notes.create(NoteDraft("Weg", container = Container(arabic, grammar)))
        notes.archiveFolder(verbs)
        notes.archiveNote(alphabet)
        notes.archiveNote(nouns)

        notes.deleteTabWithContents(arabic)

        val book = book()
        assertEquals(setOf("Verben", "Alphabet", "Nomen"), book.archived().map(::nameOf).toSet())
        assertEquals(setOf("kataba", "Verben", "Alphabet", "Nomen"), (book.allNotesAndFolders()).toSet())
        assertTrue(notesHoldTogether(database.notes().folders(), database.notes().notes()))
        notes.restoreFolder(verbs)
        assertEquals(listOf("kataba"), book().all().map { it.title })
    }

    @Test
    fun `deleted for good from the archive, a note or a folder with all in it is gone`() = runBlocking {
        val grammar = notes.createFolder("Grammatik", Container())
        val verbs = notes.createFolder("Verben", Container(null, grammar))
        val kataba = notes.create(NoteDraft("kataba", container = Container(null, verbs)))
        val alphabet = notes.create(NoteDraft("Alphabet"))
        notes.archiveNote(kataba)
        notes.archiveNote(alphabet)
        notes.archiveFolder(grammar)

        notes.delete(alphabet)
        notes.deleteArchivedFolder(grammar)

        assertEquals(emptyList<String>(), book().allNotesAndFolders())
        assertFalse(database.notes().notes().any())
    }

    @Test
    fun `a note archived keeps being archived when saved`() = runBlocking {
        val verbs = notes.create(NoteDraft("Verben"))
        notes.archiveNote(verbs)

        notes.update(verbs, NoteDraft("Verben", "kataba"))

        assertTrue(book().isArchived(checkNotNull(book().note(verbs))))
    }

    private suspend fun book() = notes.notebook().first()

    private suspend fun Notebook.allNotesAndFolders(): List<String> =
        database.notes().folders().map { it.name } + database.notes().notes().map { it.title }

    private fun nameOf(entry: NoteEntry): String = when (entry) {
        is NoteEntry.OfFolder -> entry.folder.name
        is NoteEntry.OfNote -> entry.note.title
    }
}
