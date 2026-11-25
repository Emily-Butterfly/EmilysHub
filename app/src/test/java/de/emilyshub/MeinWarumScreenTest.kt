package de.emilyshub

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.warum.Reason
import de.emilyshub.warum.ReasonColor
import de.emilyshub.warum.ReasonDraft
import de.emilyshub.warum.ReasonFont
import de.emilyshub.warum.ReasonLook
import de.emilyshub.warum.ReasonSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Mein Warum (SPEC.md §18, #161, #162): the fifth entry of Sonstiges, a board of Gründe looked at without buttons but the
 * pen, opened large and swiped through, and edited: added, changed, moved and deleted; with a photo, at most 100.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
class MeinWarumScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.settingsDao.values["reminders-asked"] = "yes"
    }

    @Test
    fun `Mein Warum is the fifth entry of Sonstiges, and empty it says what it is for`() {
        launchApp().use {
            compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()
            val entries = listOf("Ziele", "Dokumentation", "Sprachen", "Tracking", "Mein Warum").map { entry ->
                compose.onNode(hasText(entry) and hasClickAction()).fetchSemanticsNode().boundsInRoot.top
            }
            assertEquals(entries.sorted(), entries)

            compose.onNode(hasText("Mein Warum") and hasClickAction()).performClick()
            compose.onNode(isHeading() and hasText("Mein Warum")).assertIsDisplayed()
            compose.onNodeWithText("Hier sammelst du, was dich hält: Menschen, Momente, Pläne, Sätze.").assertIsDisplayed()
            compose.onNode(hasText("Ersten Grund hinzufügen") and hasClickAction()).assertIsDisplayed()
            compose.onNodeWithContentDescription("Bearbeiten").assertIsDisplayed()
        }
    }

    @Test
    fun `the first reason is added, and an empty one is not saved`() {
        launchApp().use {
            openWarum()
            compose.onNode(hasText("Ersten Grund hinzufügen") and hasClickAction()).performClick()
            compose.onNode(isHeading() and hasText("Neuer Grund")).assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Ein Grund braucht einen Text oder ein Foto.").assertIsDisplayed()

            compose.onNode(hasSetTextAction()).performTextInput("Lina und ihr Lachen")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNode(isHeading() and hasText("Mein Warum")).assertIsDisplayed()
            compose.onNodeWithText("Lina und ihr Lachen").assertIsDisplayed()
        }
        assertEquals(listOf("Lina und ihr Lachen"), texts())
    }

    @Test
    fun `the board shows no buttons but the pen, a reason opens large, and swiping leads to the next`() {
        reason("Das Meer")
        reason("Lina")

        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Mehr zu „Lina“").assertDoesNotExist()
            compose.onNodeWithContentDescription("Grund hinzufügen").assertDoesNotExist()

            compose.onNodeWithText("Lina").performClick()
            compose.onNodeWithContentDescription("Zurück").assertIsDisplayed()
            compose.onNodeWithText("Lina").assertIsDisplayed()

            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Das Meer").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Das Meer").assertIsDisplayed()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isHeading() and hasText("Mein Warum")).assertIsDisplayed()
        }
    }

    @Test
    fun `editing, a reason moves forward and back through its menu, and is changed when tapped`() {
        reason("Das Meer")
        reason("Lina")
        reason("Mein Garten")

        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Bearbeiten").performClick()
            menuOf("Das Meer", "Nach vorn")
            compose.waitUntil(5_000) { texts() == listOf("Mein Garten", "Das Meer", "Lina") }
            menuOf("Mein Garten", "Nach hinten")
            compose.waitUntil(5_000) { texts() == listOf("Das Meer", "Mein Garten", "Lina") }

            compose.onNodeWithText("Lina").performClick()
            compose.onNode(isHeading() and hasText("Grund ändern")).assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextReplacement("Lina und Ben")
            compose.onNodeWithText("Speichern").performClick()

            // Back once leaves editing, and the board is as calm as on opening.
            compose.onNodeWithContentDescription("Fertig").performClick()
            compose.onNodeWithContentDescription("Grund hinzufügen").assertDoesNotExist()
        }
        assertEquals(listOf("Das Meer", "Mein Garten", "Lina und Ben"), texts())
    }

    @Test
    fun `a reason is deleted only once confirmed`() {
        reason("Das Meer")
        reason("Lina")

        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Bearbeiten").performClick()
            menuOf("Lina", "Löschen")
            compose.onNode(hasText("Diesen Grund löschen?") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
            assertEquals(listOf("Lina", "Das Meer"), texts())

            menuOf("Lina", "Löschen")
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(5_000) { texts() == listOf("Das Meer") }
            compose.onNodeWithText("Lina").assertDoesNotExist()
        }
    }

    @Test
    fun `a reason added while editing comes on top`() {
        reason("Das Meer")

        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Bearbeiten").performClick()
            compose.onNodeWithContentDescription("Grund hinzufügen").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("Tee am Morgen")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Tee am Morgen").assertIsDisplayed()
        }
        assertEquals(listOf("Tee am Morgen", "Das Meer"), texts())
    }

    @Test
    fun `Mein Warum stays open when another tab is chosen, as the goals do, and comes back looked at`() {
        reason("Lina")

        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Bearbeiten").performClick()
            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()
            compose.onNode(isHeading() and hasText("Mein Warum")).assertIsDisplayed()
            compose.onNodeWithText("Lina").assertIsDisplayed()
            // Back as it is looked at, not edited.
            compose.onNodeWithContentDescription("Grund hinzufügen").assertDoesNotExist()
        }
    }

    @Test
    fun `the search finds no reason`() {
        reason("Lina und ihr Lachen")
        app.inbox("Lina anrufen")

        launchApp().use {
            compose.onNodeWithContentDescription("Suchen").performClick()
            compose.onNode(hasSetTextAction() and hasText("Suchbegriff")).performTextInput("lina")
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Lina anrufen").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Lina und ihr Lachen").assertDoesNotExist()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a reason has a photo from the gallery with a text as a strip below it, or alone, and opens large`() {
        val sea = app.lessonDocuments.put("Meer.png", png())
        val alone = app.lessonDocuments.put("Lina.png", png())

        launchApp().use { scenario ->
            openWarum()
            compose.onNode(hasText("Ersten Grund hinzufügen") and hasClickAction()).performClick()
            compose.onNode(hasText("Foto wählen") and hasClickAction()).performClick()
            // Android's photo picker, with the phone's photos and albums, not the file dialog.
            val request = scenario.pickFile(Uri.parse(sea))
            assertEquals(MediaStore.ACTION_PICK_IMAGES to "image/*", request.action to request.type)
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Foto").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Foto entfernen") and hasClickAction()).assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextInput("Das Meer")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithContentDescription("Bearbeiten").performClick()
            compose.onNodeWithContentDescription("Grund hinzufügen").performClick()
            compose.onNode(hasText("Foto wählen") and hasClickAction()).performClick()
            scenario.pickFile(Uri.parse(alone))
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Foto").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithContentDescription("Fertig").performClick()

            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Foto").fetchSemanticsNodes().size == 2 }
            compose.onNodeWithText("Das Meer").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Foto").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Das Meer").assertIsDisplayed()
        }
        val board = runBlocking { app.reasons.board().first() }
        assertEquals(listOf("", "Das Meer"), board.map { it.text })
        assertTrue(board.all { it.photo != null })
    }

    @Test
    fun `once 100 reasons have a photo, the editor says so`() {
        runBlocking { repeat(100) { app.testDatabase.reasons().insert(Reason(text = "Grund $it", position = it, photo = "${UUID.randomUUID()}.att")) } }

        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Bearbeiten").performClick()
            compose.onNodeWithContentDescription("Grund hinzufügen").performClick()
            compose.onNodeWithText("Schon 100 Gründe haben ein Foto; mehr geht nicht.").assertIsDisplayed()
            compose.onNode(hasText("Foto wählen") and hasClickAction()).assertIsNotEnabled()
        }
    }

    @Test
    fun `a reason gets a font, a size and both colours, shown as on the board, and the editor says when it is hard to read`() {
        launchApp().use {
            openWarum()
            compose.onNode(hasText("Ersten Grund hinzufügen") and hasClickAction()).performClick()
            compose.onNode(hasSetTextAction()).performTextInput("Tee am Morgen")
            compose.onNodeWithText("Schwer zu lesen").assertDoesNotExist()

            compose.onNode(hasText("Schönschrift") and isSelectable()).performScrollTo().performClick()
            compose.onNode(hasText("Groß") and isSelectable()).performScrollTo().performClick()
            compose.onNode(hasContentDescription("Hintergrund: Weiß")).performScrollTo().performClick()
            compose.onNode(hasContentDescription("Schriftfarbe: Gelb, mittel")).performScrollTo().performClick()
            compose.onNodeWithText("Schwer zu lesen").performScrollTo().assertIsDisplayed()
            compose.onNode(hasContentDescription("Schriftfarbe: Blau, kräftig")).performScrollTo().performClick()
            compose.onNodeWithText("Schwer zu lesen").assertDoesNotExist()
            // The reason as it will stand on the board, above the choices.
            compose.onAllNodesWithText("Tee am Morgen").assertCountEquals(2)

            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Tee am Morgen").assertIsDisplayed()
        }
        assertEquals(
            ReasonLook(ReasonFont.Script, ReasonSize.Large, ReasonColor.White, ReasonColor.Blue4),
            runBlocking { app.reasons.board().first().single().look },
        )
    }

    @Test
    fun `the licences of the fonts can be read from the editor`() {
        launchApp().use {
            openWarum()
            compose.onNode(hasText("Ersten Grund hinzufügen") and hasClickAction()).performClick()
            compose.onNode(hasText("Lizenzen der Schriften") and hasClickAction()).performScrollTo().performClick()
            compose.onNode(isHeading() and hasText("Lizenzen der Schriften")).assertIsDisplayed()
            // Each under its name, written in itself, once read from the app's files.
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Libre Baskerville (Serifen)").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Great Vibes (Schönschrift)"))
            compose.onNodeWithText("Great Vibes (Schönschrift)").assertExists()
            compose.onAllNodesWithText("SIL Open Font License", substring = true).onFirst().assertExists()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the board is exported as one image after the hint that it is not encrypted, through Android's file dialog`() {
        reason("Lina")

        launchApp().use { scenario ->
            openWarum()
            compose.onNodeWithContentDescription("Mehr zu „Mein Warum“").performClick()
            compose.onNode(hasText("Als Bild exportieren") and hasAnyAncestor(isPopup())).performClick()
            compose.onNode(hasText("Das Bild ist nicht verschlüsselt.") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText("Weiter") and hasAnyAncestor(isDialog())).performClick()

            compose.waitUntil(10_000) { scenario.startedForResult() != null }
            val request = scenario.pickFile(Uri.parse("content://bilder/1"))
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
            assertEquals("image/png", request.type)
            assertEquals("Mein Warum 2026-10-23.png", request.getStringExtra(Intent.EXTRA_TITLE))
            compose.waitUntil(10_000) { app.lessonDocuments.written.containsKey("content://bilder/1") }
        }
        val image = app.lessonDocuments.written.getValue("content://bilder/1")
        assertEquals(1080, BitmapFactory.decodeByteArray(image, 0, image.size).width)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a board too high for one image goes as several into a folder chosen once`() {
        repeat(24) { reason("Grund $it: " + "so viel, was mich hält ".repeat(20)) }

        launchApp().use { scenario ->
            openWarum()
            compose.onNodeWithContentDescription("Mehr zu „Mein Warum“").performClick()
            compose.onNode(hasText("Als Bild exportieren") and hasAnyAncestor(isPopup())).performClick()
            compose.onNode(hasText("Weiter") and hasAnyAncestor(isDialog())).performClick()

            compose.waitUntil(30_000) { scenario.startedForResult() != null }
            val request = scenario.pickFile(Uri.parse("content://ordner"))
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, request.action)
            compose.waitUntil(30_000) { app.pictureFolders["content://ordner"]?.files?.size?.let { it > 1 } == true }
        }
        val names = checkNotNull(app.pictureFolders["content://ordner"]).files.keys.toList()
        assertEquals(List(names.size) { "Mein Warum 2026-10-23 (${it + 1} von ${names.size}).png" }, names)
    }

    @Test
    fun `an empty board offers no export, nor does the board while it is edited`() {
        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Mehr zu „Mein Warum“").assertDoesNotExist()
        }
        reason("Lina")
        launchApp().use {
            openWarum()
            compose.onNodeWithContentDescription("Mehr zu „Mein Warum“").assertIsDisplayed()
            compose.onNodeWithContentDescription("Bearbeiten").performClick()
            compose.onNodeWithContentDescription("Mehr zu „Mein Warum“").assertDoesNotExist()
        }
    }

    /** The request the app made of another app last, if any, without answering it. */
    private fun ActivityScenario<MainActivity>.startedForResult(): Intent? {
        var request: Intent? = null
        onActivity { request = shadowOf(it).peekNextStartedActivityForResult()?.intent }
        return request
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a photo taken with the camera app is the reason's, and the file the camera wrote is gone`() {
        val shot = File(File(app.cacheDir, "camera"), "photo.jpg")
        launchApp().use { scenario ->
            openWarum()
            compose.onNode(hasText("Ersten Grund hinzufügen") and hasClickAction()).performClick()
            compose.onNode(hasText("Foto aufnehmen") and hasClickAction()).performClick()
            var request: Intent? = null
            scenario.onActivity { request = shadowOf(it).nextStartedActivityForResult.intent }
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, checkNotNull(request).action)
            shot.writeBytes(png())
            scenario.onActivity { shadowOf(it).receiveResult(checkNotNull(request), Activity.RESULT_OK, null) }
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Foto").fetchSemanticsNodes().isNotEmpty() }
            assertFalse("no file in plain text is left", shot.exists())
            compose.onNodeWithText("Speichern").performClick()
        }
        assertTrue(runBlocking { app.reasons.board().first() }.single().photo != null)
    }

    @Test
    fun `without a camera app the editor says so, and nothing is left`() {
        shadowOf(app).checkActivities(true)
        launchApp().use {
            openWarum()
            compose.onNode(hasText("Ersten Grund hinzufügen") and hasClickAction()).performClick()
            compose.onNode(hasText("Foto aufnehmen") and hasClickAction()).performClick()
            compose.onNodeWithText("Keine Kamera-App gefunden.").assertIsDisplayed()
            assertFalse(File(File(app.cacheDir, "camera"), "photo.jpg").exists())
        }
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun openWarum() {
        compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()
        compose.onNode(hasText("Mein Warum") and hasClickAction()).performClick()
    }

    /** Chooses [item] in the menu of the reason that says [text]. */
    private fun menuOf(text: String, item: String) {
        compose.onNodeWithContentDescription("Mehr zu „$text“").performClick()
        compose.onNode(hasText(item) and hasAnyAncestor(isPopup())).performClick()
    }

    private fun reason(text: String) = runBlocking { check(app.reasons.save(null, ReasonDraft(text)) is Saving.Saved) }

    private fun texts() = runBlocking { app.reasons.board().first().map { it.text } }
}
