package de.emilyshub

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.state.ToggleableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.languages.CardDraft
import de.emilyshub.languages.ExamDraft
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.Rating
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.speech.OfflineVoice
import de.emilyshub.wishes.WishDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    fun `a card not known when the practice was left is practised again from the tab Karten and the start page until known`() {
        val arabic = language("Arabisch")
        card(arabic, "كتاب", "Buch")
        card(arabic, "قلم", "Stift")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Nochmal").performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()
            // The one not known comes again; left before it is known.
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithText("Für heute geschafft").assertIsDisplayed()
            compose.onNodeWithText("Nicht gewusste üben (1)").performClick()
            compose.onNodeWithText("1 von 1").assertIsDisplayed()
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Nochmal").performClick()
            // Not known again, it comes again in the round.
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()

            compose.onNodeWithText("Nicht gewusste geübt: 1 Karte").assertIsDisplayed()
            compose.onNodeWithText("gewusst ·", substring = true).assertDoesNotExist()
            compose.onNode(hasText("Zurück") and hasClickAction()).performClick()
            compose.onNodeWithText("Für heute geschafft").assertIsDisplayed()
            compose.onNodeWithText("Nicht gewusste üben", substring = true).assertDoesNotExist()
        }
        assertEquals("the day's workload stays done", true, runBlocking { app.flashcards.workloads.of(arabic, LocalDate.now(app.clock)).first() }?.done)
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
    fun `a Vokabeldatei, JSON, is offered and taken over from either menu item, after a preview`() {
        val arabic = language("Arabisch")
        val json = """{"vokabeln": [{"ar_unvokalisiert": "قهوة", "ar_vokalisiert": "قَهْوَةٌ", "bedeutung": "der Kaffee", "kategorie": "Essen"}]}"""
        val file = app.lessonDocuments.put("vokabeln.json", json.toByteArray())

        launchApp().use { scenario ->
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Karten importieren").performClick()
            val request = scenario.pickFile(Uri.parse(file))
            assertTrue("JSON is offered", "application/json" in request.getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty())
            compose.onNodeWithText("1 neu · 0 geändert · 0 unverändert").assertIsDisplayed()
            compose.onNodeWithText("Abbrechen").performClick()

            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Vokabeldatei abgleichen").performClick()
            val again = scenario.pickFile(Uri.parse(file))
            assertTrue("JSON is offered", "application/json" in again.getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty())
            compose.onNodeWithText("Übernehmen").performClick()
            compose.waitForIdle()
            assertEquals("Angelegt: 1 · geändert: 0", ShadowToast.getTextOfLatestToast())
            compose.onNodeWithText("قَهْوَةٌ").assertIsDisplayed()
        }
        val card = runBlocking { app.testDatabase.cards().of(arabic) }.single()
        assertEquals("قهوة" to true, card.vocabKey to card.bothWays)
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
    fun `a card that hakt is marked on the tab, found by its filter, told in its editor and counted in the evaluation`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        val pen = (card(arabic, "qalam", "Stift") as Saving.Saved).id
        // Known at first, then four times not.
        runBlocking {
            app.flashcards.answer(pen, Rating.Good)
            repeat(4) { app.flashcards.answer(pen, Rating.Again) }
        }

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNodeWithText("· hakt", substring = true).assertIsDisplayed()
            compose.onNode(isSelectable() and hasText("Hakt")).performClick()
            compose.onNodeWithText("qalam").assertIsDisplayed()
            compose.onNodeWithText("kitab").assertDoesNotExist()

            compose.onNodeWithText("qalam").performClick()
            compose.onNodeWithText("Diese Karte hakt", substring = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            tab("Auswertung").performClick()
            compose.onNodeWithText("Karten: 2 · sicher 0 · heute fällig 1 · hakt 1").assertIsDisplayed()
        }
    }

    @Test
    fun `a card is read aloud with its language's voice only when asked, from its back only once uncovered`() {
        val arabic = language("Arabisch")
        app.speech.voices += OfflineVoice("ar-local", Locale("ar"))
        runBlocking {
            app.languages.setVoice(arabic, "ar-local")
            val door = (app.flashcards.create(arabic, CardDraft("bab", "Tür", bothWays = true)) as Saving.Saved).id
            app.flashcards.answer(door, Rating.Easy)
        }
        card(arabic, "kitab", "Buch")
        // The next day: its back new, and the other card.
        app.clock.now = app.clock.now.plus(1, ChronoUnit.DAYS)

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("Rückrichtung").assertIsDisplayed()
            // It would give away the answer.
            compose.onNodeWithContentDescription("Vorlesen").assertDoesNotExist()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithContentDescription("Vorlesen").performClick()
            assertEquals(listOf("ar-local" to "bab"), app.speech.spoken)

            compose.onNodeWithText("Gut").performClick()
            compose.onNodeWithText("kitab").assertIsDisplayed()
            assertEquals("nothing without being asked", 1, app.speech.spoken.size)
            compose.onNodeWithContentDescription("Vorlesen").performClick()
            assertEquals("ar-local" to "kitab", app.speech.spoken.last())

            compose.onNodeWithContentDescription("Karte ändern").performClick()
            compose.waitForIdle()
            assertTrue("left, the practice reads no more", app.speech.stopped > 0)
            compose.onNodeWithContentDescription("Vorlesen").performClick()
            assertEquals(3, app.speech.spoken.size)
        }
    }

    @Test
    fun `without a voice chosen, or one no longer on the phone, nothing is offered to read aloud`() {
        val arabic = language("Arabisch")
        card(arabic, "kitab", "Buch")
        app.speech.voices += OfflineVoice("ar-other", Locale("ar"))

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("kitab").assertIsDisplayed()
            compose.onNodeWithContentDescription("Vorlesen").assertDoesNotExist()
        }
        runBlocking { app.languages.setVoice(arabic, "ar-gone") }
        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("kitab").assertIsDisplayed()
            compose.onNodeWithContentDescription("Vorlesen").assertDoesNotExist()
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

    @Test
    fun `Heute practises the one language with cards open from its bar, back to Heute, and then offers it no more`() {
        launchApp().use {
            compose.onNodeWithContentDescription("Vokabeln üben").assertDoesNotExist()
        }
        val arabic = language("Arabisch")
        card(arabic, "كتاب", "Buch")

        launchApp().use {
            compose.onNodeWithContentDescription("Vokabeln üben").performClick()
            heading("Arabisch").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()
            compose.onNode(hasText("Zurück") and hasClickAction()).performClick()

            compose.onNodeWithText("‹ Gestern").assertIsDisplayed()
            compose.onNodeWithContentDescription("Vokabeln üben").assertDoesNotExist()
        }
    }

    @Test
    fun `with more languages open, Heute offers them the most open first, and back from one, or from the next, leads to Heute`() {
        val arabic = language("Arabisch")
        val persian = language("Persisch")
        val italian = language("Italienisch")
        card(arabic, "kitab", "Buch")
        card(persian, "ketab", "Buch")
        card(persian, "qalam", "Stift")
        card(italian, "libro", "Buch")

        launchApp().use { scenario ->
            compose.onNodeWithContentDescription("Vokabeln üben").performClick()
            assertEquals(
                listOf("Persisch (2)", "Arabisch (1)", "Italienisch (1)"),
                compose.onAllNodes(hasClickAction() and hasText(" (", substring = true)).fetchSemanticsNodes()
                    .map { it.config[SemanticsProperties.Text].joinToString() },
            )
            compose.onNodeWithText("Italienisch (1)").performClick()
            heading("Italienisch").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithText("‹ Gestern").assertIsDisplayed()
            // Done with one, "Weiter mit …" goes on with the next in its place; the back key leads to Heute from there too.
            compose.onNodeWithContentDescription("Vokabeln üben").performClick()
            compose.onNodeWithText("Arabisch (1)").performClick()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("Gut").performClick()
            compose.onNodeWithText("Weiter mit Persisch (2)").performClick()
            heading("Persisch").assertIsDisplayed()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("‹ Gestern").assertIsDisplayed()
            compose.onNodeWithContentDescription("Vokabeln üben").assertIsDisplayed()
        }
    }

    @Test
    fun `yesterday's view has no practising in its bar`() {
        card(language("Arabisch"), "kitab", "Buch")

        launchApp().use {
            compose.onNodeWithContentDescription("Vokabeln üben").assertIsDisplayed()
            compose.onNodeWithText("‹ Gestern").performClick()
            compose.onNodeWithContentDescription("Vokabeln üben").assertDoesNotExist()
        }
    }

    @Test
    fun `an exam's menu practises the cards of all its topics to the end, also once it is taken`() {
        val arabic = language("Arabisch")
        runBlocking {
            val exam = (app.languages.createExam(arabic, ExamDraft("A2", LocalDate.parse("2026-12-20"))) as Saving.Saved).id
            val colours = (app.languages.createTopic(arabic, "Farben", examId = exam) as Saving.Saved).id
            val food = (app.languages.createTopic(arabic, "Essen", examId = exam) as Saving.Saved).id
            app.languages.createTopic(arabic, "Leer", examId = exam)
            app.flashcards.create(arabic, CardDraft("ahmar", "rot", topicId = colours))
            app.flashcards.create(arabic, CardDraft("khubz", "Brot", topicId = food))
            app.languages.setExamTaken(exam, taken = true)
        }
        card(arabic, "kitab", "Buch")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Themen").performClick()
            compose.onNodeWithContentDescription("Menü für A2").performClick()
            compose.onNodeWithText("Prüfung üben").performClick()

            compose.onNodeWithText("1 von 2").assertIsDisplayed()
            repeat(2) {
                compose.onNodeWithText("kitab").assertDoesNotExist()
                compose.onNodeWithText("Aufdecken").performClick()
                compose.onNodeWithText("Gut").performClick()
            }
            compose.onNodeWithText("Prüfung geübt: 2 Karten").assertIsDisplayed()
            compose.onNodeWithText("Weiter mit", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `an archived language offers neither its topics nor its exams to practise`() {
        val arabic = language("Arabisch")
        runBlocking {
            val exam = (app.languages.createExam(arabic, ExamDraft("A2", LocalDate.parse("2026-12-20"))) as Saving.Saved).id
            val colours = (app.languages.createTopic(arabic, "Farben", examId = exam) as Saving.Saved).id
            app.flashcards.create(arabic, CardDraft("ahmar", "rot", topicId = colours))
            app.languages.setLanguageArchived(arabic, true)
        }

        // Each menu in an app of its own: one open covers the other.
        listOf("Menü für A2" to ("Prüfung ändern" to "Prüfung üben"), "Menü für Farben" to ("Umbenennen" to "Thema üben"))
            .forEach { (menu, entries) ->
                launchApp().use {
                    openArchivedTopics("Arabisch")
                    // Its cards are still counted.
                    compose.onNodeWithText("1 Karte · 0 sicher").assertIsDisplayed()
                    compose.onNodeWithContentDescription(menu).performClick()
                    compose.onNodeWithText(entries.first).assertExists()
                    compose.onNodeWithText(entries.second).assertDoesNotExist()
                }
            }
    }

    /** The tab Themen of the archived language [name], reached as it is: through "Sprachen verwalten". */
    private fun openArchivedTopics(name: String) {
        openLanguages()
        compose.onNodeWithContentDescription("Menü der Sprachen").performClick()
        compose.onNodeWithText("Sprachen verwalten").performClick()
        compose.onNodeWithContentDescription("Menü für $name").performClick()
        compose.onNodeWithText("Sessions ansehen").performClick()
        tab("Themen").performClick()
    }

    @Test
    fun `an exam whose topics have no cards offers no practising`() {
        val arabic = language("Arabisch")
        runBlocking {
            val exam = (app.languages.createExam(arabic, ExamDraft("A2", LocalDate.parse("2026-12-20"))) as Saving.Saved).id
            app.languages.createTopic(arabic, "Farben", examId = exam)
        }

        launchApp().use {
            openLanguage("Arabisch")
            tab("Themen").performClick()
            compose.onNodeWithContentDescription("Menü für A2").performClick()
            compose.onNodeWithText("Prüfung ändern").assertExists()
            compose.onNodeWithText("Prüfung üben").assertDoesNotExist()
        }
    }

    @Test
    fun `the evaluation tells how regularly the cards are practised, and what is due in the days to come`() {
        val arabic = language("Arabisch")
        val book = (card(arabic, "kitab", "Buch") as Saving.Saved).id
        card(arabic, "qalam", "Stift")
        // The first answer of a card is not counted as known or not: it was not learnt then.
        runBlocking {
            app.flashcards.answer(book, Rating.Again)
            app.flashcards.answer(book, Rating.Good)
        }
        val preview = checkNotNull(runBlocking { app.flashcards.summary(arabic, LocalDate.parse("2026-10-23")).first() }.preview)

        launchApp().use {
            openLanguage("Arabisch")
            tab("Auswertung").performClick()
            compose.onNodeWithText("Diesen Monat an 1 von 23 Tagen geübt · 2 Abfragen · 100 % gewusst").assertIsDisplayed()
            compose.onNodeWithText("Dieses Jahr an 1 Tag geübt · 2 Abfragen · 100 % gewusst").assertIsDisplayed()
            compose.onNodeWithText("Insgesamt an 1 Tag geübt · 2 Abfragen · 100 % gewusst").assertIsDisplayed()
            compose.onNodeWithText("Morgen fällig: ${preview.tomorrow} · nächste 7 Tage: ${preview.nextWeek}").assertIsDisplayed()
        }
    }

    @Test
    fun `without cards the evaluation tells nothing of practising`() {
        language("Arabisch")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Auswertung").performClick()
            compose.onNodeWithText("geübt", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Morgen fällig", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `typed, the answer is checked and an answer suggested, wrong struck through, and Weiss nicht is wrong`() {
        val arabic = language("Arabisch")
        card(arabic, "كتاب", "Buch, Heft")
        card(arabic, "قلم", "Stift")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Abfrage: aufdecken").performClick()
            compose.onNode(hasText("Eintippen") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Abfrage: eintippen").assertIsDisplayed()
            compose.onNodeWithText("Abfrage: eintippen").performClick()
            compose.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()

            // One meaning is enough, case does not count; Enter checks.
            compose.onNodeWithText("كتاب").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").assertDoesNotExist()
            field("Antwort").assertIsFocused()
            field("Antwort").performTextInput(" heft")
            field("Antwort").performImeAction()
            compose.onNodeWithText("Richtig").assertIsDisplayed()
            compose.onNodeWithText("Buch, Heft").assertIsDisplayed()
            suggested("Gut").assertIsDisplayed()
            compose.onNodeWithText("Gut").performClick()

            // Wrong: struck through, the answer under it, Nochmal suggested; all four answers still there.
            compose.onNodeWithText("قلم").assertIsDisplayed()
            field("Antwort").performTextInput("Heft")
            compose.onNodeWithText("Prüfen").performClick()
            compose.onNode(hasText("Heft") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "falsch")).assertIsDisplayed()
            compose.onNodeWithText("Stift").assertIsDisplayed()
            suggested("Nochmal").assertIsDisplayed()
            listOf("Schwer", "Gut", "Leicht").forEach { compose.onNode(hasText(it) and hasClickAction()).assertIsDisplayed() }
            compose.onNodeWithText("Nochmal").performClick()

            // Again in the round: not known, it uncovers as wrong, nothing struck through.
            compose.onNodeWithText("قلم").assertIsDisplayed()
            field("Antwort").assertTextEquals("Antwort", "")
            compose.onNodeWithText("Weiß nicht").performClick()
            compose.onNodeWithText("Stift").assertIsDisplayed()
            compose.onNodeWithText("Richtig", substring = true).assertDoesNotExist()
            suggested("Nochmal").assertIsDisplayed()
            compose.onNodeWithText("Gut").performClick()

            compose.onNodeWithText("Für heute geschafft: 2 Karten").assertIsDisplayed()
        }
        assertEquals(true, runBlocking { app.flashcards.answering(arabic).first() }.typed)
    }

    @Test
    fun `typed from its back, the side in the language is typed, and right but for the vowel marks says so with both`() {
        val arabic = language("Arabisch")
        runBlocking {
            app.flashcards.setTypedAnswers(arabic, typed = true)
            val write = (app.flashcards.create(arabic, CardDraft("كَتَبَ", "schreiben", bothWays = true)) as Saving.Saved).id
            app.flashcards.answer(write, Rating.Easy)
        }
        // The next day, its front is not due, its back new.
        app.clock.now = app.clock.now.plus(1, ChronoUnit.DAYS)

        launchApp().use {
            openLanguages()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("Rückrichtung").assertIsDisplayed()
            compose.onNodeWithText("schreiben").assertIsDisplayed()
            field("Antwort").performTextInput("كتب")
            compose.onNodeWithText("Prüfen").performClick()

            compose.onNodeWithText("Richtig, bis auf die Vokalzeichen").assertIsDisplayed()
            compose.onNodeWithText("Eingetippt: كتب").assertIsDisplayed()
            compose.onNodeWithText("كَتَبَ").assertIsDisplayed()
            suggested("Gut").assertIsDisplayed()
        }
        // The keyboard is asked for the language, as named in German.
        assertEquals(Locale.forLanguageTag("ar"), runBlocking { app.flashcards.answering(arabic).first() }.locale)
    }

    @Test
    fun `with the vowel marks left out the question has none, and uncovered it has them`() {
        val arabic = language("Arabisch")
        card(arabic, "قَلَم", "Stift")

        launchApp().use {
            openLanguage("Arabisch")
            tab("Karten").performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Vokalzeichen in der Frage ausblenden").performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNode(hasText("Vokalzeichen in der Frage ausblenden") and SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
                .assertIsDisplayed()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()

            compose.onNodeWithText("قلم").assertIsDisplayed()
            compose.onNodeWithText("قَلَم").assertDoesNotExist()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("قَلَم").assertIsDisplayed()
            compose.onNodeWithText("Stift").assertIsDisplayed()
        }
        assertEquals(true, runBlocking { app.flashcards.answering(arabic).first() }.hideVowelMarks)
        assertEquals("nothing is kept on the card", "قَلَم", cardOf(arabic, "قَلَم").front)
    }

    @Test
    fun `with the vowel marks left out, asked from its back nothing changes`() {
        val arabic = language("Arabisch")
        runBlocking {
            app.flashcards.setHideVowelMarks(arabic, hide = true)
            val write = (app.flashcards.create(arabic, CardDraft("كَتَبَ", "schreiben", bothWays = true)) as Saving.Saved).id
            app.flashcards.answer(write, Rating.Easy)
        }
        // The next day, its front is not due, its back new.
        app.clock.now = app.clock.now.plus(1, ChronoUnit.DAYS)

        launchApp().use {
            openLanguages()
            compose.onNode(hasText("Üben") and hasClickAction()).performClick()
            compose.onNodeWithText("Rückrichtung").assertIsDisplayed()
            compose.onNodeWithText("schreiben").assertIsDisplayed()
            compose.onNodeWithText("Aufdecken").performClick()
            compose.onNodeWithText("كَتَبَ").assertIsDisplayed()
        }
    }

    @Test
    fun `without vowel marks on any card the menu does not offer to leave them out`() {
        card(language("Italienisch"), "libro", "Buch")

        launchApp().use {
            openLanguage("Italienisch")
            tab("Karten").performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Abfrage: aufdecken").assertIsDisplayed()
            compose.onNodeWithText("Vokalzeichen in der Frage ausblenden").assertDoesNotExist()
        }
    }

    @Test
    fun `a language's limit is set in the tab's menu, and the start page tells what waits beyond it`() {
        val arabic = language("Arabisch")
        dueCards(arabic, 25)

        launchApp().use {
            openLanguages()
            compose.onNodeWithText("Heute: 25 fällig").assertIsDisplayed()
            compose.onNode(hasText("Arabisch") and isHeading()).performClick()
            tab("Karten").performClick()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Höchstens Abfragen am Tag: ohne Grenze").performClick()
            compose.onNode(hasText("Ohne Grenze") and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("Speichern") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Heute: 20 fällig · 5 warten").assertIsDisplayed()
            compose.onNodeWithContentDescription("Mehr zu den Karten").performClick()
            compose.onNodeWithText("Höchstens Abfragen am Tag: 20").assertIsDisplayed()
        }
        assertEquals(20, runBlocking { app.flashcards.tab(arabic).first() }?.dailyLimit)
    }

    @Test
    fun `the overall limit is set in the start page's menu, and its line tells what waits once the day's are done`() {
        val arabic = language("Arabisch")
        dueCards(arabic, 25)
        runBlocking { app.flashcards.setOverallLimit(20) }
        // The day's twenty done.
        runBlocking {
            repeat(20) {
                val next = checkNotNull(app.flashcards.workloads.of(arabic, LocalDate.now(app.clock)).first()).remaining.first()
                app.flashcards.answer(next.id, Rating.Good)
            }
        }

        launchApp().use {
            openLanguages()
            compose.onNodeWithText("Für heute geschafft · 5 warten").assertIsDisplayed()
            compose.onNodeWithContentDescription("Menü der Sprachen").performClick()
            compose.onNodeWithText("Höchstens Abfragen am Tag, alle Sprachen: 20").performClick()
            compose.onNode(hasText("Ohne Grenze") and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("Speichern") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Heute: 5 fällig").assertIsDisplayed()
        }
        assertEquals(null, runBlocking { app.flashcards.overallLimit().first() })
    }

    @Test
    fun `a workload done at its limit does its routine as the app opens, also one made after the answers`() {
        val italian = language("Italienisch")
        val arabic = language("Arabisch")
        dueCards(arabic, 30)
        dueCards(italian, 10)
        runBlocking {
            app.flashcards.setOverallLimit(20)
            // Italian's share is 5: practised to it, with more waiting.
            repeat(5) {
                val next = checkNotNull(app.flashcards.workloads.of(italian, LocalDate.now(app.clock)).first()).remaining.first()
                app.flashcards.answer(next.id, Rating.Good)
            }
        }
        // Made afterwards, no answer did it.
        app.routine("Vokabeln", "18:00")
        runBlocking {
            val routine = app.testDatabase.routines().withStatus(RoutineStatus.Active).single()
            app.testDatabase.routines().update(routine.copy(vocabularyLanguageId = italian))
        }

        fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first() }.map { it.status }
        assertEquals(listOf(OccurrenceStatus.Open), statuses())

        launchApp().use {
            compose.waitUntil(timeoutMillis = 5_000) { statuses() == listOf(OccurrenceStatus.Done) }
        }
        assertEquals(listOf(OccurrenceStatus.Done), statuses())
    }

    /** [count] cards of [languageId], answered some days ago and due today. */
    private fun dueCards(languageId: Long, count: Int) {
        val start = app.clock.now
        app.clock.now = start.minus(10, ChronoUnit.DAYS)
        runBlocking {
            repeat(count) {
                val id = (app.flashcards.create(languageId, CardDraft("k$it", "b$it")) as Saving.Saved).id
                app.flashcards.answer(id, Rating.Good)
            }
        }
        app.clock.now = start
    }

    /** The answer [label], set apart as the one an answer typed suggests. */
    private fun suggested(label: String) = compose.onNode(
        hasText(label) and hasClickAction() and SemanticsMatcher("suggested") { node ->
            node.config.getOrElseNullable(SemanticsProperties.StateDescription) { null }?.endsWith(", vorgeschlagen") == true
        },
    )

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
