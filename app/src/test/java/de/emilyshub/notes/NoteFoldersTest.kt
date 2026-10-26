package de.emilyshub.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.NameProblem
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Folders of notes (SPEC.md §7): in a tab, in each other to any depth; moved with all in them, also to another tab, but
 * never into themselves; deleted with what is in them moving up a level or with it; each sorted as chosen.
 */
@RunWith(AndroidJUnit4::class)
class NoteFoldersTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `folders are made in a tab and in each other, to any depth, and renamed`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val grammar = notes.createFolder(" Grammatik ", Container(arabic))
        val verbs = notes.createFolder("Verben", Container(arabic, grammar))
        var deepest = verbs
        repeat(50) { deepest = notes.createFolder("Ebene $it", Container(arabic, deepest)) }

        notes.renameFolder(verbs, "Verbformen")

        val book = book()
        assertEquals(listOf("Grammatik", "Verbformen", "Ebene 0"), book.path(deepest).take(3).map { it.name })
        assertEquals(52, book.path(deepest).size)
        assertEquals(listOf("Grammatik"), book.foldersIn(Container(arabic)).map { it.name })
    }

    @Test
    fun `a note made in a folder lies there, and one moved goes last where it is moved to, unchanged`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val grammar = notes.createFolder("Grammatik", Container(arabic))
        val verbs = notes.create(NoteDraft("Verben", container = Container(arabic, grammar)))
        val alphabet = notes.create(NoteDraft("Alphabet", container = Container(arabic)))
        clock.now += Duration.ofHours(1)

        notes.moveNote(alphabet, Container(arabic, grammar))

        assertEquals(Container(arabic, grammar), notes.get(verbs)?.let { Container(it.tabId, it.folderId) })
        val moved = checkNotNull(notes.get(alphabet))
        assertEquals(Container(arabic, grammar) to 1, Container(moved.tabId, moved.folderId) to moved.position)
        assertEquals(Instant.parse("2026-10-23T05:00:00Z"), moved.changedAt)
    }

    @Test
    fun `a folder moved to another tab takes along all that is in it, at any depth`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val travel = notes.createTab("Reisen")
        val grammar = notes.createFolder("Grammatik", Container(arabic))
        val verbs = notes.createFolder("Verben", Container(arabic, grammar))
        val kataba = notes.create(NoteDraft("kataba", container = Container(arabic, verbs)))
        notes.createFolder("Ägypten", Container(travel))

        notes.moveFolder(grammar, Container(travel))

        val book = book()
        assertEquals(listOf("Ägypten", "Grammatik"), book.foldersIn(Container(travel)).map { it.name })
        assertEquals(Container(travel, grammar), book.folder(verbs)?.let(book::containerOf))
        assertEquals(Container(travel, verbs), notes.get(kataba)?.let { Container(it.tabId, it.folderId) })
        assertEquals(emptyList<NoteFolder>(), book.foldersIn(Container(arabic)))
    }

    @Test
    fun `a folder cannot be moved into itself nor into a folder in it`() = runBlocking {
        val grammar = notes.createFolder("Grammatik", Container())
        val verbs = notes.createFolder("Verben", Container(folderId = grammar))

        for (into in listOf(grammar, verbs)) {
            val moving = { runBlocking { notes.moveFolder(grammar, Container(folderId = into)) } }
            assertThrows(IllegalArgumentException::class.java) { moving() }
        }

        assertEquals(Container(), book().folder(grammar)?.let(book()::containerOf))
    }

    @Test
    fun `a folder deleted moves what is in it up a level, after what is there, or takes it all along`() = runBlocking {
        val grammar = notes.createFolder("Grammatik", Container())
        val verbs = notes.createFolder("Verben", Container(folderId = grammar))
        val present = notes.createFolder("Präsens", Container(folderId = verbs))
        val kataba = notes.create(NoteDraft("kataba", container = Container(folderId = present)))
        val rules = notes.create(NoteDraft("Regeln", container = Container(folderId = verbs)))
        notes.create(NoteDraft("Übersicht", container = Container(folderId = grammar)))

        notes.deleteFolderMovingUp(verbs)
        val book = book()
        assertEquals(listOf("Präsens"), book.foldersIn(Container(folderId = grammar)).map { it.name })
        assertEquals(Container(folderId = grammar) to 1, notes.get(rules)?.let { Container(it.tabId, it.folderId) to it.position })

        notes.deleteFolderWithContents(grammar)
        assertEquals(emptyList<NoteFolder>(), book().foldersIn(Container()))
        assertEquals(listOf(null, null), listOf(notes.get(kataba), notes.get(rules)))
        assertNull(book().folder(present))
    }

    @Test
    fun `sorted by hand, what lies in a folder is in the order it came there, and keeps an order put by hand`() = runBlocking {
        val recipes = notes.createFolder("Rezepte", Container())
        val inside = Container(folderId = recipes)
        notes.create(NoteDraft("Suppe", container = inside))
        val pizza = notes.create(NoteDraft("Pizza", container = inside))
        notes.create(NoteDraft("Brot", container = inside))
        notes.createFolder("Kuchen", inside)
        notes.createFolder("Getränke", inside)

        notes.sort(inside, NoteSort.Manual)
        assertEquals(listOf("Suppe", "Pizza", "Brot"), book().notesIn(inside).map { it.title })
        assertEquals(listOf("Kuchen", "Getränke"), book().foldersIn(inside).map { it.name })

        notes.arrangeNotes(inside, listOf(pizza))
        notes.sort(inside, NoteSort.Name)
        notes.create(NoteDraft("Salat", container = inside))
        notes.sort(inside, NoteSort.Manual)
        assertEquals(listOf("Pizza", "Suppe", "Brot", "Salat"), book().notesIn(inside).map { it.title })
    }

    @Test
    fun `the top of Allgemein and of each tab keep a sort of their own`() = runBlocking {
        val arabic = notes.createTab("Arabisch")

        notes.sort(Container(), NoteSort.Name)
        notes.sort(Container(arabic), NoteSort.Manual)

        val book = book()
        assertEquals(NoteSort.Name to NoteSort.Manual, book.sortOf(Container()) to book.sortOf(Container(arabic)))
        assertEquals(NoteSort.Changed, book.sortOf(Container(notes.createTab("Neu"))))
    }

    @Test
    fun `a tab deleted moves its folders to Allgemein after what lies there, or deletes them with it`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val recipes = notes.createTab("Rezepte")
        notes.createFolder("Reisen", Container())
        val grammar = notes.createFolder("Grammatik", Container(arabic))
        val verbs = notes.create(NoteDraft("Verben", container = Container(arabic, grammar)))
        val baking = notes.createFolder("Backen", Container(recipes))
        notes.createFolder("Kuchen", Container(recipes, baking))
        val bread = notes.create(NoteDraft("Brot", container = Container(recipes, baking)))

        notes.deleteTabMovingContents(arabic)
        notes.deleteTabWithContents(recipes)

        val book = book()
        assertEquals(1, book.folder(grammar)?.position)
        assertEquals(listOf("Grammatik", "Reisen"), book.foldersIn(Container()).map { it.name }.sorted())
        assertEquals(Container(folderId = grammar), notes.get(verbs)?.let { Container(it.tabId, it.folderId) })
        assertNull(book.folder(baking))
        assertNull(notes.get(bread))
    }

    @Test
    fun `a note saved in a folder deleted meanwhile is saved at the top of its tab`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val gone = notes.createFolder("Weg", Container(arabic))
        notes.deleteFolderWithContents(gone)

        val id = notes.create(NoteDraft("Notiz", container = Container(arabic, gone)))

        assertEquals(Container(arabic), notes.get(id)?.let { Container(it.tabId, it.folderId) })
    }

    @Test
    fun `a folder needs a name of at most 50 characters, which others may have too`() = runBlocking {
        assertEquals(NameProblem.Missing, folderNameProblem(" "))
        assertEquals(NameProblem.TooLong, folderNameProblem("x".repeat(51)))
        assertNull(folderNameProblem("x".repeat(50)))

        assertThrows(IllegalArgumentException::class.java) { runBlocking { notes.createFolder(" ", Container()) } }
        notes.createFolder("Neu", Container())
        notes.createFolder("Neu", Container())
        assertEquals(listOf("Neu", "Neu"), book().foldersIn(Container()).map { it.name })
    }

    private suspend fun book() = notes.notebook().first()
}
