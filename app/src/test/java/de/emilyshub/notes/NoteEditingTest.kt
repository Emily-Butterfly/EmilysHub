package de.emilyshub.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/**
 * A note opened to edit and saved (SPEC.md §7): read at once with where it lies, what it is linked to and its tags, and
 * whether it is in the archive, only to read; saved new or changed, or refused for its problems, with nothing saved.
 */
@RunWith(AndroidJUnit4::class)
class NoteEditingTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-11-11T08:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a note opens as it is saved, with where it lies, its links and its tags`() = runBlocking {
        val tab = notes.createTab("Arabisch")
        val goal = wishes.create(WishDraft("Arabisch lernen"))
        val draft = NoteDraft("Verben", "- kataba", Container(tab), links = setOf(NoteLink.Goal(goal)))
        val id = notes.create(draft)

        val editing = notes.editing(id)

        assertEquals(NoteEditing(id, draft), editing)
        assertFalse(editing!!.archived)
    }

    @Test
    fun `a note archived, itself or in its folder, opens only to read, and one deleted not at all`() = runBlocking {
        val folder = notes.createFolder("Alt", Container())
        val inFolder = notes.create(NoteDraft("Liste", container = Container(folderId = folder)))
        val alone = notes.create(NoteDraft("Zettel"))
        notes.archiveFolder(folder)
        notes.archiveNote(alone)
        val deleted = notes.create(NoteDraft("Weg"))
        notes.delete(deleted)

        assertTrue(notes.editing(inFolder)!!.archived)
        assertTrue(notes.editing(alone)!!.archived)
        assertNull(notes.editing(deleted))
    }

    @Test
    fun `saving makes a new note or changes the one opened`() = runBlocking {
        val made = notes.save(NoteEditing.New, NoteDraft("Einkauf"))
        val id = (made as Saving.Saved).id

        val changed = notes.save(notes.editing(id)!!, NoteDraft("Einkauf", "- Milch"))

        assertEquals(Saving.Saved(id), changed)
        assertEquals("- Milch", notes.get(id)!!.text)
    }

    @Test
    fun `a draft with problems is refused, and nothing is saved`() = runBlocking {
        val id = notes.create(NoteDraft("Einkauf"))

        val refused = notes.save(notes.editing(id)!!, NoteDraft("  ", "x".repeat(NoteDraft.TEXT_MAX + 1)))

        assertEquals(Saving.Refused(setOf(NoteProblem.TitleMissing, NoteProblem.TextTooLong)), refused)
        assertEquals("Einkauf", notes.get(id)!!.title)
    }
}
