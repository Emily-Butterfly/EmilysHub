package de.emilyshub.warum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.languages.Languages
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.testAttachmentStore
import de.emilyshub.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The Gründe of Mein Warum (SPEC.md §18): checked, laid out in the order the user gives them, and deleted; their photos
 * shrunk, kept encrypted, at most 100, and gone with their reason.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReasonsTest {

    private val database = inMemoryDatabase()
    private val folder = "reasons-${System.nanoTime()}"
    private val store = testAttachmentStore(folder)
    private val documents = FakeLessonDocuments()
    private val files = LessonFiles(Vault(store, documents, Dispatchers.Unconfined), Dispatchers.Unconfined)
    private val reasons = Reasons({ database }, files.vault, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a reason is kept with its text, without spaces at its edges, on its lines`() = runBlocking {
        val saved = reasons.save(null, ReasonDraft("  Lina\nund ihr Lachen  \n"))

        val id = (saved as Saving.Saved).id
        assertEquals(listOf("Lina\nund ihr Lachen"), texts())
        assertEquals(ReasonDraft("Lina\nund ihr Lachen"), reasons.editing(id))
    }

    @Test
    fun `a reason keeps its font, size and colours, and one the app does not know counts as Standard`() = runBlocking {
        val look = ReasonLook(ReasonFont.Script, ReasonSize.Large, ReasonColor.Blue4, ReasonColor.White)
        val id = save("Lina")
        reasons.save(id, ReasonDraft("Lina", look = look))
        assertEquals(look, reasons.editing(id)?.look)
        assertEquals(look, reasons.board().first().single().look)

        database.reasons().update(checkNotNull(database.reasons().find(id)).copy(font = "comic", textColor = "pink"))
        assertEquals(look.copy(font = ReasonFont.Standard, text = ReasonColor.Standard), reasons.editing(id)?.look)
    }

    @Test
    fun `an empty reason without a photo is refused, and one over 500 characters`() = runBlocking {
        assertEquals(Saving.Refused(setOf(ReasonProblem.Empty)), reasons.save(null, ReasonDraft(" \n​ ")))
        assertEquals(Saving.Refused(setOf(ReasonProblem.TextTooLong)), reasons.save(null, ReasonDraft("a".repeat(501))))
        assertEquals(Saving.Saved::class, reasons.save(null, ReasonDraft("🌱".repeat(500)))::class)

        assertEquals(1, texts().size)
    }

    @Test
    fun `a new reason comes on top`() = runBlocking {
        save("Lina")
        save("Das Meer")
        save("Mein Garten")

        assertEquals(listOf("Mein Garten", "Das Meer", "Lina"), texts())
    }

    @Test
    fun `a reason changed keeps its place`() = runBlocking {
        val lina = save("Lina")
        save("Das Meer")

        reasons.save(lina, ReasonDraft("Lina und Ben"))

        assertEquals(listOf("Das Meer", "Lina und Ben"), texts())
    }

    @Test
    fun `a reason moves forward and back by one, and stays at the ends`() = runBlocking {
        val lina = save("Lina")
        save("Das Meer")
        val garden = save("Mein Garten")

        reasons.forward(lina)
        assertEquals(listOf("Mein Garten", "Lina", "Das Meer"), texts())
        reasons.forward(lina)
        reasons.forward(lina)
        assertEquals(listOf("Lina", "Mein Garten", "Das Meer"), texts())

        reasons.back(garden)
        reasons.back(garden)
        assertEquals(listOf("Lina", "Das Meer", "Mein Garten"), texts())
    }

    @Test
    fun `a reason dragged goes where it is put, and a new one still comes on top`() = runBlocking {
        val lina = save("Lina")
        save("Das Meer")
        save("Mein Garten")
        save("Tee am Morgen")

        reasons.move(lina, 1)
        assertEquals(listOf("Tee am Morgen", "Lina", "Mein Garten", "Das Meer"), texts())
        reasons.move(lina, 10)
        assertEquals(listOf("Tee am Morgen", "Mein Garten", "Das Meer", "Lina"), texts())

        save("Musik")
        assertEquals("Musik", texts().first())
    }

    @Test
    fun `a reason deleted is gone, and the others keep their order`() = runBlocking {
        save("Lina")
        val sea = save("Das Meer")
        save("Mein Garten")

        reasons.delete(sea)

        assertEquals(listOf("Mein Garten", "Lina"), texts())
        assertNull(reasons.editing(sea))
        // Changed once gone, it is not made anew.
        assertEquals(Saving.Refused(setOf(ReasonProblem.Gone)), reasons.save(sea, ReasonDraft("Das Meer")))
        assertEquals(2, texts().size)
    }

    @Test
    fun `a photo is shrunk to 2000 px at the long edge and kept encrypted, with a text or without`() = runBlocking {
        val photo = import(png(3000, 1500))

        val withText = (reasons.save(null, ReasonDraft("Das Meer", photo)) as Saving.Saved).id
        val alone = (reasons.save(null, ReasonDraft(" ", import(png(100, 100)))) as Saving.Saved).id

        assertEquals(ReasonDraft("Das Meer", photo), reasons.editing(withText))
        assertEquals("", reasons.editing(alone)?.text)
        val kept = checkNotNull(reasons.photo(photo))
        val bitmap = BitmapFactory.decodeByteArray(kept, 0, kept.size)
        assertEquals(2000, bitmap.width)
        assertEquals(1000, bitmap.height)
        assertFalse("not as it is shown", File(File(ApplicationProvider.getApplicationContext<Context>().filesDir, folder), photo).readBytes().contentEquals(kept))
    }

    @Test
    fun `no picture is no photo`() = runBlocking {
        assertEquals(AttachmentImport.Unreadable, reasons.importPhoto(documents.put("kein.jpg", "kein Bild".toByteArray()), null))
    }

    @Test
    fun `a reason deleted takes its photo with it, and a photo replaced or taken off goes once saved`() = runBlocking {
        val first = import(png(50, 50))
        val id = (reasons.save(null, ReasonDraft("Lina", first)) as Saving.Saved).id

        val second = import(png(60, 60))
        reasons.save(id, ReasonDraft("Lina", second))
        assertFalse("replaced", store.has(first))
        assertTrue(store.has(second))

        reasons.save(id, ReasonDraft("Lina"))
        assertFalse("taken off", store.has(second))

        val third = import(png(70, 70))
        reasons.save(id, ReasonDraft("Lina", third))
        reasons.delete(id)
        assertFalse("gone with its reason", store.has(third))
        assertEquals(emptySet<String>(), store.names())
    }

    @Test
    fun `a photo imported but left unsaved is let go of, and one saved stays`() = runBlocking {
        val saved = import(png(50, 50))
        val id = (reasons.save(null, ReasonDraft("Lina", saved)) as Saving.Saved).id

        val left = import(png(60, 60))
        reasons.discard(ReasonDraft("Lina", left), reasons.editing(id))

        assertFalse(store.has(left))
        assertTrue(store.has(saved))
    }

    @Test
    fun `a photo of another reason or of a lesson is no photo to save`() = runBlocking {
        val lina = import(png(50, 50))
        reasons.save(null, ReasonDraft("Lina", lina))

        assertEquals(Saving.Refused(setOf(ReasonProblem.PhotoGone)), reasons.save(null, ReasonDraft("Auch Lina", lina)))
        assertEquals(Saving.Refused(setOf(ReasonProblem.PhotoGone)), reasons.save(null, ReasonDraft("", "${UUID.randomUUID()}.att")))
    }

    @Test
    fun `at most 100 reasons have a photo, and one that has one may change it`() = runBlocking {
        repeat(99) { database.reasons().insert(Reason(text = "", position = it, photo = store.write(byteArrayOf(1)))) }
        assertTrue(reasons.photoAllowed(null))
        val last = (reasons.save(null, ReasonDraft("Lina", import(png(50, 50)))) as Saving.Saved).id

        assertFalse(reasons.photoAllowed(null))
        assertEquals(AttachmentImport.TooMany, reasons.importPhoto(documents.put("mehr.png", png(50, 50)), null))
        val staged = import(png(50, 50), reasonId = last)
        assertEquals(Saving.Refused(setOf(ReasonProblem.TooManyPhotos)), reasons.save(null, ReasonDraft("Ben", staged)))
        assertTrue(reasons.photoAllowed(last))
        assertEquals(Saving.Saved(last), reasons.save(last, ReasonDraft("Lina", staged)))
        assertEquals(Saving.Saved::class, reasons.save(null, ReasonDraft("Ohne Foto"))::class)
    }

    private suspend fun import(bytes: ByteArray, reasonId: Long? = null): String =
        (reasons.importPhoto(documents.put("foto.png", bytes), reasonId) as AttachmentImport.Added).attachment.file

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            .compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private suspend fun save(text: String): Long = (reasons.save(null, ReasonDraft(text)) as Saving.Saved).id

    private suspend fun texts() = reasons.board().first().map { it.text }
}
