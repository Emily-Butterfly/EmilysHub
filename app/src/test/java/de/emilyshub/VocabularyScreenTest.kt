package de.emilyshub

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.wishes.WishDraft
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

    @Test
    fun `a routine is done by the cards of a language chosen in its editor, under Mehr Optionen`() {
        val arabic = language("Arabisch")

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Vokabeln")
            compose.openMoreOptions()
            compose.onNodeWithText("Erledigt durch Vokabeln").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()

            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(listOf(arabic), runBlocking { app.testDatabase.routines().withStatus(RoutineStatus.Active) }.map { it.vocabularyLanguageId })
    }

    @Test
    fun `on Heute such a routine says by which cards, and its menu practises them, which does it`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        app.routine("Vokabeln", "18:00")
        runBlocking {
            val routine = app.testDatabase.routines().withStatus(RoutineStatus.Active).single()
            app.testDatabase.routines().update(routine.copy(vocabularyLanguageId = arabic))
        }

        launchApp().use {
            compose.onNodeWithText("durch Vokabeln: Arabisch").assertIsDisplayed()
            compose.onNodeWithContentDescription("Mehr zu „Vokabeln“", substring = true).performClick()
            compose.onNodeWithText("Üben").performClick()
            compose.onNodeWithText("1 von 1").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()
            compose.onNodeWithText("Für heute geschafft: 1 Karte").assertIsDisplayed()
        }
        assertEquals(
            listOf(OccurrenceStatus.Done),
            runBlocking { app.routines.dueOn(app.routines.today()).first() }.map { it.status },
        )
    }

    @Test
    fun `a goal's Karten-Ziel has a line of its own in the tree, which opens the cards of its language`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        runBlocking { app.wishes.create(WishDraft("Wortschatz", cardLanguageId = arabic, cardCount = "1000")) }

        launchApp().use {
            compose.openGoals()
            compose.onNodeWithContentDescription("„Wortschatz“ aufklappen").performClick()
            compose.onNodeWithText("Karten: Arabisch · 0 von 1\u202F000").performClick()

            heading("Arabisch").assertIsDisplayed()
            tab("Karten").assertIsSelected()
            compose.onNodeWithText("kitab").assertIsDisplayed()
        }
    }

    @Test
    fun `a topic counts its cards, which open filtered on it, and its menu practises it whole`() {
        val arabic = language("Arabisch")
        val colours = runBlocking { (app.languages.createTopic(arabic, "Farben") as Saving.Saved).id }
        runBlocking {
            app.flashcards.create(arabic, CardDraft("ahmar", "rot", topicId = colours))
            app.flashcards.create(arabic, CardDraft("azraq", "blau", topicId = colours))
        }
        card(arabic, "kitab", "Buch")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Themen").performClick()
            compose.onNodeWithText("2 Karten · 0 sicher").performClick()

            tab("Karten").assertIsSelected()
            compose.onNodeWithText("ahmar").assertIsDisplayed()
            compose.onNodeWithText("kitab").assertDoesNotExist()

            tab("Themen").performClick()
            compose.onNodeWithContentDescription("Menü für Farben").performClick()
            compose.onNodeWithText("Thema üben").performClick()

            compose.onNodeWithText("1 von 2").assertIsDisplayed()
            compose.onNodeWithText("ahmar").assertIsDisplayed()
            repeat(2) {
                compose.onNodeWithText("Aufdecken").performClick()
                compose.onNodeWithText("Gut").performClick()
            }
            compose.onNodeWithText("Thema geübt: 2 Karten").assertIsDisplayed()
        }
    }

    @Test
    fun `the search over all areas finds a card, and its hit opens it`() {
        val arabic = language("Arabisch")
        card(arabic, "كِتَاب", "Buch")

        launchApp().use {
            compose.onNodeWithContentDescription("Suchen").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("كتاب")
            // The search waits for a pause in typing.
            compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("كِتَاب – Buch · Arabisch").fetchSemanticsNodes().isNotEmpty() }

            compose.onNode(isHeading() and hasText("Sprachen · Karten")).assertIsDisplayed()
            compose.onNodeWithText("كِتَاب – Buch · Arabisch").performClick()

            heading("Karte ändern").assertIsDisplayed()
        }
    }

    @Test
    fun `the tab Karten searches its cards, and the evaluation names them in a line`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        card(arabic, "qalam", "Stift")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            field("Karten durchsuchen").performTextInput("stift")
            compose.onNodeWithText("qalam").assertIsDisplayed()
            compose.onNodeWithText("kitab").assertDoesNotExist()

            tab("Auswertung").performClick()
            compose.onNodeWithText("Karten: 2 · sicher 0 · heute fällig 2").assertIsDisplayed()
        }
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
