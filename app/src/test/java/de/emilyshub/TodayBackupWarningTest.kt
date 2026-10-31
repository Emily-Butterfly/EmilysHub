package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.Instant

/** Heute tells when the last backup is over 30 days old or failed (SPEC.md §10), and leads to the backups. */
@RunWith(AndroidJUnit4::class)
class TodayBackupWarningTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun askedBefore() {
        // The questions for reminders came once already, so that they do not come up over Heute.
        app.settingsDao.values["reminders-asked"] = "yes"
        runBlocking { app.backups.setPassword("Flieder und Lila") }
    }

    @Test
    fun `a nightly backup that failed is told on Heute, which leads to the backups`() {
        runBlocking { app.nightlyBackups.choose(FOLDER) }
        app.backupFolder.failing = true
        runBlocking { app.nightlyBackups.backUp() }

        launchApp().use {
            compose.onNodeWithText("Das letzte Backup ist fehlgeschlagen").assertIsDisplayed()

            compose.onNodeWithText("Zu den Backups").performClick()
            compose.onNodeWithText("Backup-Passwort").assertIsDisplayed()
        }
    }

    @Test
    fun `a last backup over 30 days old is told on Heute`() {
        runBlocking { app.backups.export { ByteArrayOutputStream() } }
        app.clock.now = Instant.parse("2026-11-23T07:00:00Z")

        launchApp().use {
            compose.onNodeWithText("Das letzte Backup ist über 30 Tage alt").assertIsDisplayed()
        }
    }

    @Test
    fun `a backup not 30 days old is not told`() {
        runBlocking { app.backups.export { ByteArrayOutputStream() } }
        app.clock.now = Instant.parse("2026-11-20T07:00:00Z")

        launchApp().use {
            compose.onNodeWithText("Heute steht nichts an", substring = true).assertExists()
            compose.onNodeWithText("Backup", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `with no backup at all after 30 days, Heute says so`() {
        app.clock.now = Instant.parse("2026-11-23T07:00:00Z")

        launchApp().use {
            compose.onNodeWithText("Noch kein Backup").assertIsDisplayed()
        }
    }

    private companion object {
        const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
    }
}
