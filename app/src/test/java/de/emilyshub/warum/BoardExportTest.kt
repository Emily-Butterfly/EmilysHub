package de.emilyshub.warum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.backup.FakeBackupFolder
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.testAttachmentStore
import de.emilyshub.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Mein Warum as images (SPEC.md §18, #164): the whole board as PNG, 1080 px wide, in the design the app shows; split into
 * several when it gets too high; one written where Android's file dialog says, several into a folder chosen once;
 * nothing of it left unencrypted in the app's storage.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoardExportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = inMemoryDatabase()
    private val documents = FakeLessonDocuments()
    private val files = LessonFiles(Vault(testAttachmentStore(), documents, Dispatchers.Unconfined), Dispatchers.Unconfined)
    private val reasons = Reasons({ database }, files.vault, Dispatchers.Unconfined)
    private val folder = FakeBackupFolder("Bilder")
    private val clock = TestClock(Instant.parse("2026-11-24T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val theme = BoardTheme(StandardColors(text = Color.BLACK, background = Color.LTGRAY), page = 0xFFFFF7FE.toInt())

    private fun export(limit: Int = BoardPicture.LIMIT) = BoardExport(
        reasons, documents, folderAt = { folder }, BoardPicture({ Typeface.DEFAULT }, limit), clock, Dispatchers.Unconfined,
    )

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the board is one PNG 1080 px wide on the page's colour, with its photos and colours, named by the day`() = runBlocking {
        reason("Lina", look = ReasonLook(background = ReasonColor.Blue4, text = ReasonColor.White))
        reason("Das Meer", photo = png(400, 300, Color.GREEN))
        val export = export()

        assertEquals(1, export.count(theme))
        assertEquals("Mein Warum 2026-11-24.png", export.fileName(page = 1, of = 1))
        assertTrue(export.saveOne("content://bilder/1", theme))

        val image = decode(documents.written.getValue("content://bilder/1"))
        assertEquals(1080, image.width)
        assertEquals(theme.page, image.getPixel(2, 2))
        // The newest on the left: the photo, then the blue one on the right.
        assertTrue("its photo", (0 until image.height step 4).any { near(image.getPixel(300, it), Color.GREEN) })
        assertTrue("a Grund on strong blue", (0 until image.height step 4).any { image.getPixel(780, it) == ReasonColor.Blue4.argb })
    }

    @Test
    fun `a board higher than the limit goes into the folder as several images, none higher, counted in their names`() = runBlocking {
        repeat(6) { reason("Grund $it ".repeat(20)) }
        val export = export(limit = 1200)

        val count = export.count(theme)
        assertTrue("several: $count", count > 1)
        assertTrue(export.saveInto("content://ordner", theme))

        assertEquals(List(count) { "Mein Warum 2026-11-24 (${it + 1} von $count).png" }, folder.files.keys.toList())
        folder.files.values.map(::decode).forEach { image ->
            assertEquals(1080, image.width)
            assertTrue(image.height <= 1200)
        }
    }

    @Test
    fun `a reason higher than the limit has an image of its own, as high as it is`() = runBlocking {
        reason("Lina")
        reason("Hoch", photo = png(100, 600, Color.RED))
        reason("Ben")
        val export = export(limit = 1200)

        assertEquals(3, export.count(theme))
        assertTrue(export.saveInto("content://ordner", theme))
        val heights = folder.files.values.map { decode(it).height }
        assertTrue("the high one whole: $heights", heights[1] > 1200)
    }

    @Test
    fun `nothing of the images is left in the app's storage`() = runBlocking {
        reason("Lina", photo = png(200, 200, Color.YELLOW))
        val before = everyFile()

        export().saveOne("content://bilder/1", theme)

        assertEquals(before, everyFile())
    }

    @Test
    fun `an empty board makes no image`() = runBlocking {
        assertEquals(0, export().count(theme))
        assertFalse(export().saveOne("content://bilder/1", theme))
        assertTrue(documents.written.isEmpty())
    }

    private suspend fun reason(text: String, photo: ByteArray? = null, look: ReasonLook = ReasonLook()) {
        val file = photo?.let { (reasons.importPhoto(documents.put("foto.png", it), null) as AttachmentImport.Added).attachment.file }
        check(reasons.save(null, ReasonDraft(text, file, look)) is Saving.Saved)
    }

    private fun png(width: Int, height: Int, color: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    /** Whether [a] and [b] are the same colour but for what JPEG makes of it. */
    private fun near(a: Int, b: Int) = listOf(16, 8, 0).all { shift -> kotlin.math.abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) < 12 }

    private fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun everyFile(): Set<String> = listOf(context.filesDir, context.cacheDir).flatMap { dir ->
        dir.walkTopDown().filter(File::isFile).map { it.path }.toList()
    }.toSet()
}
