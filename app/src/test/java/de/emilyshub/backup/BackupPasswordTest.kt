package de.emilyshub.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.routine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The backup password changed (SPEC.md §10; #111): with the old one, or, forgotten, anew; each backup opens with the
 * password it was made with, and those made after with the new one.
 */
@RunWith(AndroidJUnit4::class)
class BackupPasswordTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val backups = app.backups

    @Test
    fun `changed with the old password, backups after it open with the new one, those before with the old`() {
        app.routine("Vitamine", "07:30")
        runBlocking { backups.setPassword(OLD) }
        val before = exported()

        assertTrue(runBlocking { backups.changePassword(OLD, NEW) })
        val after = exported()

        assertEquals(1, open(before, OLD).summary.routines)
        assertEquals(1, open(after, NEW).summary.routines)
        assertWrongPassword { open(after, OLD) }
        assertWrongPassword { open(before, NEW) }
    }

    @Test
    fun `a wrong old password changes nothing`() {
        runBlocking { backups.setPassword(OLD) }

        assertFalse(runBlocking { backups.changePassword("Lila und Flieder", NEW) })

        open(exported(), OLD)
        assertWrongPassword { open(exported(), NEW) }
    }

    @Test
    fun `a new password with a problem is refused, and the old one stays`() {
        runBlocking { backups.setPassword(OLD) }

        assertThrows(IllegalArgumentException::class.java) { runBlocking { backups.changePassword(OLD, "Flieder") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { backups.forgotPassword("Flieder") } }

        open(exported(), OLD)
    }

    @Test
    fun `forgotten, a new password is set without the old one, and later backups open with it`() {
        runBlocking { backups.setPassword(OLD) }
        val before = exported()

        runBlocking { backups.forgotPassword(NEW) }

        open(exported(), NEW)
        // The file was made with the old one, and still opens only with it.
        open(before, OLD)
        assertWrongPassword { open(before, NEW) }
    }

    @Test
    fun `without a password set there is nothing to change`() {
        assertThrows(IllegalStateException::class.java) { runBlocking { backups.changePassword(OLD, NEW) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { backups.forgotPassword(NEW) } }
    }

    @Test
    fun `each new password has a salt of its own`() {
        runBlocking { backups.setPassword(OLD) }
        val first = saltOf(exported())

        runBlocking { backups.changePassword(OLD, OLD) }

        assertFalse(first.contentEquals(saltOf(exported())))
    }

    private fun exported(): ByteArray = ByteArrayOutputStream().also { out -> runBlocking { backups.export { out } } }.toByteArray()

    private fun open(file: ByteArray, password: String): Backup =
        runBlocking { backups.open({ ByteArrayInputStream(file) }, password) }

    /** The salt in the file's header, after "EMHB", its format and the iterations. */
    private fun saltOf(file: ByteArray): ByteArray = file.copyOfRange(SALT_AT, SALT_AT + BackupKey.SALT_BYTES)

    private fun assertWrongPassword(block: () -> Unit) {
        val e = assertThrows(BackupException::class.java, block)
        assertEquals(BackupProblem.WrongPassword, e.problem)
    }

    private companion object {
        const val OLD = "Flieder und Lila"
        const val NEW = "Lavendel im Juni"
        const val SALT_AT = 4 + 1 + 4
    }
}
