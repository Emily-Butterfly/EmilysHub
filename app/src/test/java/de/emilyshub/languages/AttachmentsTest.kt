package de.emilyshub.languages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Importing into a lesson (SPEC.md §15): text into its note, within its limit; photos shrunk, turned and stripped;
 * PDFs and the number of attachments within their limits; all kept encrypted on the disk, taken in on saving, let go of
 * when left unsaved, deleted with their lesson, and cleared up when nothing has them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentsTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val store = testAttachmentStore()
    private val documents = FakeLessonDocuments()
    private val files = LessonFiles(store, documents, Dispatchers.Unconfined)
    private val languages = Languages({ database }, clock, files, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a text file is added to the note as typed text, after a blank line`() = runBlocking {
        val address = documents.put("Stunde.txt", "﻿Vokabeln:\r\n- كتاب\r\n\r\n".toByteArray())

        assertEquals(TextImport.Appended("Vokabeln:\n- كتاب"), files.importText(address, ""))
        assertEquals(TextImport.Appended("## Notiz\n\nVokabeln:\n- كتاب"), files.importText(address, "## Notiz\n"))
    }

    @Test
    fun `a text file that would bring the note over its limit, or is no text, is turned down`() = runBlocking {
        val half = "x".repeat(LessonDraft.NOTE_MAX / 2)
        val address = documents.put("lang.txt", half.toByteArray())
        assertTrue(files.importText(address, half.dropLast(2)) is TextImport.Appended)
        assertEquals(TextImport.TooLong, files.importText(address, half))
        assertEquals(TextImport.TooLong, files.importText(documents.put("riesig.txt", ByteArray(LessonFiles.TEXT_MAX_BYTES + 1) { 'a'.code.toByte() }), ""))
        assertEquals(TextImport.NotText, files.importText(documents.put("bild.txt", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00)), ""))
        assertEquals(TextImport.NotText, files.importText(documents.put("null.txt", "a\u0000b".toByteArray()), ""))
        assertEquals(TextImport.NotText, files.importText("content://test/missing", ""))
    }

    @Test
    fun `a photo is shrunk to 2000 px at the long edge, turned the right way up, and keeps none of its metadata`() = runBlocking {
        val address = documents.put("IMG_0001.JPG", jpeg(3000, 1500, orientation = ExifInterface.ORIENTATION_ROTATE_90, gps = true))

        val added = files.importPhoto(address, count = 0) as AttachmentImport.Added

        assertEquals(AttachmentKind.Photo, added.attachment.kind)
        assertEquals("IMG_0001.jpg", added.attachment.name)
        val kept = checkNotNull(store.read(added.attachment.file))
        assertEquals(kept.size.toLong(), added.attachment.bytes)
        val bitmap = BitmapFactory.decodeByteArray(kept, 0, kept.size)
        assertEquals("turned: now upright", 1000, bitmap.width)
        assertEquals(2000, bitmap.height)
        val exif = ExifInterface(kept.inputStream())
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED))

        val small = files.importPhoto(documents.put("klein.png", png(300, 200)), count = 0) as AttachmentImport.Added
        val smallKept = checkNotNull(store.read(small.attachment.file))
        assertEquals("never made larger", 300, BitmapFactory.decodeByteArray(smallKept, 0, smallKept.size).width)
        assertEquals(AttachmentImport.Unreadable, files.importPhoto(documents.put("kein.jpg", "kein Bild".toByteArray()), count = 0))
    }

    @Test
    fun `a PDF is taken up to 10 MB and only if it opens, and an eleventh attachment of any kind is not`() = runBlocking {
        val pdf = pdf(1000)
        val added = files.importPdf(documents.put("Arbeitsblatt.pdf", pdf), count = 9) as AttachmentImport.Added
        assertArrayEquals(pdf, store.read(added.attachment.file))
        assertEquals("Arbeitsblatt.pdf", added.attachment.name)

        assertTrue(files.importPdf(documents.put("genau.pdf", pdf(LessonFiles.PDF_MAX_BYTES)), count = 0) is AttachmentImport.Added)
        assertEquals(AttachmentImport.TooLarge, files.importPdf(documents.put("gross.pdf", pdf(LessonFiles.PDF_MAX_BYTES + 1)), count = 0))
        assertEquals(AttachmentImport.Unreadable, files.importPdf(documents.put("falsch.pdf", "kein PDF".toByteArray()), count = 0))
        assertEquals(AttachmentImport.TooMany, files.importPdf(documents.put("elf.pdf", pdf), count = LessonFiles.MAX_ATTACHMENTS))
        assertEquals(AttachmentImport.TooMany, files.importPhoto(documents.put("elf.png", png(10, 10)), count = LessonFiles.MAX_ATTACHMENTS))
    }

    @Test
    fun `attachments lie only encrypted on the disk, and one cannot stand in for another`() = runBlocking {
        val secret = "GEHEIMER-INHALT-DER-STUNDE".toByteArray()
        val first = (files.importPdf(documents.put("a.pdf", "%PDF-".toByteArray() + secret), count = 0) as AttachmentImport.Added).attachment
        val second = (files.importPdf(documents.put("b.pdf", pdf(100)), count = 0) as AttachmentImport.Added).attachment

        val dir = File(checkNotNull(folderOf(first.file)))
        dir.listFiles().orEmpty().forEach { file ->
            assertFalse(file.name, String(file.readBytes(), Charsets.ISO_8859_1).contains("GEHEIMER-INHALT"))
        }
        File(dir, first.file).copyTo(File(dir, second.file), overwrite = true)
        assertNull("bound to its name", store.read(second.file))
        assertNull(store.read("../../database"))
    }

    @Test
    fun `a lesson saved takes its attachments in, in order, one taken out goes on saving, and all go with the lesson`() = runBlocking {
        val (language, teacher) = languageAndTeacher()
        val photo = import(files.importPhoto(documents.put("tafel.png", png(50, 50)), 0))
        val sheet = import(files.importPdf(documents.put("blatt.pdf", pdf(50)), 1))
        val id = (languages.save(LessonEditing.New, draft(language, teacher).copy(attachments = listOf(sheet, photo))) as Saving.Saved).id

        val editing = checkNotNull(languages.editing(id))
        assertEquals(listOf(sheet, photo), editing.original?.attachments)
        assertFalse(files.isStaged(sheet.file))

        // Taken out in the editor and left without saving: still there.
        files.discard(checkNotNull(editing.original).copy(attachments = listOf(photo)), editing.original)
        assertTrue(store.has(sheet.file))

        languages.save(editing, checkNotNull(editing.original).copy(attachments = listOf(photo)))
        assertEquals(listOf(photo), languages.editing(id)?.original?.attachments)
        assertFalse(store.has(sheet.file))

        languages.delete(id)
        assertFalse(store.has(photo.file))
    }

    @Test
    fun `an editor left unsaved leaves no attachments, and clearing up spares only what is saved or still being imported`() = runBlocking {
        val (language, teacher) = languageAndTeacher()
        val left = import(files.importPdf(documents.put("weg.pdf", pdf(50)), 0))
        files.discard(draft(language, teacher).copy(attachments = listOf(left)), saved = null)
        assertFalse(store.has(left.file))

        val saved = import(files.importPdf(documents.put("bleibt.pdf", pdf(50)), 0))
        languages.save(LessonEditing.New, draft(language, teacher).copy(attachments = listOf(saved)))
        val importing = import(files.importPdf(documents.put("gerade.pdf", pdf(50)), 0))
        val stray = store.write("übrig".toByteArray())

        languages.clearUpAttachments()

        assertTrue(store.has(saved.file))
        assertTrue(store.has(importing.file))
        assertFalse(store.has(stray))
        assertEquals("one let go of is not taken in", 0, savedCount(languages.save(LessonEditing.New, draft(language, teacher).copy(attachments = listOf(left)))))
    }

    @Test
    fun `a lesson with more than ten attachments is refused, and one is exported decrypted`() = runBlocking {
        val (language, teacher) = languageAndTeacher()
        val many = List(LessonFiles.MAX_ATTACHMENTS + 1) { import(files.importPdf(documents.put("$it.pdf", pdf(20)), 0)) }
        assertEquals(
            Saving.Refused(setOf(LessonProblem.TooManyAttachments)),
            languages.save(LessonEditing.New, draft(language, teacher).copy(attachments = many)),
        )

        assertTrue(files.export(many.first(), "content://export/blatt.pdf"))
        assertArrayEquals(pdf(20), documents.written["content://export/blatt.pdf"])
    }

    private suspend fun savedCount(saving: Saving<LessonProblem>): Int =
        languages.editing((saving as Saving.Saved).id)?.original?.attachments.orEmpty().size

    private fun folderOf(file: String): String? =
        File(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().filesDir.path)
            .walkTopDown().firstOrNull { it.name == file }?.parent

    private fun import(result: AttachmentImport) = (result as AttachmentImport.Added).attachment

    private suspend fun languageAndTeacher(): Pair<Long, Long> {
        val language = (languages.createLanguage("Arabisch") as Saving.Saved).id
        val teacher = (languages.saveTeacher(TeacherEditing.New, TeacherDraft("Amal", languageIds = setOf(language))) as Saving.Saved).id
        return language to teacher
    }

    private fun draft(language: Long, teacher: Long) = LessonDraft(LocalDate.parse("2026-11-12"), "60", language, teacher)

    private fun pdf(size: Int) = ("%PDF-1.7\n".toByteArray() + ByteArray(maxOf(0, size - 9)) { 'x'.code.toByte() }).copyOf(size)

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** A JPEG of [width] × [height], as the camera saved it turned by [orientation], with where it was taken if [gps]. */
    private fun jpeg(width: Int, height: Int, orientation: Int, gps: Boolean): ByteArray {
        val file = File.createTempFile("photo", ".jpg")
        file.outputStream().use {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                .compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            if (gps) {
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "52/1,31/1,12/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            }
            saveAttributes()
        }
        return file.readBytes().also { file.delete() }
    }
}
