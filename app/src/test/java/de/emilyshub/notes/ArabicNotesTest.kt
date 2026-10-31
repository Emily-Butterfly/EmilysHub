package de.emilyshub.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.notes.Inline.Bold
import de.emilyshub.notes.Inline.Text
import de.emilyshub.search.Search
import de.emilyshub.ui.notes.startsRightToLeft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId

/** Notes written in Arabic (SPEC.md §7): saved as typed, read as Markdown, and found, with their vowel marks or without. */
@RunWith(AndroidJUnit4::class)
class ArabicNotesTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)
    private val search = Search({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a note in Arabic, in a tab and folder named in Arabic, is saved as it reads`() = runBlocking {
        val tab = notes.createTab("العربية")
        val folder = notes.createFolder("الأفعال", Container(tab))
        val text = "# الفعل الماضي\n- **كَتَبَ** – schreiben\n- قَرَأَ"

        val id = notes.create(NoteDraft("كَتَبَ يَكْتُبُ", text, Container(tab, folder)))

        val saved = checkNotNull(notes.get(id))
        assertEquals(nfc("كَتَبَ يَكْتُبُ") to nfc(text), saved.title to saved.text)
        assertEquals(Container(tab, folder), Container(saved.tabId, saved.folderId))
    }

    @Test
    fun `Arabic Markdown is read as such, bold and lists too`() {
        val blocks = Markdown.parse("# الفعل\n- **كتب** يكتب\n\nنص *مائل*")

        assertEquals(Block.Heading(1, listOf(Text("الفعل"))), blocks[0])
        assertEquals(Block.Bullets(listOf(ListItem(listOf(Bold(listOf(Text("كتب"))), Text(" يكتب"))))), blocks[1])
        assertEquals(Block.Paragraph(listOf(Text("نص "), Inline.Italic(listOf(Text("مائل"))))), blocks[2])
    }

    @Test
    fun `a block of Arabic goes right to left, one of German or code left to right`() {
        val blocks = Markdown.parse("- كتب\n\n- schreiben كتب\n\n> «مثال»\n\n```\nكتب\n```")

        assertEquals(listOf(true, false, true, false), blocks.map(::startsRightToLeft))
    }

    @Test
    fun `Arabic is found with its vowel marks or without, as learners type it`() = runBlocking {
        notes.create(NoteDraft("الماضي", "كَتَبَ – schreiben"))
        notes.create(NoteDraft("المضارع", "يكتب"))

        assertEquals(setOf("الماضي", "المضارع"), search.find("كتب").single().hits.map { it.title }.toSet())
        assertEquals(setOf("الماضي", "المضارع"), search.find("كَتَبَ").single().hits.map { it.title }.toSet())
        assertEquals(listOf("الماضي"), search.find("كـتـب –").single().hits.map { it.title })
        assertEquals(listOf("المضارع"), search.find("يَكْتُبُ").single().hits.map { it.title })
    }

    private fun nfc(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC)
}
