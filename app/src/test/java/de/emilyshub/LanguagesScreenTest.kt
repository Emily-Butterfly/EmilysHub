package de.emilyshub

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
            compose.onNodeWithText("Archiviert · Hat Sessions: nur archivierbar.").assertIsDisplayed()

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
