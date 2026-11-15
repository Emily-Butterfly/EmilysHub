package de.emilyshub

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.ledger.SubscriptionDraft
import de.emilyshub.ledger.SubscriptionEditing
import de.emilyshub.ledger.SubscriptionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate

/**
 * An Abo to cancel soon on Heute, under "Demnächst" (#80), which opens with a tap; and its reminder tapped, which opens
 * it behind the app's lock, "Gekündigt" asking until when.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionTodayTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    /** What reminders need is allowed, so that the app does not ask for it first. */
    @Before
    fun allowReminders() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52, where it all fits below the line for thoughts
    fun `an Abo in its lead is under Demnaechst with its last day, and opens with a tap`() {
        // The test app's clock: 23 October 2026; 14 days of lead.
        abo("Streaming", cancelBy = "2026-11-06")
        abo("Zeitung", cancelBy = "2026-12-31")

        launchApp().use {
            compose.onNodeWithText("Demnächst").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("kündigen bis 06.11.2026").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Zeitung").assertDoesNotExist()

            compose.onNodeWithText("Streaming").performScrollTo().performClick()

            compose.onNodeWithText("Abo bearbeiten").assertIsDisplayed()
        }
    }

    @Test
    fun `its reminder's Gekuendigt opens the Abo once unlocked, also after a turn, asking until when, and cancels it`() {
        val streaming = abo("Streaming", cancelBy = "2026-11-06")
        app.authenticator.deviceSecure = true

        ActivityScenario.launch<MainActivity>(MainActivity.openSubscription(app, streaming, cancel = true)).use { scenario ->
            compose.onNodeWithText("„Streaming“ gekündigt").assertDoesNotExist()
            // Turned while still locked: what is to open stays to open.
            scenario.recreate()
            compose.unlockWithFingerprint(app.authenticator)

            compose.onNodeWithText("„Streaming“ gekündigt").assertIsDisplayed()
            compose.onNode(hasText("Gekündigt") and hasAnyAncestor(isDialog())).performClick()
            compose.waitForIdle()
        }
        val cancelled = runBlocking { app.subscriptions.overview().first() }.cancelled.single()
        assertEquals(SubscriptionStatus.Cancelled(LocalDate.parse("2026-11-06")), cancelled.status)
    }

    @Test
    fun `its reminder tapped opens the Abo, without asking`() {
        val streaming = abo("Streaming", cancelBy = "2026-11-06")

        ActivityScenario.launch<MainActivity>(MainActivity.openSubscription(app, streaming, cancel = false)).use {
            compose.onNodeWithText("Abo bearbeiten").assertIsDisplayed()
            compose.onNodeWithText("„Streaming“ gekündigt").assertDoesNotExist()
        }
    }

    private fun abo(name: String, cancelBy: String): Long = runBlocking {
        val saved = app.subscriptions.save(SubscriptionEditing.New, SubscriptionDraft(name, cancelBy = LocalDate.parse(cancelBy)))
        (saved as Saving.Saved).id
    }
}
