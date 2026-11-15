package de.emilyshub.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.R
import de.emilyshub.island.IslandClipboard
import de.emilyshub.island.SystemFingerprint
import de.emilyshub.languages.CameraShot
import de.emilyshub.languages.PdfInMemory
import de.emilyshub.ui.tracking.SummaryPdf
import de.emilyshub.ui.tracking.summaryBlocks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * The doctor's summary and the photos of events on a device (SPEC.md §17, #132, #133): the PDF Android's own
 * PdfDocument writes opens, A4 with the pages laid out; a photo the camera app writes to the file it is lent is kept in
 * Tracking's encrypted database, no file in plain text is left, and nothing goes to the gallery. The camera app itself is
 * not driven: what it does is write to the lent file. Test values are made up.
 */
@RunWith(AndroidJUnit4::class)
class SummaryAndPhotosOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "tracking-photos-${UUID.randomUUID()}.db"
    private val file = context.getDatabasePath(name)
    private val keyFile = File(context.noBackupFilesDir, "$name.key")

    private val tracking = Tracking(
        keyFile = keyFile,
        databaseFile = file,
        schema = TrackingDatabase.VERSION,
        fingerprint = SystemFingerprint(
            context,
            "tracking-photos-fingerprint-key",
            R.string.tracking_fingerprint_unlock_title,
            R.string.tracking_fingerprint_enable_title,
        ),
        openDatabase = { rawKey -> TrackingDatabase.open(context, rawKey, name) },
        clipboard = IslandClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    )

    @After
    fun deleteDatabase() {
        tracking.lock()
        context.deleteDatabase(name)
        keyFile.delete()
        CameraShot.clear(context)
    }

    @Test
    fun `the PDF opens as A4 with as many pages as were laid out`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val today = LocalDate.parse("2026-11-15")
        val events = (1..120).map { TrackingEvent(id = it.toLong(), kind = EventKind.Shower, at = Instant.parse("2026-11-14T08:00:00Z"), pulseBefore = 80) }
        val summary = DoctorSummary.of(TrackingRecords(berlin, events = events), today.minusDays(27), today, hints = emptyList())
        val out = ByteArrayOutputStream()

        val pages = SummaryPdf.write(summaryBlocks(context.resources, summary, "- Eigene Frage?", berlin, today), out) { "Seite $it" }

        assertTrue(pages >= 3)
        PdfInMemory.open(out.toByteArray()).use { renderer ->
            assertEquals(pages, renderer.pageCount)
            renderer.openPage(0).use { page ->
                assertEquals(SummaryPdf.PAGE_WIDTH, page.width)
                assertEquals(SummaryPdf.PAGE_HEIGHT, page.height)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                var ink = false
                for (x in 0 until bitmap.width step 4) for (y in 0 until 120 step 2) if (bitmap.getPixel(x, y) != Color.WHITE) ink = true
                assertTrue("the title is drawn", ink)
            }
        }
    }

    @Test
    fun `a photo the camera app writes is kept encrypted in Tracking, and neither a file nor the gallery has it`() {
        val inGallery = galleryCount()
        runBlocking {
            tracking.load()
            tracking.setUp("tracking-geheim")
        }
        val events = TrackingEvents(tracking, Clock.systemDefaultZone())
        val event = checkNotNull(runBlocking { events.create(EventKind.Bath) })
        val uri = CameraShot.prepare(context)
        // As the camera app would, with the permission the intent grants it.
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                .compress(Bitmap.CompressFormat.JPEG, 90, checkNotNull(out))
        }

        val taken = checkNotNull(CameraShot.read(context, EventPhotos.MAX_TAKEN_BYTES))
        val added = runBlocking { events.addPhoto(event, taken) }
        CameraShot.clear(context)

        assertTrue(added is PhotoAdd.Added)
        assertFalse(File(File(context.cacheDir, "camera"), "photo.jpg").exists())
        assertEquals(inGallery, galleryCount())
        tracking.lock()
        assertFalse("the database holds it encrypted", file.readBytes().contains("JFIF".toByteArray()))
        runBlocking { assertTrue(tracking.unlockWithPassword("tracking-geheim")) }
        assertNotEquals(null, runBlocking { events.photo((added as PhotoAdd.Added).id) })
    }

    private fun galleryCount(): Int =
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, null).use { it?.count ?: 0 }

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }
}
