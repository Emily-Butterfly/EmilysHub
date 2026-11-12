package de.emilyshub.languages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.KeyGenerator

/**
 * The lessons' attachments on a device (SPEC.md §15): a PDF opened from memory and drawn page by page by Android's own
 * PdfRenderer, and a photo taken the way the camera app hands it over, through the file the app lends it, read, kept
 * encrypted and deleted at once. The camera app itself is not driven here: what it does is write to the lent file.
 */
@RunWith(AndroidJUnit4::class)
class AttachmentsOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = AttachmentStore({ File(context.cacheDir, "attachments-test") }, { key })
    private val documents = PhoneLessonDocuments(context)
    private val files = LessonFiles(store, documents)

    @Test
    fun aPdfOpensFromMemoryAndItsPagesAreDrawn() {
        val pdf = pdfOfTwoPages()

        assertEquals(2, documents.pdfPages(pdf))
        assertEquals(null, documents.pdfPages("kein PDF".toByteArray()))
        PdfInMemory.open(pdf).use { renderer ->
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                assertNotEquals("the black square is drawn", Color.WHITE, bitmap.getPixel(20, 20))
            }
        }
        val added = runBlocking { files.importPdf(put(pdf), count = 0) }
        assertTrue(added is AttachmentImport.Added)
    }

    @Test
    fun aPhotoTheCameraAppWritesIsTakenEncryptedAndTheFileIsGone() {
        val uri = CameraShot.prepare(context)
        // As the camera app would, with the permission the intent grants it.
        context.contentResolver.openOutputStream(uri, "wt").use { out ->
            Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                .compress(Bitmap.CompressFormat.JPEG, 90, checkNotNull(out))
        }

        val added = runBlocking { files.importPhoto(uri.toString(), count = 0) } as AttachmentImport.Added
        CameraShot.clear(context)

        assertFalse(File(File(context.cacheDir, "camera"), "photo.jpg").exists())
        val kept = checkNotNull(store.read(added.attachment.file))
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(kept, 0, kept.size)
        assertEquals(2000, bitmap.width)
        assertEquals(1333, bitmap.height)
    }

    /** [pdf] in a file of the cache, at its address, as the file dialog would give it. */
    private fun put(pdf: ByteArray): String {
        val file = File(context.cacheDir, "Arbeitsblatt.pdf").apply { writeBytes(pdf) }
        return android.net.Uri.fromFile(file).toString()
    }

    private fun pdfOfTwoPages(): ByteArray {
        val document = PdfDocument()
        repeat(2) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(200, 300, index + 1).create())
            page.canvas.drawRect(10f, 10f, 60f, 60f, Paint().apply { color = Color.BLACK })
            document.finishPage(page)
        }
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}
