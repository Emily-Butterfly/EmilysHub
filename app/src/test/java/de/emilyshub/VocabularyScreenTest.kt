package de.emilyshub

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
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.languages.CardDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
