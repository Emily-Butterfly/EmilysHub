package de.emilyshub.ui.languages

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.languages.Card
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Language
import de.emilyshub.languages.Prompt
import de.emilyshub.languages.Rating
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
import org.robolectric.annotation.Config

/** A card in practice (SPEC.md §15): the four answers fit, written out, on a narrow phone. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h640dp")
class CardFaceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `the four answers are written out whole on a narrow phone`() {
        val card = Card(id = 1, languageId = 1, front = "Wort", back = "Übersetzung")
        val intervals = Rating.entries.associateWith { 1 }
        // As the practice lays it out: 24 dp on each side.
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            Column(Modifier.padding(24.dp)) {
                CardFace(Prompt(card), intervals, hint = false, enabled = true, onAnswer = {})
            }
        }
        compose.onNodeWithText("Aufdecken").performClick()
        listOf("Nochmal", "Schwer", "Gut", "Leicht").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertFalse("$label is cut off", layouts.single().hasVisualOverflow)
        }
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
                CardEditorScreen(flashcards, CardEditorTarget.New(languageId), onClose = { closed = true })
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
