package de.emilyshub.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.R
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
 * Notes in tabs (SPEC.md §7): "Alle" shows all of them, "Allgemein" those in no tab of the user's, which are made,
 * renamed, put in order and deleted, their notes going to "Allgemein" or with them.
 */
@RunWith(AndroidJUnit4::class)
class NotesTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a note is saved with its title and Markdown as typed, in Allgemein unless in a tab`() = runBlocking {
        val id = notes.create(NoteDraft(" Einkauf ", "  - Milch\n- Brot\n\n"))

        val saved = checkNotNull(notes.get(id))
        assertEquals("Einkauf" to "  - Milch\n- Brot", saved.title to saved.text)
        assertNull(saved.tabId)
        assertEquals(clock.now, saved.changedAt)
    }

    @Test
    fun `each tab shows its notes, the one changed last first, and Alle shows all`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val shopping = notes.create(NoteDraft("Einkauf"))
        clock.now += Duration.ofMinutes(1)
        val verbs = notes.create(NoteDraft("Verben", tabId = arabic))
        clock.now += Duration.ofMinutes(1)
        notes.update(shopping, NoteDraft("Einkauf", "- Milch"))

        assertEquals(listOf("Einkauf", "Verben"), titles(TabSelection.All))
        assertEquals(listOf("Einkauf"), titles(TabSelection.General))
        assertEquals(listOf("Verben"), titles(TabSelection.Own(arabic)))
        assertEquals(arabic, notes.get(verbs)?.tabId)
    }

    @Test
    fun `tabs are made last of all, renamed and put in order, each name once and never Alle or Allgemein`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val recipes = notes.createTab(" Rezepte ")
        notes.renameTab(recipes, "Kochen")
        assertEquals(listOf("Arabisch", "Kochen"), notes.tabs().first().map { it.name })

        notes.arrangeTabs(listOf(recipes, arabic))
        assertEquals(listOf("Kochen", "Arabisch"), notes.tabs().first().map { it.name })

        for (name in listOf("arabisch", "Allgemein", "alle", " ", "x".repeat(NoteTab.NAME_MAX + 1))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { notes.createTab(name) } }
        }
        assertEquals(NameProblem.Taken, tabNameProblem("ALLE", emptyList()))
    }

    @Test
    fun `the names no tab can take are those the screens show for the tabs the app has`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(NoteTab.FIXED_NAMES, listOf(R.string.notes_tab_all, R.string.notes_tab_general).map(context::getString))
    }

    @Test
    fun `a tab deleted moves its notes to Allgemein, or takes them along`() = runBlocking {
        val arabic = notes.createTab("Arabisch")
        val recipes = notes.createTab("Rezepte")
        val verbs = notes.create(NoteDraft("Verben", tabId = arabic))
        val soup = notes.create(NoteDraft("Linsensuppe", tabId = recipes))

        notes.deleteTabMovingNotes(arabic)
        notes.deleteTabWithNotes(recipes)

        assertEquals(emptyList<NoteTab>(), notes.tabs().first())
        assertNull(notes.get(verbs)?.tabId)
        assertEquals("Verben", notes.get(verbs)?.title)
        assertNull(notes.get(soup))
    }

    @Test
    fun `a note in a tab deleted meanwhile is saved in Allgemein`() = runBlocking {
        val gone = notes.createTab("Weg")
        notes.deleteTabWithNotes(gone)

        val id = notes.create(NoteDraft("Notiz", tabId = gone))

        assertNull(notes.get(id)?.tabId)
    }

    @Test
    fun `a note needs a title of at most 80 characters and a text of at most 100000`() {
        assertEquals(setOf(NoteProblem.TitleMissing), NoteDraft(" ", "Text").problems)
        assertEquals(setOf(NoteProblem.TitleTooLong), NoteDraft("x".repeat(81)).problems)
        assertEquals(setOf(NoteProblem.TextTooLong), NoteDraft("Roman", "x".repeat(100_001)).problems)
        assertEquals(emptySet<NoteProblem>(), NoteDraft("Roman", "x".repeat(100_000)).problems)
    }

    @Test
    fun `a note deleted is gone`() = runBlocking {
        val id = notes.create(NoteDraft("Einkauf"))

        notes.delete(id)

        assertNull(notes.get(id))
        assertEquals(emptyList<String>(), titles(TabSelection.All))
    }

    private suspend fun titles(selection: TabSelection) = notes.notes(selection).first().map { it.title }
}
