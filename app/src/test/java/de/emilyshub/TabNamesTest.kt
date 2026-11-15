package de.emilyshub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.ui.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The names in the bottom bar (SPEC.md §1; #114): each on one line and whole, on the width of the A52, however large
 * the system's type is; the text measured as the phone would.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabNamesTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun `at the usual size each tab name stands on one line, whole`() = assertTabNamesOnOneLine()

    @Test
    @Config(qualifiers = "w411dp-h915dp", fontScale = 1.5f)
    fun `at 150 percent each tab name stands on one line, whole`() = assertTabNamesOnOneLine()

    @Test
    @Config(qualifiers = "w411dp-h915dp", fontScale = 2f)
    fun `at 200 percent each tab name stands on one line, whole`() = assertTabNamesOnOneLine()

    private fun assertTabNamesOnOneLine() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val names = TopLevelDestination.entries.map { app.getString(it.label) }
        launchApp().use {
            names.forEach { name ->
                val layout = layoutOf(name)
                assertEquals("$name on one line", 1, layout.lineCount)
                assertFalse("$name cut short", layout.isLineEllipsized(0))
                // To a screen reader, the tab says its full name.
                compose.onNode(isSelectable() and hasText(name)).assertExists()
            }
        }
    }

    private fun layoutOf(name: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(name) and hasAnyAncestor(isSelectable()), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
