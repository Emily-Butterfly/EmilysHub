package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement

/** What a user does with the Doku (SPEC.md §14), shared by its Robolectric tests. */

/** The Doku password the tests set. */
const val DOKU_PASSWORD = "geheim-geheim"

/** Opens the Doku: under "Sonstiges" in the bottom bar, then "Dokumentation" in its list. */
fun ComposeTestRule.openDoku() {
    onNode(isSelectable() and hasText("Sonstiges")).performClick()
    onNode(hasText("Dokumentation") and hasClickAction()).performClick()
}

/** Sets the Doku password on first opening, the warning confirmed, and the fingerprint switched on if [withFingerprint]. */
fun ComposeTestRule.setUpDoku(withFingerprint: Boolean = false) {
    field("Doku-Passwort").performTextInput(DOKU_PASSWORD)
    field("Doku-Passwort wiederholen").performTextInput(DOKU_PASSWORD)
    onNodeWithText("Verstanden").performScrollTo().performClick()
    if (withFingerprint) onNodeWithText("Mit Fingerabdruck entsperren").performScrollTo().performClick()
    onNodeWithText("Passwort festlegen").performScrollTo().performClick()
}

/** Unlocks the locked Doku with [password]. */
fun ComposeTestRule.unlockDoku(password: String = DOKU_PASSWORD) {
    field("Passwort").performTextReplacement(password)
    onNodeWithText("Entsperren").performClick()
}

/** The text field labelled [label]. */
fun ComposeTestRule.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label))
