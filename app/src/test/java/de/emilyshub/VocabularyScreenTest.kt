package de.emilyshub

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.languages.CardDraft
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.Rating
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
import java.time.temporal.ChronoUnit

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
            compose.onNodeWithText("neu").assertIsDisplayed()
            compose.onNodeWithText("Heute: 1 neu").assertIsDisplayed()

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
    fun `the start page counts what is due and what is new apart, either alone without the other`() {
        val arabic = language("Arabisch")
        val persian = language("Persisch")
        val turkish = language("Türkisch")
        // Answered "Schwer" today, each is due tomorrow.
        listOf(arabic to "kitab", persian to "ketab").forEach { (language, front) ->
            runBlocking { app.flashcards.answer((card(language, front, "Buch") as Saving.Saved).id, Rating.Hard) }
        }
        card(arabic, "qalam", "Stift")
        card(turkish, "kitap", "Buch")
        app.clock.now = app.clock.now.plus(1, ChronoUnit.DAYS)

        launchApp().use {
            openLanguages()
            compose.onNodeWithText("Heute: 1 fällig · 1 neu").assertExists()
            compose.onNodeWithText("Heute: 1 fällig").assertExists()
            compose.onNodeWithText("Heute: 1 neu").assertExists()
        }
    }

    @Test
    fun `the last answer is taken back, the card shown again covered where it was in the round`() {
        val arabic = language("Arabisch")
        card(arabic, "كتاب", "Buch")
        card(arabic, "قلم", "Stift")

        launchApp().use {
            openLanguages()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            val takeBack = compose.onNodeWithContentDescription("Letzte Antwort zurücknehmen")
            takeBack.assertIsNotEnabled()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Leicht").performClick()
            compose.onNodeWithText("2 von 2").assertIsDisplayed()

            takeBack.performClick()

            compose.onNodeWithText("1 von 2").assertIsDisplayed()
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Buch").assertDoesNotExist()
            compose.onNodeWithText("Aufdecken").assertIsDisplayed()
            takeBack.assertIsNotEnabled()

            // Nochmal twice: taken back, the card is in the round again as before, covered.
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Nochmal").performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Nochmal").performClick()
            compose.onNodeWithText("Aufdecken").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            takeBack.performClick()
            compose.onNodeWithText("2 von 2").assertIsDisplayed()
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Buch").assertDoesNotExist()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()
            compose.onNodeWithText("Für heute geschafft: 2 Karten").assertIsDisplayed()
        }
        val book = cardOf(arabic, "كتاب")
        assertEquals("Nochmal, then Gut", 2, runBlocking { app.testDatabase.cards().reviewsOf(book.id) }.size)
    }

    @Test
    fun `the start page counts the cards due today, and practising them through says it is done`() {
        val arabic = language("Arabisch")
        card(arabic, "كتاب", "Buch")
        card(arabic, "قلم", "Stift")

        launchApp().use {
            openLanguages()
            compose.onNodeWithText("Heute: 2 neu").assertIsDisplayed()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()

            compose.onNodeWithText("1 von 2").assertIsDisplayed()
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Buch").assertDoesNotExist()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Buch").assertIsDisplayed()
            // Nochmal and Schwer a day, Gut two, Leicht eight, said with each answer.
            mapOf("Nochmal" to "in einem Tag", "Schwer" to "in einem Tag", "Gut" to "in 2 Tagen", "Leicht" to "in 8 Tagen").forEach { (answer, interval) ->
                compose.onNode(hasText(answer) and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, interval)).assertIsDisplayed()
            }
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
            // The first answer of each counts: one not known at first, though known when it came again.
            compose.onNodeWithText("1 gewusst · 1 Nochmal").assertIsDisplayed()
            compose.onNodeWithText("Weiter mit", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Zurück").performClick()
            compose.onNodeWithText("Für heute geschafft").assertIsDisplayed()
        }
    }

    @Test
    fun `the day's workload done, the other language with the most open is practised next in its place`() {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val persian = language("Persisch")
        card(arabic, "kitab", "Buch")
        card(italian, "libro", "Buch")
        card(italian, "penna", "Stift")
        card(persian, "ketab", "Buch")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()
            compose.onNodeWithText("1 gewusst · 0 Nochmal").assertIsDisplayed()

            compose.onNodeWithText("Weiter mit Italienisch (2)").performClick()
            heading("Italienisch").assertIsDisplayed()
            compose.onNodeWithText("1 von 2").assertIsDisplayed()
            // Its own practice: the last answer in the other language is not to take back here.
            compose.onNodeWithContentDescription("Letzte Antwort zurücknehmen").assertIsNotEnabled()
            repeat(2) {
                compose.onNodeWithText("Aufdecken").performClick()
                compose.onNodeWithText("Gut").performClick()
            }
            compose.onNodeWithText("Weiter mit Persisch (1)").performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()

            compose.onNodeWithText("Für heute geschafft: 1 Karte").assertIsDisplayed()
            compose.onNodeWithText("Weiter mit", substring = true).assertDoesNotExist()
            // In place of the one done: back is the language it started from.
            compose.onNodeWithText("Zurück").performClick()
            heading("Arabisch").assertIsDisplayed()
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
    fun `a card is changed from the practice, which goes on where it was, and deleted it goes on with the next`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        card(arabic, "qalam", "Stift")
        card(arabic, "bab", "Tür")

        launchApp().use {
            openLanguages()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Leicht").performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            val before = cardOf(arabic, "qalam")

            compose.onNodeWithContentDescription("Karte ändern").performClick()
            heading("Karte ändern").assertIsDisplayed()
            field("Rückseite").performTextReplacement("Füller")
            compose.onNodeWithText("Speichern").performClick()

            // Where it was, uncovered as it was, with the text changed.
            compose.onNodeWithText("2 von 3").assertIsDisplayed()
            compose.onNodeWithText("qalam").assertIsDisplayed()
            compose.onNodeWithText("Füller").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").assertDoesNotExist()
            assertEquals("its planning kept", before.copy(back = "Füller"), cardOf(arabic, "qalam"))

            compose.onNodeWithContentDescription("Karte ändern").performClick()
            compose.onNodeWithContentDescription("Karte löschen").performClick()
            compose.onNode(hasText("Löschen") and hasClickAction()).performClick()

            compose.onNodeWithText("2 von 2").assertIsDisplayed()
            compose.onNodeWithText("bab").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").assertIsDisplayed()
        }
    }

    @Test
    fun `a topic practised asking a card from its back goes on with the next once that way is ticked off`() {
        val arabic = language("Arabisch")
        val colours = runBlocking { (app.languages.createTopic(arabic, "Farben") as Saving.Saved).id }
        runBlocking {
            val red = (app.flashcards.create(arabic, CardDraft("ahmar", "rot", topicId = colours, bothWays = true)) as Saving.Saved).id
            app.flashcards.answer(red, Rating.Easy)
            app.flashcards.create(arabic, CardDraft("azraq", "blau", topicId = colours))
        }
        // The next day, its front is not due, its back new.
        app.clock.now = app.clock.now.plus(1, ChronoUnit.DAYS)

        launchApp().use {
            openLanguage("Arabisch")
            tab("Themen").performClick()
            compose.onNodeWithContentDescription("Menü für Farben").performClick()
            compose.onNodeWithText("Thema üben").performClick()
            compose.onNodeWithText("Rückrichtung").assertIsDisplayed()
            compose.onNodeWithText("rot").assertIsDisplayed()

            compose.onNodeWithContentDescription("Karte ändern").performClick()
            // Never asked from its back yet, it is ticked off without asking.
            compose.onNodeWithText("In beide Richtungen abfragen").performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("1 von 2").assertIsDisplayed()
            compose.onNodeWithText("azraq").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").assertIsDisplayed()
            compose.onNodeWithText("Rückrichtung").assertDoesNotExist()
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
        card(language("Italienisch"), "libro", "Buch")

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
            compose.onNodeWithText("2 gewusst · 0 Nochmal").assertIsDisplayed()
            // A topic practised offers no other language.
            compose.onNodeWithText("Weiter mit", substring = true).assertDoesNotExist()
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

    private fun cardOf(languageId: Long, front: String) =
        runBlocking { app.flashcards.tab(languageId).first() }!!.cards.single { it.card.front == front }.card

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
