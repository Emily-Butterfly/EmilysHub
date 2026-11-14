package de.emilyshub

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import de.emilyshub.languages.CardDraft
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate

/** The Vokabeltrainer (SPEC.md §15; #104): a card made on the tab Karten, the count on the start page, and practising. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
class VocabularyScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a card is made on the tab Karten, and a front made already is refused`() {
        val arabic = language("Arabisch")
        card(arabic, "كتاب", "Buch")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("neu", substring = true).assertIsDisplayed()

            compose.onNodeWithContentDescription("Karte anlegen").performClick()
            heading("Neue Karte").assertIsDisplayed()
            field("Vorderseite").performTextInput("كتاب")
            field("Rückseite").performTextInput("Buch")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Diese Karte gibt es schon.").assertIsDisplayed()

            field("Vorderseite").performTextInput("قلم")
            field("Rückseite").performTextInput(" und Stift")
            compose.onNodeWithText("Speichern").performClick()

            heading("Arabisch").assertIsDisplayed()
            compose.onNodeWithText("كتابقلم").assertIsDisplayed()
        }
        assertEquals(listOf("Buch", "Buch und Stift"), runBlocking { app.flashcards.tab(arabic).first() }?.cards?.map { it.card.back }?.sorted())
    }

    @Test
    fun `the start page counts the cards due today, and practising them through says it is done`() {
        val arabic = language("Arabisch")
        card(arabic, "كتاب", "Buch")
        card(arabic, "قلم", "Stift")

        launchApp().use {
            openLanguages()
            compose.onNodeWithText("Heute fällig: 2").assertIsDisplayed()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()

            compose.onNodeWithText("1 von 2").assertIsDisplayed()
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Buch").assertDoesNotExist()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Buch").assertIsDisplayed()
            // Nochmal and Schwer a day, Gut two, Leicht eight.
            compose.onAllNodesWithText("1 T").assertCountEquals(2)
            listOf("2 T", "8 T").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            compose.onNodeWithText("„Schwer“ heißt gewusst", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Nochmal").performClick()

            compose.onNodeWithText("2 von 2").assertIsDisplayed()
            compose.onNodeWithText("قلم").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()

            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()

            compose.onNodeWithText("Für heute geschafft: 2 Karten").assertIsDisplayed()
            compose.onNodeWithText("Zurück").performClick()
            compose.onNodeWithText("Für heute geschafft").assertIsDisplayed()
        }
    }

    @Test
    fun `cards are made from the lines of a session's note, those there already not ticked, with the session's topic`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        val amal = runBlocking { (app.languages.saveTeacher(TeacherEditing.New, TeacherDraft("Amal", languageIds = setOf(arabic))) as Saving.Saved).id }
        val note = "## Wortschatz\n- **kitab** – Buch\n- qalam = Stift\nbab\tTür\nHeute viel geübt."
        runBlocking {
            app.languages.save(
                LessonEditing.New,
                LessonDraft(LocalDate.parse("2026-10-22"), "45", arabic, amal, note = note, newTopics = listOf("Schule")),
            )
        }

        launchApp().use {
            openLanguage("Arabisch")
            compose.onNodeWithText("Amal").performClick()
            compose.onNodeWithText("Karten aus der Notiz").performScrollTo().performClick()

            compose.onNodeWithText("gibt es schon").assertIsDisplayed()
            compose.onNodeWithText("2 Karten anlegen").assertIsDisplayed()
            compose.onNodeWithText("Seiten tauschen").performClick()
            compose.onNodeWithText("Stift").assertIsDisplayed()
            compose.onNodeWithText("Seiten tauschen").performClick()
            compose.onNodeWithText("2 Karten anlegen").performClick()
            compose.waitForIdle()
            assertEquals("2 Karten angelegt.", ShadowToast.getTextOfLatestToast())
        }
        val tab = checkNotNull(runBlocking { app.flashcards.tab(arabic).first() })
        assertEquals(listOf("bab", "kitab", "qalam"), tab.cards.map { it.card.front })
        assertEquals(listOf("Schule", null, "Schule"), tab.cards.map { it.topic?.name })
        assertEquals("the note stays as it is", note, runBlocking { app.testDatabase.languages().observeLessons().first() }.single().note)
    }

    @Test
    fun `a file of cards is imported after a preview, its unreadable lines named`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        val file = app.lessonDocuments.put("vokabeln.txt", "kitab;Buch\nqalam;Stift;qalam\nnur eins\nbab\tTür\tbāb\toffen".toByteArray())

        launchApp().use { scenario ->
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Karten importieren").performClick()
            val request = scenario.pickFile(Uri.parse(file))
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)

            compose.onNodeWithText("3 Karten, 1 schon vorhanden, 1 Zeile nicht lesbar (Zeile 3)").assertIsDisplayed()
            compose.onNodeWithText("2 Karten anlegen").performClick()
            compose.waitForIdle()
            assertEquals("2 Karten angelegt, 1 schon vorhanden und übersprungen.", ShadowToast.getTextOfLatestToast())
            compose.onNodeWithText("qalam").assertIsDisplayed()
        }
        val bab = runBlocking { app.flashcards.tab(arabic).first() }?.cards?.single { it.card.front == "bab" }?.card
        assertEquals(listOf("Tür", "bāb", "offen"), listOf(bab?.back, bab?.transcription, bab?.note))
    }

    private fun language(name: String) = runBlocking { (app.languages.createLanguage(name) as Saving.Saved).id }

    private fun card(languageId: Long, front: String, back: String) = runBlocking { app.flashcards.create(languageId, CardDraft(front, back)) }

    private fun openLanguages() {
        tab("Sonstiges").performClick()
        compose.onNode(hasText("Sprachen") and hasClickAction()).performClick()
    }

    private fun openLanguage(name: String) {
        openLanguages()
        compose.onNode(hasText(name) and isHeading()).performClick()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun heading(text: String) = compose.onNode(isHeading() and hasText(text))
}
