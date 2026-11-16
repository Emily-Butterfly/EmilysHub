package de.emilyshub.ui.languages

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.Day
import de.emilyshub.TestClock
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.languages.Card
import de.emilyshub.languages.CardDraft
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Language
import de.emilyshub.languages.Prompt
import de.emilyshub.languages.Rating
import de.emilyshub.languages.Topic
import de.emilyshub.speech.FakeSpeech
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A card in practice (SPEC.md §15): the four answers fit, written out, on a narrow phone, and say their interval to
 * TalkBack; a tap on the card uncovers it. Its editor tells a front taken while it is typed, and makes one card after
 * another; the tab Karten has "Üben".
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h640dp")
class CardFaceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `the four answers are written out whole on a narrow phone`() {
        val card = Card(id = 1, languageId = 1, front = "Wort", back = "Übersetzung")
        val intervals = Rating.entries.associateWith { 1 }
        // As the practice lays it out: the answers 16 dp from each side of the screen.
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            CardFace(Prompt(card), intervals, hint = false, enabled = true, onAnswer = {})
        }
        compose.onNodeWithText("Aufdecken").performClick()
        listOf("Nochmal", "Schwer", "Gut", "Leicht").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertFalse("$label is cut off", layouts.single().hasVisualOverflow)
        }
    }

    @Test
    fun `a tap on the card uncovers it, and each answer says its interval with it`() {
        val card = Card(id = 1, languageId = 1, front = "kitab", back = "Buch")
        val intervals = mapOf(Rating.Again to 1, Rating.Hard to 3, Rating.Good to 46, Rating.Easy to 400)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            CardFace(Prompt(card), intervals, hint = false, enabled = true, onAnswer = {})
        }
        compose.onNodeWithText("kitab").performClick()

        compose.onNodeWithText("Buch").assertExists()
        // Said with the answer, so not once more on its own.
        compose.onNodeWithText("1,5 Mon.", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("in 1,5 Monaten", compose.onNodeWithText("Gut").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals("in einem Tag", compose.onNodeWithText("Nochmal").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals("in 1,1 Jahren", compose.onNodeWithText("Leicht").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test
    fun `the editor tells a front taken while it is typed`() {
        val database = inMemoryDatabase()
        try {
            val (flashcards, languageId) = flashcards(database)
            runBlocking { flashcards.create(languageId, CardDraft("kitab", "Buch")) }
            Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
                CardEditorScreen(flashcards, FakeSpeech(), CardEditorTarget.New(languageId), onClose = {})
            }
            compose.onNodeWithText("Vorderseite").performTextInput("Kitab")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            compose.waitForIdle()

            compose.onNodeWithText("Diese Karte gibt es schon.", useUnmergedTree = true).assertExists()
        } finally {
            database.close()
        }
    }

    @Test
    fun `Speichern und neue makes the card and leaves the editor open for the next with its topic`() {
        val database = inMemoryDatabase()
        try {
            val (flashcards, languageId) = flashcards(database)
            val colours = runBlocking { database.languages().insertTopic(Topic(languageId = languageId, name = "Farben")) }
            var closed = false
            Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
                CardEditorScreen(flashcards, FakeSpeech(), CardEditorTarget.New(languageId), onClose = { closed = true })
            }
            compose.onNodeWithText("Vorderseite").performTextInput("ahmar")
            compose.onNodeWithText("Rückseite").performTextInput("rot")
            compose.onNodeWithText("Farben").performScrollTo().performClick()
            compose.onNodeWithText("Speichern und neue").performClick()
            compose.waitForIdle()

            assertFalse(closed)
            compose.onNodeWithText("„ahmar“ angelegt").assertExists()
            compose.onNodeWithText("ahmar").assertDoesNotExist()
            compose.onNodeWithText("Vorderseite").performTextInput("azraq")
            compose.onNodeWithText("Rückseite").performTextInput("blau")
            compose.onNodeWithText("Speichern").performClick()
            compose.waitForIdle()

            assertTrue(closed)
            val saved = runBlocking { database.cards().of(languageId) }
            assertEquals(listOf("ahmar", "azraq"), saved.map { it.front }.sorted())
            assertEquals(listOf(colours, colours), saved.map { it.topicId })
        } finally {
            database.close()
        }
    }

    @Test
    fun `the tab Karten has Ueben while cards are due`() {
        val database = inMemoryDatabase()
        try {
            val (flashcards, languageId) = flashcards(database)
            runBlocking { flashcards.create(languageId, CardDraft("kitab", "Buch")) }
            var practised = false
            Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
                CardsTabContent(flashcards, Day(clock), languageId, null, {}, {}, onPractice = { practised = true })
            }
            compose.onNodeWithText("Heute: 1 neu").assertExists()
            compose.onNodeWithText("Üben").performClick()

            assertTrue(practised)
        } finally {
            database.close()
        }
    }

    private val clock = TestClock(Instant.parse("2026-11-16T09:00:00Z"), ZoneId.of("Europe/Berlin"))

    /** The Vokabeltrainer on [database], with a language "Arabisch" made in it. */
    private fun flashcards(database: HubDatabase): Pair<Flashcards, Long> {
        val languageId = runBlocking { database.languages().insertLanguage(Language(name = "Arabisch")) }
        return Flashcards({ database }, clock, FakeLessonDocuments(), Dispatchers.Unconfined) to languageId
    }

    @Test
    fun `asked from its back, the card shows its back with Rueckrichtung, and revealed its front`() {
        val card = Card(id = 1, languageId = 1, front = "kitab", back = "Buch", transcription = "kitāb", bothWays = true)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            Column { CardFace(Prompt(card, reverse = true), Rating.entries.associateWith { 1 }, hint = false, enabled = true, onAnswer = {}) }
        }
        compose.onNodeWithText("Rückrichtung").assertExists()
        compose.onNodeWithText("Buch").assertExists()
        compose.onNodeWithText("kitab").assertDoesNotExist()
        compose.onNodeWithText("Aufdecken").performClick()
        compose.onNodeWithText("kitab").assertExists()
        compose.onNodeWithText("kitāb").assertExists()
    }

    @Test
    fun `the editor ticks a new card to be asked both ways`() {
        val database = inMemoryDatabase()
        try {
            val clock = TestClock(Instant.parse("2026-11-16T09:00:00Z"), ZoneId.of("Europe/Berlin"))
            val languageId = runBlocking { database.languages().insertLanguage(Language(name = "Arabisch")) }
            val flashcards = Flashcards({ database }, clock, FakeLessonDocuments(), Dispatchers.Unconfined)
            var closed = false
            Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
                CardEditorScreen(flashcards, FakeSpeech(), CardEditorTarget.New(languageId), onClose = { closed = true })
            }
            compose.onNodeWithText("Vorderseite").performTextInput("kitab")
            compose.onNodeWithText("Rückseite").performTextInput("Buch")
            compose.onNodeWithText("In beide Richtungen abfragen").performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()
            compose.waitForIdle()

            assertTrue(closed)
            assertEquals(listOf(true), runBlocking { database.cards().of(languageId).map { it.bothWays } })
        } finally {
            database.close()
        }
    }
}
