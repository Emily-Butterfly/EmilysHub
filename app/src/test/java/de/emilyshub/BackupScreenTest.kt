package de.emilyshub

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The backups in the settings (SPEC.md §10): the backup password set once, a backup saved to a file chosen in Android's
 * dialog, and one loaded from there with its password, replacing all data only once confirmed.
 */
@RunWith(AndroidJUnit4::class)
class BackupScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `the backup password is set once, with at least 10 characters, typed the same twice`() {
        launchApp().use {
            openBackups()
            compose.onNodeWithText("Backup speichern").performScrollTo().assertIsNotEnabled()

            setPassword("Flieder", "Flieder")
            compose.onNodeWithText("Mindestens 10 Zeichen").assertIsDisplayed()
            setPassword(PASSWORD, "Flieder und lila")
            compose.onNodeWithText("Die beiden Passwörter sind nicht gleich").assertIsDisplayed()
            setPassword(PASSWORD, PASSWORD)

            compose.onNodeWithText("Das Backup-Passwort ist festgelegt.", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Passwort festlegen").assertDoesNotExist()
            assertTrue(runBlocking { app.backups.hasPassword() })
        }
    }

    @Test
    fun `a backup is saved to the file chosen in Android's dialog, offered a name with the date`() {
        app.routine("Vitamine", "07:30")
        val saved = ByteArrayOutputStream()
        shadowOf(app.contentResolver).registerOutputStream(FILE, saved)

        launchApp().use { scenario ->
            openBackups()
            setPassword(PASSWORD, PASSWORD)
            compose.onNodeWithText("Backup speichern").performScrollTo().performClick()

            val request = scenario.documentRequest()
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
            assertEquals("EmilysHub-Backup-2026-10-23.emhb", request.getStringExtra(Intent.EXTRA_TITLE))
            scenario.answer(request, FILE)

            compose.onNodeWithText("Backup gespeichert.").performScrollTo().assertIsDisplayed()
        }
        val backup = runBlocking { app.backups.open({ ByteArrayInputStream(saved.toByteArray()) }, PASSWORD) }
        assertEquals(1, backup.summary.routines)
    }

    @Test
    fun `a backup is loaded with its password, and replaces all data once that is confirmed`() {
        aBackupOfVitaminsThenYoga()
        // As on a new device, where no backup password is set yet.
        app.settingsDao.values.clear()

        launchApp().use { scenario ->
            openBackups()
            compose.onNodeWithText("Backup laden").performScrollTo().performClick()
            val request = scenario.documentRequest()
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)
            scenario.answer(request, FILE)

            enterBackupPassword("Lila und Flieder")
            compose.onNodeWithText("Das Passwort passt nicht zu diesem Backup.").assertIsDisplayed()
            enterBackupPassword(PASSWORD)

            compose.onNodeWithText("Backup laden?").assertIsDisplayed()
            compose.onNodeWithText("Das Backup vom 23.10.2026, 07:00 ersetzt alle Daten", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Im Backup: 1 Routine, 0 To-dos, 0 Wünsche, 0 Orte und 1 Tag.").assertIsDisplayed()
            compose.onNodeWithText("Sein Passwort wird dann auch dein Backup-Passwort auf diesem Gerät.").assertIsDisplayed()
            assertEquals(listOf("Yoga"), routineNames())
            compose.onNodeWithText("Ersetzen").performClick()

            compose.onNodeWithText("Backup geladen.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Das Backup-Passwort ist festgelegt.", substring = true).performScrollTo().assertIsDisplayed()
            assertEquals(listOf("Vitamine"), routineNames())
        }
    }

    @Test
    fun `a backup not confirmed changes nothing`() {
        aBackupOfVitaminsThenYoga()

        launchApp().use { scenario ->
            openBackups()
            compose.onNodeWithText("Backup laden").performScrollTo().performClick()
            scenario.answer(scenario.documentRequest(), FILE)
            enterBackupPassword(PASSWORD)
            compose.onNodeWithText("Sein Passwort wird", substring = true).assertDoesNotExist()

            compose.onNodeWithText("Abbrechen").performClick()

            compose.onNodeWithText("Backup laden?").assertDoesNotExist()
            assertEquals(listOf("Yoga"), routineNames())
        }
    }

    @Test
    fun `a file that is no backup is named as such`() {
        shadowOf(app.contentResolver).registerInputStreamSupplier(FILE) { ByteArrayInputStream("Einkaufsliste".toByteArray()) }

        launchApp().use { scenario ->
            openBackups()
            compose.onNodeWithText("Backup laden").performScrollTo().performClick()
            scenario.answer(scenario.documentRequest(), FILE)
            enterBackupPassword(PASSWORD)

            compose.onNodeWithText("Diese Datei ist kein Backup von EmilysHub.").assertIsDisplayed()
        }
    }

    @Test
    fun `the backups are to be copied off the phone`() {
        launchApp().use {
            openBackups()

            compose.onNodeWithText("Kopiere deine Backups regelmäßig vom Handy weg", substring = true)
                .performScrollTo().assertIsDisplayed()
        }
    }

    /** A backup with the routine Vitamine and a tag, in [FILE]; since then, Vitamine is gone and Yoga there. */
    private fun aBackupOfVitaminsThenYoga() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.tag("Gesundheit")
        val file = ByteArrayOutputStream()
        runBlocking {
            app.backups.setPassword(PASSWORD)
            app.backups.export { file }
            app.routines.delete(vitamins)
        }
        app.routine("Yoga", "18:00")
        shadowOf(app.contentResolver).registerInputStreamSupplier(FILE) { ByteArrayInputStream(file.toByteArray()) }
    }

    private fun openBackups() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Backups verwalten").performScrollTo().performClick()
    }

    private fun setPassword(password: String, repeated: String) {
        compose.onNode(hasSetTextAction() and hasText("Passwort")).performTextReplacement(password)
        compose.onNode(hasSetTextAction() and hasText("Passwort wiederholen")).performTextReplacement(repeated)
        compose.onNodeWithText("Passwort festlegen").performScrollTo().performClick()
    }

    private fun enterBackupPassword(password: String) {
        compose.onNode(hasSetTextAction() and hasText("Passwort") and hasAnyAncestor(isDialog())).performTextReplacement(password)
        compose.onNodeWithText("Öffnen").performClick()
    }

    private fun routineNames() = runBlocking { app.routines.inUse().first() }.map { it.name }

    /** What the app asked Android's file dialog for. */
    private fun ActivityScenario<MainActivity>.documentRequest(): Intent {
        var request: Intent? = null
        onActivity { request = shadowOf(it).nextStartedActivityForResult.intent }
        return checkNotNull(request)
    }

    /** The user picks [file] in Android's file dialog. */
    private fun ActivityScenario<MainActivity>.answer(request: Intent, file: Uri) {
        onActivity { shadowOf(it).receiveResult(request, Activity.RESULT_OK, Intent().setData(file)) }
        compose.waitForIdle()
    }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        val FILE: Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABackup.emhb")
    }
}
