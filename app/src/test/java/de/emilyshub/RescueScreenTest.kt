package de.emilyshub

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.Unopenable
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.PatientOrder
import de.emilyshub.ui.dateAndTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId

/**
 * The way out when the data cannot be opened (#100): instead of the app, and behind its lock, why in one sentence, and
 * trying again, loading a backup into new data, or starting over, the old data laid aside, never deleted; the Doku stays.
 */
@RunWith(AndroidJUnit4::class)
class RescueScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val databaseFile: File get() = HubDatabase.file(app)
    private val keyFile: File get() = File(app.noBackupFilesDir, "database.key")

    @After
    fun closeDoku() {
        app.doku.lock()
        app.deleteDatabase(DokuDatabase.NAME)
    }

    @Test
    fun `for each reason, it says why in one sentence, and that no reminders come until then`() {
        val reasons = mapOf(
            Unopenable.KeyBroken to "Der Schlüssel zu deinen Daten fehlt oder ist beschädigt.",
            Unopenable.FileBroken to "Die Datei mit deinen Daten ist beschädigt.",
            Unopenable.NewerVersion to "Deine Daten stammen aus einer neueren Version der App.",
        )
        for ((reason, sentence) in reasons) {
            broken(reason)
            launchApp().use {
                compose.onNodeWithText("Deine Daten lassen sich nicht öffnen").assertIsDisplayed()
                compose.onNodeWithText(sentence).assertIsDisplayed()
                compose.onNodeWithText("Bis dahin kommen keine Erinnerungen.").assertIsDisplayed()
                listOf("Erneut versuchen", "Backup laden", "Neu beginnen").forEach {
                    compose.onNode(hasText(it) and hasClickAction()).performScrollTo().assertIsDisplayed()
                }
                val installNewer = compose.onNodeWithText("Installiere die neuere Version der App", substring = true)
                if (reason == Unopenable.NewerVersion) installNewer.assertIsDisplayed() else installNewer.assertDoesNotExist()
                compose.onNodeWithContentDescription("Einstellungen").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `it stands behind the app's lock, which counts as on, as its setting cannot be read`() {
        runBlocking {
            app.settings.load()
            app.settings.setAppLockEnabled(false)
        }
        app.authenticator.deviceSecure = true
        broken(Unopenable.KeyBroken)

        launchApp().use {
            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
            compose.onNodeWithText("Deine Daten lassen sich nicht öffnen").assertDoesNotExist()

            compose.unlockWithFingerprint(app.authenticator)

            compose.onNodeWithText("Deine Daten lassen sich nicht öffnen").assertIsDisplayed()
        }
    }

    @Test
    fun `trying again opens them once more, and into the app once they open`() {
        app.openFails = Unopenable.FileBroken
        runBlocking { app.data.retry() }

        launchApp().use {
            compose.onNodeWithText("Erneut versuchen").performClick()
            compose.onNodeWithText("Sie lassen sich weiterhin nicht öffnen.").performScrollTo().assertIsDisplayed()

            app.openFails = null
            compose.onNodeWithText("Erneut versuchen").performClick()

            compose.onNodeWithContentDescription("Einstellungen").assertIsDisplayed()
            compose.onNodeWithText("Deine Daten lassen sich nicht öffnen").assertDoesNotExist()
        }
    }

    @Test
    fun `starting over, once confirmed, lays the data aside and starts empty, the Doku staying as it is`() {
        aDokuWithFrauM()
        app.routine("Vitamine", "07:30")
        oldFiles()
        broken(Unopenable.KeyBroken)

        launchApp().use {
            compose.onNode(hasText("Neu beginnen") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("Neu beginnen?").assertIsDisplayed()
            compose.onNodeWithText("Abbrechen").performClick()
            assertTrue("nothing laid aside yet", databaseFile.exists())

            compose.onNode(hasText("Neu beginnen") and hasClickAction()).performScrollTo().performClick()
            compose.onNode(hasText("Neu beginnen") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()

            compose.onNodeWithContentDescription("Einstellungen").assertIsDisplayed()
            assertEquals(emptyList<String>(), routineNames())
        }
        assertFalse(databaseFile.exists())
        assertFalse(keyFile.exists())
        val laidAside = File(app.noBackupFilesDir, "set-aside").walk().filter { it.isFile }.map { it.readText() }.toSet()
        assertTrue(laidAside.containsAll(setOf("old data", "old key")))
        assertEquals(listOf("Frau M."), patientNames())
    }

    @Test
    fun `data laid aside show on the backup screen with their date, to delete for good once confirmed`() {
        oldFiles()
        broken(Unopenable.FileBroken)
        val when_ = app.clock.now.atZone(ZoneId.systemDefault()).dateAndTime()

        launchApp().use {
            compose.onNode(hasText("Neu beginnen") and hasClickAction()).performScrollTo().performClick()
            compose.onNode(hasText("Neu beginnen") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()

            compose.onNodeWithContentDescription("Einstellungen").performClick()
            compose.onNodeWithText("Backups verwalten").performScrollTo().performClick()
            compose.onNodeWithText("Beiseitegelegt am $when_").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Endgültig löschen").performClick()
            compose.onNodeWithText("Endgültig löschen?").assertIsDisplayed()
            compose.onNode(hasText("Endgültig löschen") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Beiseitegelegt am", substring = true).assertDoesNotExist()
        }
        assertEquals(emptyList<File>(), File(app.noBackupFilesDir, "set-aside").listFiles().orEmpty().toList())
    }

    @Test
    fun `a backup is loaded into new data, and the app opens with it`() {
        aBackupOfVitamins()
        oldFiles()
        broken(Unopenable.KeyBroken)

        launchApp().use { scenario ->
            compose.onNode(hasText("Backup laden") and hasClickAction()).performScrollTo().performClick()
            scenario.pickFile(FILE)
            enterBackupPassword("Lila und Flieder")
            compose.onNodeWithText("Das Passwort passt nicht zu diesem Backup.").assertIsDisplayed()
            assertEquals("all as it was", "old data", databaseFile.readText())

            enterBackupPassword(PASSWORD)
            compose.onNodeWithText("Sein Passwort wird dann auch dein Backup-Passwort auf diesem Gerät.").assertIsDisplayed()
            compose.onNodeWithText("Ersetzen").performClick()

            compose.onNodeWithContentDescription("Einstellungen").assertIsDisplayed()
            assertEquals(listOf("Vitamine"), routineNames())
        }
        assertFalse(databaseFile.exists())
    }

    @Test
    fun `a backup opened but not confirmed leaves everything as it was`() {
        aBackupOfVitamins()
        oldFiles()
        broken(Unopenable.KeyBroken)

        launchApp().use { scenario ->
            compose.onNode(hasText("Backup laden") and hasClickAction()).performScrollTo().performClick()
            scenario.pickFile(FILE)
            enterBackupPassword(PASSWORD)
            compose.onNodeWithText("Abbrechen").performClick()

            compose.onNodeWithText("Deine Daten lassen sich nicht öffnen").assertIsDisplayed()
        }
        assertEquals("old data", databaseFile.readText())
        assertEquals("old key", keyFile.readText())
        assertEquals(null, File(app.noBackupFilesDir, "set-aside").listFiles()?.firstOrNull())
    }

    /** The data cannot be opened for [reason]: until tried again, though they would open now. */
    private fun broken(reason: Unopenable) {
        app.openFails = reason
        assertEquals(reason, runBlocking { app.data.retry() })
        app.openFails = null
    }

    /** Stand-ins for the files of the data that cannot be opened. */
    private fun oldFiles() {
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText("old data")
        keyFile.writeText("old key")
    }

    private fun aBackupOfVitamins() {
        app.routine("Vitamine", "07:30")
        val file = ByteArrayOutputStream()
        runBlocking {
            app.backups.setPassword(PASSWORD)
            app.backups.export { file }
        }
        shadowOf(app.contentResolver).registerInputStreamSupplier(FILE) { ByteArrayInputStream(file.toByteArray()) }
    }

    private fun aDokuWithFrauM() = runBlocking {
        app.doku.load()
        app.doku.setUp(DOKU_PASSWORD)
        app.patients.create(PatientDraft("Frau M."))
        app.doku.lock()
    }

    private fun patientNames(): List<String> = runBlocking {
        assertTrue(app.doku.unlockWithPassword(DOKU_PASSWORD))
        checkNotNull(app.patients.active(PatientOrder.Name)).map { it.name }
    }

    private fun enterBackupPassword(password: String) {
        compose.onNode(hasSetTextAction() and hasText("Passwort") and hasAnyAncestor(isDialog())).performTextReplacement(password)
        compose.onNodeWithText("Öffnen").performClick()
    }

    private fun routineNames() = runBlocking { app.routines.inUse().first() }.map { it.name }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        const val DOKU_PASSWORD = "Doku-Geheimnis"
        val FILE: Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABackup.emhb")
    }
}
