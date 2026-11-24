package de.emilyshub

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.island.IslandState
import de.emilyshub.tracking.PhotoInfo
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The photos of an event (SPEC.md §17, "Ereignisse"): taken with the camera app, Tracking open meanwhile, kept encrypted
 * in Tracking at once, the file the camera wrote deleted, never in the gallery; deleted after asking. In one test, as the
 * FileProvider keeps its folders from the first test of a run.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrackingPhotoScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    /** Where the camera app writes the photo, as the app lends it the file. */
    private val shot = File(File(app.cacheDir, "camera"), "photo.jpg")

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private fun photos(): List<PhotoInfo> =
        checkNotNull(runBlocking { app.tracking.withDatabase { database -> database.events().all().flatMap { database.photos().of(it.id) } } })

    /** The camera app, asked by the app, takes a photo into the file it was lent, while the app is behind it. */
    private fun ActivityScenario<MainActivity>.takePhoto(): Intent {
        var request: Intent? = null
        onActivity { request = shadowOf(it).nextStartedActivityForResult.intent }
        moveToState(Lifecycle.State.CREATED)
        assertTrue("Tracking stays open while the camera app is in front", app.tracking.state is IslandState.Open)
        shot.outputStream().use { out ->
            Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        // Android delivers the result before the activity is in front again; the scenario cannot stop at STARTED
        // without passing RESUMED, so it comes while the activity is still behind.
        onActivity { shadowOf(it).receiveResult(checkNotNull(request), Activity.RESULT_OK, null) }
        moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        return checkNotNull(request)
    }

    @Test
    fun `a photo is taken with the camera app, kept encrypted, and deleted after asking, and none without one`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.setUpTracking()
            compose.onNodeWithText("+ Ereignis").tap()
            compose.onNodeWithText("Duschen").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("+ Foto").tap()
            var cancelled: Intent? = null
            scenario.onActivity { cancelled = shadowOf(it).nextStartedActivityForResult.intent }
            scenario.onActivity { shadowOf(it).receiveResult(checkNotNull(cancelled), Activity.RESULT_CANCELED, null) }
            compose.waitForIdle()
            assertEquals("back without a photo, none is kept", emptyList<PhotoInfo>(), photos())
            assertFalse(shot.exists())

            compose.onNodeWithText("+ Foto").tap()
            val request = scenario.takePhoto()

            // Encoded anew off the main thread, which the test's idling does not see.
            compose.waitUntil(5_000) { photos().isNotEmpty() }
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, request.action)
            val output = checkNotNull(request.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java))
            assertEquals("lent through the app's own provider", "${app.packageName}.camera", output.authority)
            assertEquals(1, photos().size)
            assertFalse("no file in plain text is left", shot.exists())
            app.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, null).use { cursor ->
                assertEquals("not in the gallery", 0, cursor?.count ?: 0)
            }

            compose.onNode(hasContentDescription("Foto vom", substring = true)).tap()
            compose.onNodeWithText("Foto löschen").performClick()
            compose.onNodeWithText("Foto löschen?").assertIsDisplayed()
            compose.onNodeWithText("Löschen").performClick()
            compose.waitForIdle()

            assertEquals(emptyList<PhotoInfo>(), photos())
        }
    }

    @Test
    fun `without a camera app it says so, Tracking stays open, and no file is left`() {
        shadowOf(app).checkActivities(true)
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            compose.onNodeWithText("+ Ereignis").tap()
            compose.onNodeWithText("Duschen").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("+ Foto").tap()

            compose.onNodeWithText("Keine Kamera-App gefunden.").performScrollTo().assertIsDisplayed()
            assertTrue(app.tracking.state is IslandState.Open)
            assertFalse(shot.exists())
            assertEquals(emptyList<PhotoInfo>(), photos())
        }
    }
}
