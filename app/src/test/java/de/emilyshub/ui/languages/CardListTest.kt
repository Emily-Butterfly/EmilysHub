package de.emilyshub.ui.languages

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.Day
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.languages.Card
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Language
import de.emilyshub.languages.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The list of the tab Karten on a narrow phone (SPEC.md §15; #144): front and back the whole width, when due, "sicher"
 * and the topic on a line beneath, and above the list how many cards it shows.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h640dp")
class CardListTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val database = inMemoryDatabase()

    /** Monday, 16 November 2026, 10:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-16T09:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val long = "Ein langes Wort auf der Vorderseite, das zwei Zeilen braucht"

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a long front takes the whole width, with when it is due, sicher and its topic beneath`() {
        val languageId = runBlocking { database.languages().insertLanguage(Language(name = "Arabisch")) }
        val colours = runBlocking { database.languages().insertTopic(Topic(languageId = languageId, name = "Farben")) }
        runBlocking {
            database.cards().insert(
                Card(
                    languageId = languageId, front = long, back = "Rückseite", topicId = colours,
                    stability = 40.0, difficulty = 5.0, firstReview = LocalDate.parse("2026-10-01"),
                    lastReview = LocalDate.parse("2026-10-19"), due = LocalDate.parse("2026-11-18"), interval = 30,
                ),
            )
        }
        show(languageId)

        // Each line its own node, not the row they are merged into for TalkBack.
        val front = compose.onNodeWithText(long, useUnmergedTree = true)
        val back = compose.onNodeWithText("Rückseite", useUnmergedTree = true).getBoundsInRoot()
        val details = compose.onNodeWithText("fällig 18.11.2026 · sicher · Farben", useUnmergedTree = true).getBoundsInRoot()
        // The width the front may take: the screen less 16 dp to each side, nothing at the right.
        val layouts = mutableListOf<TextLayoutResult>()
        front.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val screen = compose.onRoot().fetchSemanticsNode().size.width
        val sides = with(Density(ApplicationProvider.getApplicationContext())) { 32.dp.roundToPx() }
        assertEquals(screen - sides, layouts.single().layoutInput.constraints.maxWidth)
        assertTrue("the back beneath the front", back.top >= front.getBoundsInRoot().bottom)
        assertTrue("the details beneath the back", details.top >= back.bottom)
    }

    @Test
    fun `above the list how many cards it shows, of how many with a topic chosen or a search`() {
        val languageId = runBlocking { database.languages().insertLanguage(Language(name = "Arabisch")) }
        val colours = runBlocking { database.languages().insertTopic(Topic(languageId = languageId, name = "Farben")) }
        runBlocking {
            database.cards().insert(Card(languageId = languageId, front = "ahmar", back = "rot", topicId = colours))
            database.cards().insert(Card(languageId = languageId, front = "azraq", back = "blau", topicId = colours))
            database.cards().insert(Card(languageId = languageId, front = "kitab", back = "Buch"))
        }
        show(languageId)

        compose.onNodeWithText("3 Karten").assertExists()
        compose.onNodeWithText("Farben").performClick()
        compose.onNodeWithText("2 von 3 Karten").assertExists()
        compose.onNodeWithText("Alle").performClick()
        compose.onNode(hasSetTextAction() and hasText("Karten durchsuchen")).performTextInput("buch")
        compose.onNodeWithText("1 von 3 Karten").assertExists()
    }

    /** The tab Karten of [languageId], its topic filter kept as the page of a language keeps it. */
    private fun show(languageId: Long) {
        val flashcards = Flashcards({ database }, clock, FakeLessonDocuments(), Dispatchers.Unconfined)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            var filter by remember { mutableStateOf<Long?>(null) }
            CardsTabContent(flashcards, Day(clock), languageId, filter, { filter = it }, {}, {})
        }
    }
}
