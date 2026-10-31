package de.emilyshub.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The tabs, folders and notes as the screens show them (SPEC.md §7): in a folder its folders first, then its notes,
 * each in the order the folder is sorted by; a folder found by its path, with all that is in it at any depth.
 */
class NotebookTest {

    private val arabic = NoteTab(id = 1, name = "Arabisch", position = 0, sort = NoteSort.Name)

    @Test
    fun `a tab shows its folders before its notes, each by name, when sorted by name`() {
        val book = Notebook(
            tabs = listOf(arabic),
            folders = listOf(folder(10, "Wortschatz"), folder(11, "Grammatik"), folder(12, "Ägypten")),
            notes = listOf(note(20, "Zahlen"), note(21, "Alphabet"), note(22, "Übungen")),
            generalSort = NoteSort.Changed,
        )

        val top = Container(arabic.id)
        assertEquals(listOf("Ägypten", "Grammatik", "Wortschatz"), book.foldersIn(top).map { it.name })
        assertEquals(listOf("Alphabet", "Übungen", "Zahlen"), book.notesIn(top).map { it.title })
        assertEquals(NoteSort.Name, book.sortOf(top))
    }

    @Test
    fun `sorted by last change, a folder counts as changed when anything in it was, at any depth`() {
        val book = Notebook(
            tabs = listOf(arabic),
            folders = listOf(
                folder(10, "Grammatik", sort = NoteSort.Changed),
                folder(11, "Verben", parent = 10, changedAt = at("07:00")),
                folder(12, "Nomen", parent = 10, changedAt = at("08:00")),
                folder(13, "Präsens", parent = 11, changedAt = at("07:00")),
            ),
            notes = listOf(
                note(20, "Endungen", folder = 10, changedAt = at("08:00")),
                note(21, "Regeln", folder = 10, changedAt = at("09:00")),
                note(22, "kataba", folder = 13, changedAt = at("11:00")),
            ),
            generalSort = NoteSort.Name,
        )

        val grammar = Container(arabic.id, folderId = 10)
        assertEquals(listOf("Verben", "Nomen"), book.foldersIn(grammar).map { it.name })
        assertEquals(listOf("Regeln", "Endungen"), book.notesIn(grammar).map { it.title })
    }

    @Test
    fun `sorted by hand, what a folder holds is in the order it was put in`() {
        val book = Notebook(
            tabs = emptyList(),
            folders = listOf(folder(10, "B", tab = null, position = 1), folder(11, "A", tab = null, position = 0)),
            notes = listOf(note(20, "Zwei", tab = null, position = 1), note(21, "Eins", tab = null, position = 0)),
            generalSort = NoteSort.Manual,
        )

        assertEquals(listOf("A", "B"), book.foldersIn(Container()).map { it.name })
        assertEquals(listOf("Eins", "Zwei"), book.notesIn(Container()).map { it.title })
    }

    @Test
    fun `pinned folders come first, then pinned notes, then the other folders and notes, each as the folder is sorted`() {
        val book = Notebook(
            tabs = listOf(arabic),
            folders = listOf(folder(10, "Wortschatz"), folder(11, "Grammatik").copy(pinned = true), folder(12, "Alt")),
            notes = listOf(note(20, "Zahlen").copy(pinned = true), note(21, "Alphabet"), note(22, "Aussprache").copy(pinned = true)),
            generalSort = NoteSort.Name,
        )

        val shown = book.entriesIn(Container(arabic.id)).map {
            when (it) {
                is NoteEntry.OfFolder -> it.folder.name
                is NoteEntry.OfNote -> it.note.title
            }
        }
        assertEquals(listOf("Grammatik", "Aussprache", "Zahlen", "Alt", "Wortschatz", "Alphabet"), shown)
    }

    @Test
    fun `a folder's path leads from the top of its tab down to it, and all that is in it is found at any depth`() {
        val book = Notebook(
            tabs = listOf(arabic),
            folders = listOf(folder(10, "Grammatik"), folder(11, "Verben", parent = 10), folder(12, "Präsens", parent = 11)),
            notes = listOf(note(20, "kataba", folder = 12), note(21, "Alphabet")),
            generalSort = NoteSort.Name,
        )

        assertEquals(listOf("Grammatik", "Verben", "Präsens"), book.path(12).map { it.name })
        assertEquals(emptyList<NoteFolder>(), book.path(null))
        assertEquals(setOf(11L, 12L), book.inAndBelow(11))
        assertEquals(Container(arabic.id, folderId = 12), book.containerOf(book.notesIn(Container(arabic.id, 12)).single()))
    }

    @Test
    fun `a folder with nothing in it is empty, and so is a tab`() {
        val book = Notebook(
            tabs = listOf(arabic),
            folders = listOf(folder(10, "Grammatik"), folder(11, "Leer")),
            notes = listOf(note(20, "Regeln", folder = 10)),
            generalSort = NoteSort.Name,
        )

        assertEquals(listOf(false, true), listOf(10L, 11L).map { book.isEmpty(Container(arabic.id, it)) })
        assertEquals(false to true, book.isEmpty(Container(arabic.id)) to book.isEmpty(Container()))
    }

    @Test
    fun `all notes of all tabs show those pinned first, each the one changed last first`() {
        val book = Notebook(
            tabs = listOf(arabic),
            folders = listOf(folder(10, "Grammatik")),
            notes = listOf(
                note(20, "Alt", changedAt = at("07:00")),
                note(21, "Neu", tab = null, changedAt = at("09:00")),
                note(22, "Mitte", folder = 10, changedAt = at("08:00")),
                note(23, "Angepinnt", changedAt = at("06:00")).copy(pinned = true),
            ),
            generalSort = NoteSort.Name,
        )

        assertEquals(listOf("Angepinnt", "Neu", "Mitte", "Alt"), book.all().map { it.title })
    }

    private fun folder(
        id: Long,
        name: String,
        tab: Long? = arabic.id,
        parent: Long? = null,
        position: Int = 0,
        sort: NoteSort = NoteSort.Name,
        changedAt: Instant = at("06:00"),
    ) = NoteFolder(id, name, tab, parent, position, sort, changedAt)

    private fun note(
        id: Long,
        title: String,
        tab: Long? = arabic.id,
        folder: Long? = null,
        position: Int = 0,
        changedAt: Instant = at("06:00"),
    ) = Note(id, title, text = "", tabId = tab, changedAt = changedAt, folderId = folder, position = position)

    private fun at(time: String) = Instant.parse("2026-10-23T${time}:00Z")
}
