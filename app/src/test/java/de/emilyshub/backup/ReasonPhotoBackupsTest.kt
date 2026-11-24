package de.emilyshub.backup

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.data.Saving
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import de.emilyshub.warum.ReasonDraft
import de.emilyshub.warum.Reasons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * The photos of Mein Warum's reasons in the backups (SPEC.md §10, §18): carried along with the lessons' attachments,
 * by night too, put back on restoring, and a backup whose photos do not fit its reasons is refused.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReasonPhotoBackupsTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val backups = app.backups

    @Test
    fun `a backup carries the photos, and restoring puts them back in place of what is there now`() {
        val photo = reason("Das Meer", withPhoto = true)
        reason("Lina", withPhoto = false)
        val kept = photoOf("Das Meer")
        val file = exported()
        val later = runBlocking {
            app.reasons.board().first().forEach { app.reasons.delete(it.id) }
            checkNotNull(reason("Später", withPhoto = true))
        }

        restore(file)

        val board = runBlocking { app.reasons.board().first() }
        assertEquals(listOf("Lina", "Das Meer"), board.map { it.text })
        assertEquals(listOf(null, photo), board.map { it.photo })
        assertArrayEquals(kept, photoOf("Das Meer"))
        assertNull("what came since is gone", runBlocking { app.reasons.photo(later) })
    }

    @Test
    fun `the nightly backup carries the photos too`() {
        val photo = reason("Das Meer", withPhoto = true)
        runBlocking {
            backups.setPassword(PASSWORD)
            app.nightlyBackups.choose("content://ordner")
        }

        val nightly = app.backupFolder.files.values.single()

        assertArrayEquals(photoOf("Das Meer"), BackupFile.open(nightly, PASSWORD).attachments.single())
        assertEquals(photo, contentOf(nightly).getJSONArray("attachments").getJSONArray(0).getString(0))
    }

    @Test
    fun `a backup whose photos do not fit its reasons is refused, and nothing changes`() {
        reason("Das Meer", withPhoto = true)
        val file = exported()

        val tampered = listOf(
            "photo not carried" to sealedWith(file, carried = 0) { it.put("attachments", JSONArray()) },
            "another photo" to sealedWith(file) { it.setPhoto("${UUID.randomUUID()}.att") },
            "photo of none" to sealedWith(file) { it.setPhoto(null) },
            "over a hundred" to overAHundred(file),
        )
        for ((how, damaged) in tampered) {
            try {
                open(damaged)
                fail("$how: opened")
            } catch (e: BackupException) {
                assertEquals(how, BackupProblem.Damaged, e.problem)
            }
        }
        assertEquals(listOf("Das Meer"), runBlocking { app.reasons.board().first() }.map { it.text })
    }

    /** [file] with [Reasons.MAX_PHOTOS] more reasons with a photo, each carried as it should be. */
    private fun overAHundred(file: ByteArray): ByteArray {
        val opened = BackupFile.open(file, PASSWORD)
        val content = JSONObject(String(opened.plaintext))
        val listed = content.getJSONArray("attachments")
        val photos = List(Reasons.MAX_PHOTOS) { "${UUID.randomUUID()}.att" }
        photos.forEachIndexed { index, photo ->
            content.reasonRows().put(JSONArray().put(1000 + index).put("").put(index).put(photo))
            listed.put(JSONArray().put(photo).put(1))
        }
        val carried = opened.attachments + photos.map { byteArrayOf(1) }
        return BackupFile.seal(content.toString().toByteArray(), BackupKey.derive(PASSWORD, iterations = 1_000), carried)
    }

    @Test
    fun `a backup whose reason has the photo of a lesson's attachment is refused`() {
        reason("Das Meer", withPhoto = true)
        runBlocking {
            val language = (app.languages.createLanguage("Arabisch") as Saving.Saved).id
            val teacher = (app.languages.saveTeacher(TeacherEditing.New, TeacherDraft("Amal", languageIds = setOf(language))) as Saving.Saved).id
            val pdf = (app.lessonFiles.importPdf(app.lessonDocuments.put("Blatt.pdf", "%PDF-Blatt".toByteArray()), 0) as AttachmentImport.Added).attachment
            app.languages.save(LessonEditing.New, LessonDraft(LocalDate.parse("2026-10-20"), "60", language, teacher, attachments = listOf(pdf)))
        }
        val file = exported()
        val opened = BackupFile.open(file, PASSWORD)
        val content = JSONObject(String(opened.plaintext))
        val listed = content.getJSONArray("attachments")
        val lessons = content.getJSONObject("tables").getJSONObject("lesson_attachments")
        val lessonsFile = lessons.getJSONArray("rows").getJSONArray(0).getString(lessons.getJSONArray("columns").let { c -> (0 until c.length()).first { c.getString(it) == "file" } })
        // The reason's own photo dropped, it points at the lesson's file, carried once.
        val photoAt = (0 until listed.length()).first { listed.getJSONArray(it).getString(0) != lessonsFile }
        content.setPhoto(lessonsFile)
        listed.remove(photoAt)
        val tampered = BackupFile.seal(
            content.toString().toByteArray(),
            BackupKey.derive(PASSWORD, iterations = 1_000),
            opened.attachments.filterIndexed { index, _ -> index != photoAt },
        )

        try {
            open(tampered)
            fail("opened")
        } catch (e: BackupException) {
            assertEquals(BackupProblem.Damaged, e.problem)
        }
    }

    @Test
    fun `a Grund whose photo cannot be read keeps its text in the backup, and one with a photo alone is left out`() {
        val sea = checkNotNull(reason("Das Meer", withPhoto = true))
        val alone = checkNotNull(reason("", withPhoto = true))
        reason("Lina", withPhoto = false)
        listOf(sea, alone).forEach { File(File(app.filesDir, "attachments"), it).delete() }

        val file = exported()
        restore(file)

        val board = runBlocking { app.reasons.board().first() }
        assertEquals(listOf("Lina", "Das Meer"), board.map { it.text })
        assertEquals(listOf(null, null), board.map { it.photo })
    }

    /** A reason saying [text], with a photo of its own if [withPhoto]: the name the photo is kept under. */
    private fun reason(text: String, withPhoto: Boolean): String? = runBlocking {
        val photo = if (withPhoto) {
            val out = ByteArrayOutputStream()
            Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }.compress(Bitmap.CompressFormat.PNG, 100, out)
            val address = app.lessonDocuments.put("$text.png", out.toByteArray())
            (app.reasons.importPhoto(address, null) as AttachmentImport.Added).attachment.file
        } else {
            null
        }
        check(app.reasons.save(null, ReasonDraft(text, photo)) is Saving.Saved)
        photo
    }

    private fun photoOf(text: String): ByteArray? = runBlocking {
        app.reasons.board().first().single { it.text == text }.photo?.let { app.reasons.photo(it) }
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

    /** [file] sealed anew with its content as [change] makes it, and its first [carried] attachments after it, or all. */
    private fun sealedWith(file: ByteArray, carried: Int? = null, change: (JSONObject) -> Unit): ByteArray {
        val opened = BackupFile.open(file, PASSWORD)
        val content = JSONObject(String(opened.plaintext)).also(change)
        val attachments = carried?.let { opened.attachments.take(it) } ?: opened.attachments
        return BackupFile.seal(content.toString().toByteArray(), BackupKey.derive(PASSWORD, iterations = 1_000), attachments)
    }

    private fun JSONObject.reasonRows(): JSONArray = getJSONObject("tables").getJSONObject("reasons").getJSONArray("rows")

    /** The photo of the first reason set to [photo]. */
    private fun JSONObject.setPhoto(photo: String?) {
        val columns = getJSONObject("tables").getJSONObject("reasons").getJSONArray("columns")
        val index = (0 until columns.length()).first { columns.getString(it) == "photo" }
        reasonRows().getJSONArray(0).put(index, photo ?: JSONObject.NULL)
    }

    private companion object {
        const val PASSWORD = "Rhabarberkuchen"
    }
}
