package de.emilyshub.vault

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.Languages
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import de.emilyshub.languages.testAttachmentStore
import de.emilyshub.warum.ReasonDraft
import de.emilyshub.warum.Reasons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Ablage (CONTEXT.md; deepening plan 6, step 2): the encrypted files of the Sessions' Anhänge and the Gründe's
 * photos, which it knows by its owners; a file is claimed and released in a change, which takes effect only once the
 * change has gone through.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VaultTest {

    private val database = inMemoryDatabase()
    private val store = testAttachmentStore()
    private val documents = FakeLessonDocuments()
    private val vault = Vault(store, documents, Dispatchers.Unconfined)
    private val files = LessonFiles(vault, Dispatchers.Unconfined)
    private val clock = TestClock(Instant.parse("2026-11-24T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val languages = Languages({ database }, clock, files, Dispatchers.Unconfined)
    private val reasons = Reasons({ database }, vault, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the Ablage knows the files of both its owners, the Sessions and the reasons of Mein Warum`() = runBlocking {
        val pdf = import(files.importPdf(documents.put("Blatt.pdf", "%PDF-Blatt".toByteArray()), 0))
        val (language, teacher) = languageAndTeacher()
        languages.save(LessonEditing.New, LessonDraft(LocalDate.parse("2026-11-20"), "45", language, teacher, attachments = listOf(pdf)))
        val photo = import(reasons.importPhoto(documents.put("Meer.png", png()), null)).file
        reasons.save(null, ReasonDraft("Das Meer", photo))

        assertEquals(setOf(pdf.file, photo), vault.owned(database))
    }

    @Test
    fun `a file is free to claim while it lies in the Ablage and no owner has it`() = runBlocking {
        val photo = import(reasons.importPhoto(documents.put("Meer.png", png()), null)).file

        vault.changing(database) { assertTrue(isFree(photo)) }
        reasons.save(null, ReasonDraft("Das Meer", photo))
        vault.changing(database) {
            assertFalse("a Grund's now", isFree(photo))
            assertFalse("not in the Ablage", isFree("0b8c3f3e-1d2a-4c5b-9e6f-7a8b9c0d1e2f.att"))
        }
    }

    @Test
    fun `what a change takes is no longer staged, and what it releases is deleted, once it has gone through`() = runBlocking {
        val kept = vault.keep(byteArrayOf(1, 2, 3))
        val old = vault.keep(byteArrayOf(4, 5, 6))
        assertTrue(vault.isStaged(kept))

        vault.changing(database) {
            take(kept)
            release(old)
        }

        assertFalse(vault.isStaged(kept))
        assertTrue(store.has(kept))
        assertFalse(store.has(old))
    }

    @Test
    fun `a change that fails takes and releases nothing`() = runBlocking {
        val kept = vault.keep(byteArrayOf(1, 2, 3))
        val old = vault.keep(byteArrayOf(4, 5, 6))

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                vault.changing(database) {
                    take(kept)
                    release(old)
                    error("the transaction failed")
                }
            }
        }

        assertTrue(vault.isStaged(kept))
        assertTrue(store.has(old))
    }

    @Test
    fun `clearing up keeps what the owners have and what is staged, and lets go of the rest`() = runBlocking {
        val photo = import(reasons.importPhoto(documents.put("Meer.png", png()), null)).file
        reasons.save(null, ReasonDraft("Das Meer", photo))
        val staged = vault.keep(byteArrayOf(1))
        // As on a later start, with nothing staged in memory: what a crash left.
        val again = Vault(store, documents, Dispatchers.Unconfined)
        val left = store.write(byteArrayOf(2))

        again.clearUp(database, olderThan = java.time.Duration.ZERO)

        assertTrue(store.has(photo))
        assertFalse("staged only in the vault before", store.has(staged))
        assertFalse(store.has(left))
    }

    private suspend fun languageAndTeacher(): Pair<Long, Long> {
        val language = (languages.createLanguage("Arabisch") as Saving.Saved).id
        val teacher = (languages.saveTeacher(TeacherEditing.New, TeacherDraft("Amal", languageIds = setOf(language))) as Saving.Saved).id
        return language to teacher
    }

    private fun import(result: AttachmentImport) = (result as AttachmentImport.Added).attachment

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(60, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
}
