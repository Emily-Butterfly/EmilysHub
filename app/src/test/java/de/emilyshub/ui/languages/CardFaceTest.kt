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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.languages.Card
import de.emilyshub.languages.Rating
import org.junit.Assert.assertFalse
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
                CardFace(card, intervals, hint = false, enabled = true, onAnswer = {})
            }
        }
        compose.onNodeWithText("Aufdecken").performClick()
        listOf("Nochmal", "Schwer", "Gut", "Leicht").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertFalse("$label is cut off", layouts.single().hasVisualOverflow)
        }
    }
}
