package de.emilyshub

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Sprachen (SPEC.md §15): opened in Sonstiges and kept open there; a lesson entered with a teacher made on the spot; and
 * a language's lessons shown for one teacher only.
 */
@RunWith(AndroidJUnit4::class)
class LanguagesScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `Sprachen opens as the third entry of Sonstiges, in the tab, and stays open past another tab and a turn`() {
        launchApp().use { scenario ->
            tab("Sonstiges").performClick()
            compose.onNode(hasText("Sprachen") and hasClickAction()).performClick()

            heading("Sprachen").assertIsDisplayed()
            tab("Sonstiges").assertIsSelected()
            compose.onNodeWithText("Noch keine Sessions", substring = true).assertIsDisplayed()

            tab("Heute").performClick()
            tab("Sonstiges").performClick()
            heading("Sprachen").assertIsDisplayed()
            scenario.recreate()
            heading("Sprachen").assertIsDisplayed()

            compose.onNodeWithContentDescription("Zurück").performClick()
            heading("Sonstiges").assertIsDisplayed()
        }
    }

    @Test
    fun `a lesson is entered with a new language and a teacher made on the spot, and counts on the start page`() {
        launchApp().use {
            openLanguages()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
            heading("Neue Session").assertIsDisplayed()

            compose.onNode(hasText("Neue Sprache") and hasClickAction()).performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Arabisch")
            compose.onNode(hasText("Anlegen") and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("Neuer Lehrer") and hasClickAction()).performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Amal")
            compose.onNode(hasText("Anlegen") and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("45 Min.") and isSelectable()).performClick()
            compose.onNode(hasSetTextAction() and hasText("Preis in Euro (optional)")).performScrollTo().performTextInput("12,50")
            compose.onNodeWithText("Speichern").performClick()

            heading("Sprachen").assertIsDisplayed()
            // On the card of all languages and on that of Arabisch alike.
            compose.onAllNodesWithText("Insgesamt: 45 Min. · 1 Session · 12,50 €").assertCountEquals(2)
            compose.onNode(hasText("Arabisch") and hasClickAction()).assertIsDisplayed()
        }
        val teacher = runBlocking { app.languages.teachers().first() }.single()
        assertEquals("Amal", teacher.teacher.name)
        assertEquals(listOf("Arabisch"), teacher.languages.map { it.name })
    }

    @Test
    fun `a language's lessons can be shown for one teacher only, and its evaluation shows each teacher`() {
        runBlocking {
            val arabic = (app.languages.createLanguage("Arabisch") as Saving.Saved).id
            val amal = teacher("Amal", arabic)
            val bilal = teacher("Bilal", arabic)
            lesson(arabic, amal, "2026-10-20", 60)
            lesson(arabic, bilal, "2026-10-21", 30)
            lesson(arabic, amal, "2026-10-22", 45)
        }
        launchApp().use {
            openLanguages()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            heading("Arabisch").assertIsDisplayed()
            compose.onNodeWithText("22.10.2026 · 45 Min.").assertIsDisplayed()
            compose.onNodeWithText("21.10.2026 · 30 Min.").assertIsDisplayed()

            compose.onNode(hasText("Bilal") and isSelectable()).performClick()

            compose.onNodeWithText("21.10.2026 · 30 Min.").assertIsDisplayed()
            compose.onNodeWithText("22.10.2026 · 45 Min.").assertDoesNotExist()
            compose.onNodeWithText("20.10.2026 · 1 Std.").assertDoesNotExist()

            compose.onNode(hasText("Auswertung") and isSelectable()).performClick()
            compose.onNodeWithText("Dieser Monat: 1 Std. 45 Min. · 2 Sessions").assertIsDisplayed()
            compose.onNodeWithText("Dieser Monat: 30 Min. · 1 Session").assertIsDisplayed()
        }
    }

    @Test
    fun `the menu manages the languages and teachers, archiving rather than deleting what has lessons`() {
        runBlocking {
            val arabic = (app.languages.createLanguage("Arabisch") as Saving.Saved).id
            lesson(arabic, teacher("Amal", arabic), "2026-10-20", 60)
        }
        launchApp().use {
            openLanguages()
            compose.onNodeWithContentDescription("Menü der Sprachen").performClick()
            compose.onNodeWithText("Sprachen verwalten").performClick()
            compose.onNodeWithContentDescription("Sprache anlegen").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Italienisch")
            compose.onNode(hasText("Anlegen") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithContentDescription("Menü für Arabisch").performClick()
            compose.onNodeWithText("Löschen").assertDoesNotExist()
            compose.onNodeWithText("Archivieren").performClick()
            compose.onNodeWithText("Archiviert · Sessions, Themen oder Lehrer hängen daran: nur archivierbar.").assertIsDisplayed()
            // Archived, its lessons still open from here.
            compose.onNodeWithContentDescription("Menü für Arabisch").performClick()
            compose.onNodeWithText("Sessions ansehen").performClick()
            heading("Arabisch").assertIsDisplayed()
            compose.onNodeWithText("20.10.2026 · 1 Std.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithContentDescription("Menü der Sprachen").performClick()
            compose.onNodeWithText("Lehrer verwalten").performClick()
            compose.onNodeWithText("Amal").performClick()
            heading("Lehrer bearbeiten").assertIsDisplayed()
            compose.onNode(hasText("Italienisch") and isSelectable()).performClick()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Arabisch, Italienisch").assertIsDisplayed()
        }
        assertEquals(listOf("Italienisch"), runBlocking { app.languages.overview().first() }.cards.map { it.language.name })
    }

    @Test
    fun `a topic typed in a lesson lands on the language's list, where it is done by hand`() {
        runBlocking {
            val arabic = (app.languages.createLanguage("Arabisch") as Saving.Saved).id
            teacher("Amal", arabic)
        }
        launchApp().use {
            openLanguages()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
            compose.onNode(hasText("Amal") and isSelectable()).performScrollTo().performClick()
            compose.onNode(hasText("45 Min.") and isSelectable()).performClick()
            compose.onNode(hasSetTextAction() and hasText("Thema")).performScrollTo().performTextInput("Verben im Präsens")
            compose.onNode(hasText("Hinzufügen") and hasClickAction()).performClick()
            compose.onNode(hasText("Verben im Präsens") and isSelectable()).assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()

            heading("Arabisch").assertIsDisplayed()
            compose.onNode(hasText("Themen") and isSelectable()).performClick()
            compose.onNodeWithText("Verben im Präsens").assertIsDisplayed()
            compose.onNodeWithText("1 Session · zuletzt", substring = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Verben im Präsens abgehakt").performClick()
        }
        val topic = runBlocking { checkNotNull(app.languages.page(arabicId()).first()).topics.withoutExam.topics.single() }
        assertEquals("Verben im Präsens", topic.topic.name)
        assertTrue(topic.topic.done)
    }

    @Test
    fun `an exam is made with its date, shows on the card, and is taken with its result, which takes it off the card`() {
        runBlocking { app.languages.createLanguage("Arabisch") }
        launchApp().use {
            openLanguages()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            compose.onNode(hasText("Themen") and isSelectable()).performClick()
            compose.onNode(hasText("Neue Prüfung") and hasClickAction()).performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("A2")
            compose.onNode(hasText("Datum wählen") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("OK").performClick()
            compose.onNode(hasText("Speichern") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Nächste Prüfung: A2").assertIsDisplayed()
            compose.onAllNodesWithText("heute · 0 von 0 offen").assertCountEquals(2)
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Nächste Prüfung: A2 · heute · 0 von 0 offen").assertIsDisplayed()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            compose.onNode(hasText("Themen") and isSelectable()).performClick()

            compose.onNodeWithContentDescription("Menü für A2").performClick()
            compose.onNodeWithText("Als abgelegt markieren").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("bestanden")
            compose.onNode(hasText("Als abgelegt markieren") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Abgelegt · bestanden").assertIsDisplayed()
            compose.onNodeWithText("Nächste Prüfung: A2").assertDoesNotExist()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Nächste Prüfung", substring = true).assertDoesNotExist()
        }
        val exam = runBlocking { checkNotNull(app.languages.page(arabicId()).first()).topics.taken.single().exam?.exam }
        assertEquals("A2", exam?.name)
        assertEquals("bestanden", exam?.result)
    }

    @Test
    fun `a text file is imported into a lesson's note`() {
        runBlocking { teacher("Amal", (app.languages.createLanguage("Arabisch") as Saving.Saved).id) }
        val file = app.lessonDocuments.put("Stunde.txt", "Vokabeln: كتاب".toByteArray())
        launchApp().use { scenario ->
            openLanguages()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
            compose.onNode(hasText("Amal") and isSelectable()).performScrollTo().performClick()
            compose.onNode(hasText("45 Min.") and isSelectable()).performClick()
            compose.onNode(hasText("Textdatei") and hasClickAction()).performScrollTo().performClick()
            scenario.pickFile(Uri.parse(file))

            compose.onNodeWithText("Der Text steht jetzt am Ende der Notiz.").assertExists()
            compose.onNodeWithText("Speichern").performClick()
            heading("Arabisch").assertIsDisplayed()
        }
        val lesson = runBlocking { checkNotNull(app.languages.page(arabicId()).first()).lessons.single() }
        assertEquals("Vokabeln: كتاب", lesson.lesson.note)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a photo is attached to a lesson, shown small, and opened in full`() {
        runBlocking { teacher("Amal", (app.languages.createLanguage("Arabisch") as Saving.Saved).id) }
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }.compress(Bitmap.CompressFormat.PNG, 100, out)
        val file = app.lessonDocuments.put("Tafel.png", out.toByteArray())
        launchApp().use { scenario ->
            openLanguages()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
            compose.onNode(hasText("Amal") and isSelectable()).performScrollTo().performClick()
            compose.onNode(hasText("45 Min.") and isSelectable()).performClick()
            compose.onNode(hasText("Foto wählen") and hasClickAction()).performScrollTo().performClick()
            scenario.pickFile(Uri.parse(file))

            compose.onNodeWithContentDescription("Foto Tafel.jpg").performScrollTo().performClick()
            heading("Tafel.jpg").assertIsDisplayed()
            compose.onNodeWithText("Exportieren").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            heading("Neue Session").assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()
            heading("Arabisch").assertIsDisplayed()
        }
        val lesson = runBlocking { checkNotNull(app.languages.page(arabicId()).first()).lessons.single() }
        val attachments = runBlocking { app.languages.editing(lesson.lesson.id)?.original?.attachments.orEmpty() }
        assertEquals(listOf("Tafel.jpg"), attachments.map { it.name })
    }

    private fun arabicId() = runBlocking { app.languages.languages().first() }.single { it.language.name == "Arabisch" }.language.id

    private fun openLanguages() {
        tab("Sonstiges").performClick()
        compose.onNode(hasText("Sprachen") and hasClickAction()).performClick()
    }

    private suspend fun teacher(name: String, languageId: Long): Long =
        (app.languages.saveTeacher(TeacherEditing.New, TeacherDraft(name, languageIds = setOf(languageId))) as Saving.Saved).id

    private suspend fun lesson(languageId: Long, teacherId: Long, date: String, minutes: Int) =
        app.languages.save(LessonEditing.New, LessonDraft(LocalDate.parse(date), minutes.toString(), languageId, teacherId))

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun heading(text: String) = compose.onNode(isHeading() and hasText(text))
}
