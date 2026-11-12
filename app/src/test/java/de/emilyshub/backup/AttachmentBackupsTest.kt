package de.emilyshub.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.data.Saving
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.AttachmentRef
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDate

/**
 * The lessons' attachments in the backups (SPEC.md §10, §15): carried after the content, as many and as large as there
 * are, the nightly backup too; put back on restoring, in place of the app's own; older backups load without; and a
 * backup whose attachments were tampered with is refused, with nothing changed.
 */
@RunWith(AndroidJUnit4::class)
class AttachmentBackupsTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val backups = app.backups

    @Test
    fun `a backup carries the attachments, and restoring puts them back in place of what is there now`() {
        val (lesson, first, second) = lessonWithTwo()
        val file = exported()
        val later = runBlocking {
            app.languages.delete(lesson)
            app.languages.save(LessonEditing.New, draft(listOf(pdf("später", 30))))
            app.languages.editing(lessonIds().single())?.original?.attachments.orEmpty().single()
        }

        restore(file)

        val restored = runBlocking { app.languages.editing(lesson)?.original?.attachments }
        assertEquals(listOf(first, second), restored)
        assertArrayEquals(pdfBytes("Blatt", 40), content(first))
        assertArrayEquals(pdfBytes("Tafel", 50), content(second))
        assertNull("what came since is gone", content(later))
        assertEquals(listOf(lesson), lessonIds())
    }

    @Test
    fun `attachments that come to more than a backup's content are carried along all the same`() {
        val many = List(3) { pdf("groß-$it", LessonFiles.PDF_MAX_BYTES) }
        runBlocking { app.languages.save(LessonEditing.New, draft(many)) }
        val file = exported()
        assertTrue(file.size > BackupFile.MAX_BYTES)

        runBlocking { app.languages.delete(lessonIds().single()) }
        restore(file)

        val back = runBlocking { app.languages.editing(lessonIds().single())?.original?.attachments.orEmpty() }
        assertEquals(3, back.size)
        assertEquals(LessonFiles.PDF_MAX_BYTES, content(back.last())?.size)
    }

    @Test
    fun `the nightly backup carries the attachments too`() {
        val (_, first) = lessonWithTwo()
        runBlocking {
            backups.setPassword(PASSWORD)
            app.nightlyBackups.choose("content://ordner")
        }

        val nightly = app.backupFolder.files.values.single()

        assertArrayEquals(pdfBytes("Blatt", 40), BackupFile.open(nightly, PASSWORD).attachments.first())
        assertEquals(first.file, contentOf(nightly).getJSONArray("attachments").getJSONArray(0).getString(0))
    }

    @Test
    fun `a backup of the format before attachments still loads, and leaves none`() {
        lessonWithTwo()
        val content = contentOf(exported())
        content.remove("attachments")
        content.getJSONObject("tables").getJSONObject("lesson_attachments").put("rows", JSONArray())
        val older = BackupFile.seal1(content.toString().toByteArray(), key())

        restore(older)

        assertEquals(emptyList<AttachmentRef>(), runBlocking { app.languages.editing(lessonIds().single())?.original?.attachments })
    }

    @Test
    fun `a backup whose attachments were changed, dropped, swapped or left out of the list is refused, and nothing changes`() {
        val (lesson, first, second) = lessonWithTwo()
        val file = exported()
        val records = recordsOf(file)

        val tampered = listOf(
            "a byte changed" to file.copyOf().also { it[records[1] + 40] = (it[records[1] + 40] + 1).toByte() },
            "cut off" to file.copyOf(records[2]),
            "swapped" to swap(file, records),
            "without its end" to file.copyOf(records[3]),
            "listed larger" to sealedWith(file) { it.getJSONArray("attachments").getJSONArray(0).put(1, 41) },
            "not listed" to sealedWith(file, attachments = 1) { it.put("attachments", JSONArray().put(it.getJSONArray("attachments").get(1))) },
        )
        for ((how, damaged) in tampered) {
            try {
                open(damaged)
                fail("$how: opened")
            } catch (e: BackupException) {
                assertEquals(how, BackupProblem.Damaged, e.problem)
            }
        }

        assertEquals(listOf(first, second), runBlocking { app.languages.editing(lesson)?.original?.attachments })
        assertArrayEquals(pdfBytes("Blatt", 40), content(first))
    }

    @Test
    fun `an attachment that cannot be read is left out with its row, and the backup is made all the same`() {
        val (lesson, first, second) = lessonWithTwo()
        File(File(app.filesDir, "attachments"), first.file).delete()

        val file = exported()
        restore(file)

        assertEquals(listOf(second), runBlocking { app.languages.editing(lesson)?.original?.attachments })
    }

    @Test
    fun `a backup opened before another is refused on restoring, and nothing changes`() {
        val (lesson, first, second) = lessonWithTwo()
        val file = exported()
        val earlier = open(file)
        runBlocking { app.languages.delete(lesson) }
        open(exported())

        try {
            runBlocking { backups.restore(earlier) }
            fail("restored")
        } catch (e: BackupException) {
            assertEquals(BackupProblem.Damaged, e.problem)
        }
        assertEquals(emptyList<Long>(), lessonIds())
        restore(file)
        assertEquals(listOf(first, second), runBlocking { app.languages.editing(lesson)?.original?.attachments })
    }

    @Test
    fun `files a restore broke off left aside are put in place on the next start`() {
        val (lesson, first) = lessonWithTwo()
        val file = exported()
        open(file)
        File(File(app.filesDir, "attachments"), first.file).delete()

        runBlocking { app.languages.clearUpAttachments() }

        assertArrayEquals(pdfBytes("Blatt", 40), content(first))
        assertEquals(2, runBlocking { app.languages.editing(lesson)?.original?.attachments?.size })
    }

    /** A lesson with two PDFs, "Blatt" of 40 bytes and "Tafel" of 50. */
    private fun lessonWithTwo(): Triple<Long, AttachmentRef, AttachmentRef> = runBlocking {
        val first = pdf("Blatt", 40)
        val second = pdf("Tafel", 50)
        val id = (app.languages.save(LessonEditing.New, draft(listOf(first, second))) as Saving.Saved).id
        Triple(id, first, second)
    }

    private fun draft(attachments: List<AttachmentRef>): LessonDraft = runBlocking {
        val language = app.languages.languages().first().firstOrNull()?.language?.id
            ?: (app.languages.createLanguage("Arabisch") as Saving.Saved).id
        val teacher = app.languages.teachers().first().firstOrNull()?.teacher?.id
            ?: (app.languages.saveTeacher(TeacherEditing.New, TeacherDraft("Amal", languageIds = setOf(language))) as Saving.Saved).id
        LessonDraft(LocalDate.parse("2026-10-20"), "60", language, teacher, attachments = attachments)
    }

    private fun pdf(name: String, size: Int): AttachmentRef = runBlocking {
        val address = app.lessonDocuments.put("$name.pdf", pdfBytes(name, size))
        (app.lessonFiles.importPdf(address, count = 0) as AttachmentImport.Added).attachment
    }

    private fun pdfBytes(name: String, size: Int) = ("%PDF-$name".toByteArray() + ByteArray(size) { 'x'.code.toByte() }).copyOf(size)

    private fun content(attachment: AttachmentRef) = runBlocking { app.lessonFiles.content(attachment) }

    private fun lessonIds(): List<Long> = runBlocking {
        app.languages.page(app.languages.languages().first().single().language.id)
            .first()?.lessons.orEmpty().map { it.lesson.id }
    }

    private fun exported(): ByteArray {
        runBlocking { if (!backups.hasPassword()) backups.setPassword(PASSWORD) }
        val file = ByteArrayOutputStream()
        runBlocking { backups.export { file } }
        return file.toByteArray()
    }

    private fun open(file: ByteArray): Backup = runBlocking { backups.open({ ByteArrayInputStream(file) }, PASSWORD) }

    private fun restore(file: ByteArray) = runBlocking { backups.restore(open(file)) }

    private fun contentOf(file: ByteArray) = JSONObject(String(BackupFile.open(file, PASSWORD).plaintext))

    private fun key() = BackupKey.derive(PASSWORD, iterations = 1_000)

    /** [file] sealed anew with its content as [change] makes it, and its first [attachments] after it, or all. */
    private fun sealedWith(file: ByteArray, attachments: Int? = null, change: (JSONObject) -> Unit): ByteArray {
        val opened = BackupFile.open(file, PASSWORD)
        val content = JSONObject(String(opened.plaintext)).also(change)
        val carried = attachments?.let { opened.attachments.drop(it) } ?: opened.attachments
        return BackupFile.seal(content.toString().toByteArray(), key(), carried)
    }

    /** Where each record of a backup of format 2 begins, the last one's end after them. */
    private fun recordsOf(file: ByteArray): List<Int> = buildList {
        var at = HEADER_BYTES
        while (at < file.size) {
            add(at)
            at += 1 + 4 + 12 + ByteBuffer.wrap(file, at + 1, 4).int
        }
        add(at)
    }

    /** [file] with its two attachments' records in each other's place. */
    private fun swap(file: ByteArray, records: List<Int>): ByteArray {
        val first = file.copyOfRange(records[1], records[2])
        val second = file.copyOfRange(records[2], records[3])
        return file.copyOf(records[1]) + second + first + file.copyOfRange(records[3], file.size)
    }

    private companion object {
        const val PASSWORD = "Rhabarberkuchen"

        /** "EMHB", the format, the iterations, the salt and the password check. */
        const val HEADER_BYTES = 4 + 1 + 4 + 16 + 16
    }
}
