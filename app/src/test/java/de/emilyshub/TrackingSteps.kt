package de.emilyshub

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement

/** What a user does with Tracking (SPEC.md §17), shared by its Robolectric tests. */

/** The Tracking password the tests set. */
const val TRACKING_PASSWORD = "tracking-geheim"

/** Opens Tracking: under "Sonstiges" in the bottom bar, then "Tracking" in its list. */
fun ComposeTestRule.openTracking() {
    onNode(isSelectable() and hasText("Sonstiges")).performClick()
    onNode(hasText("Tracking") and hasClickAction()).performClick()
}

/** Sets the Tracking password on first opening, the warning confirmed, and the fingerprint switched on if [withFingerprint]. */
fun ComposeTestRule.setUpTracking(withFingerprint: Boolean = false) {
    field("Tracking-Passwort").performTextInput(TRACKING_PASSWORD)
    field("Tracking-Passwort wiederholen").performTextInput(TRACKING_PASSWORD)
    onNodeWithText("Verstanden").performScrollTo().performClick()
    if (withFingerprint) onNodeWithText("Mit Fingerabdruck entsperren").performScrollTo().performClick()
    onNodeWithText("Passwort festlegen").performScrollTo().performClick()
}

/** Unlocks the locked Tracking with [password]. */
fun ComposeTestRule.unlockTracking(password: String = TRACKING_PASSWORD) {
    field("Passwort").performTextReplacement(password)
    onNodeWithText("Entsperren").performClick()
}

/** "Fertig" on a page of Tracking, and the page closed, which saves what it shows. */
fun ComposeTestRule.done() {
    onNodeWithText("Fertig").performClick()
    waitForIdle()
}
