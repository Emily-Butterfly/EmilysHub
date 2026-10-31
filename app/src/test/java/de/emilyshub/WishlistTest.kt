package de.emilyshub

import android.app.AlarmManager
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.wishes.WishStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

/**
 * The Wunschliste tab (SPEC.md §6): wishes made and changed in the editor, from open to in progress to done on a day,
 * and then in the review; a calm place, without reminders.
 */
@RunWith(AndroidJUnit4::class)
class WishlistTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a wish is made with title, description, period and tags, and listed as open`() {
        app.tag("Reisen")

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Noch keine Ziele", substring = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Ziel anlegen").performClick()

            field("Titel").performTextInput("Nordlichter sehen")
            field("Beschreibung (optional)").performTextInput("In Tromsø")
            field("Zeitraum (optional)").performTextInput("Winter 2027")
            compose.onNode(hasText("Reisen") and hasClickAction()).tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Offen").assertIsDisplayed()
            compose.onNode(hasText("Nordlichter sehen") and hasText("Winter 2027") and hasText("Reisen")).assertIsDisplayed()
        }
        val saved = runBlocking { app.wishes.current().first().single() }
        assertEquals("In Tromsø", saved.wish.description)
        assertEquals(WishStatus.Open, saved.wish.status)
        assertEquals(1, saved.tagIds.size)
    }

    @Test
    fun `a wish needs a title`() {
        launchApp().use {
            openWishlist()
            compose.onNodeWithContentDescription("Ziel anlegen").performClick()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Bitte gib einen Titel ein.").assertIsDisplayed()
        }
        assertEquals(emptyList<Any>(), runBlocking { app.wishes.current().first() })
    }

    @Test
    fun `a wish in progress is listed above the open ones`() {
        app.wish("Arabisch lernen")
        app.wish("Bergtour")

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Bergtour").performClick()
            option("In Arbeit").tap()
            compose.onNodeWithText("Speichern").performClick()

            assertEquals(listOf("In Arbeit", "Bergtour", "Offen", "Arabisch lernen"), shownInOrder())
        }
    }

    @Test
    fun `a wish done leaves the list for the review, with the day it came true, and can be opened again from there`() {
        app.wish("Bergtour", status = WishStatus.InProgress)

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Bergtour").performClick()
            option("Erledigt").tap()
            compose.onNodeWithText("23.10.2026").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Bergtour").assertDoesNotExist()
            compose.onNodeWithText("Rückblick (1)").performClick()
            compose.onNodeWithText("Rückblick: erreichte Ziele").assertIsDisplayed()
            compose.onNodeWithText("Bergtour").assertIsDisplayed()
            compose.onNodeWithText("Erledigt am 23.10.2026").assertIsDisplayed()

            compose.onNodeWithText("Bergtour").performClick()
            option("Offen").tap()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Noch kein Ziel erreicht.").assertIsDisplayed()
        }
        assertEquals(null, runBlocking { app.wishes.current().first().single().wish.doneOn })
    }

    @Test
    fun `the review lists the wishes come true, the last first`() {
        app.wish("Bergtour", status = WishStatus.Done, doneOn = LocalDate.parse("2026-08-01"))
        app.wish("Marathon laufen", status = WishStatus.Done, doneOn = LocalDate.parse("2026-10-03"))

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Noch keine Ziele", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Rückblick (2)").performClick()

            assertEquals(
                listOf("Marathon laufen", "Erledigt am 03.10.2026", "Bergtour", "Erledigt am 01.08.2026"),
                shownInOrder(),
            )
        }
    }

    @Test
    fun `a wish is deleted after asking`() {
        app.wish("Bergtour")

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Bergtour").performClick()
            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Noch keine Ziele", substring = true).assertIsDisplayed()
        }
        assertEquals(emptyList<Any>(), runBlocking { app.wishes.current().first() })
    }

    @Test
    fun `the wishlist shows only the wishes with the tag chosen`() {
        val travel = app.tag("Reisen")
        app.tag("Sport")
        app.wish("Nordlichter sehen", tags = setOf(travel))
        app.wish("Bergtour")

        launchApp().use {
            openWishlist()
            chip("Reisen").performClick()

            compose.onNodeWithText("Nordlichter sehen").assertIsDisplayed()
            compose.onNodeWithText("Bergtour").assertDoesNotExist()

            chip("Sport").performClick()
            compose.onNodeWithText("Kein Ziel mit dem Tag „Sport“.").assertIsDisplayed()
        }
    }

    @Test
    fun `wishes remind of nothing, and are not on Heute`() {
        app.wish("Nordlichter sehen", period = "Winter 2027")
        app.wish("Bergtour", status = WishStatus.InProgress)

        launchApp().use {
            compose.onNodeWithText("Nordlichter sehen").assertDoesNotExist()
            compose.onNodeWithText("Bergtour").assertDoesNotExist()
        }
        val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        assertEquals(emptyList<Any>(), alarms.alarmsFor(ReminderReceiver.ACTION_REMIND))
    }

    private fun openWishlist() = compose.onNode(hasText("Ziele") and hasClickAction()).performClick()

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** A chip of the tag filter, not the tags shown with a wish. */
    private fun chip(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))

    /** The texts shown, top to bottom, of the section titles and the wishes in the list. */
    private fun shownInOrder(): List<String> = compose.onAllNodesWithText("", substring = true).fetchSemanticsNodes()
        .sortedBy { it.boundsInRoot.top }
        .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        .filter { it in SHOWN }

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private companion object {
        val SHOWN = setOf(
            "In Arbeit", "Offen", "Arabisch lernen", "Bergtour", "Marathon laufen",
            "Erledigt am 03.10.2026", "Erledigt am 01.08.2026",
        )
    }
}
