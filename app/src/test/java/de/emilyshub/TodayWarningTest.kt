package de.emilyshub

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.Repetition
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.DayOfWeek

/** Heute warns while something the routines in use need from Android is lacking (SPEC.md §11). */
@RunWith(AndroidJUnit4::class)
class TodayWarningTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun askedBefore() {
        // The questions for reminders came once already, so that they do not come up over Heute.
        app.settingsDao.values["reminders-asked"] = "yes"
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `with notifications not allowed, Heute warns of it and leads to "Funktioniert alles?"`() {
        app.routine("Tabletten", "08:00")

        launchApp().use {
            compose.onNodeWithText("Erinnerungen kommen vielleicht nicht an").assertIsDisplayed()
            compose.onNodeWithText("Es fehlt: Benachrichtigungen.").assertIsDisplayed()

            compose.onNodeWithText("Jetzt prüfen").performClick()
            compose.onNodeWithText("Funktioniert alles?").assertIsDisplayed()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Tabletten").assertIsDisplayed()
        }
    }

    @Test
    fun `with what the routines need allowed, Heute does not warn, also with battery limits and no location`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.routine("Tabletten", "08:00")

        launchApp().use {
            compose.onNodeWithText("Tabletten").assertIsDisplayed()
            compose.onNodeWithText("Erinnerungen kommen vielleicht nicht an").assertDoesNotExist()
        }
    }

    @Test
    fun `without routines, Heute does not warn`() {
        launchApp().use {
            compose.waitForIdle()
            compose.onNodeWithText("Erinnerungen kommen vielleicht nicht an").assertDoesNotExist()
        }
    }

    @Test
    fun `with a routine bound to a place, Heute warns of the location always lacking, and with nothing due today too`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val home = app.place("Zuhause", "FritzBox")
        app.routine("Müll", "19:00", repetition = Repetition.Weekdays(setOf(DayOfWeek.MONDAY)), places = setOf(home))

        launchApp().use {
            compose.onNodeWithText("Es fehlt: Standort „Immer“.").assertIsDisplayed()
        }
    }

    @Test
    fun `back from Android's settings with it allowed, the warning goes`() {
        app.routine("Tabletten", "08:00")

        launchApp().use { scenario ->
            compose.onNodeWithText("Erinnerungen kommen vielleicht nicht an").assertIsDisplayed()

            scenario.leaveAndReturn { shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS) }

            compose.onNodeWithText("Erinnerungen kommen vielleicht nicht an").assertDoesNotExist()
        }
    }
}
