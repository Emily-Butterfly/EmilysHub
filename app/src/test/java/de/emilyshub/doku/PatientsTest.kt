package de.emilyshub.doku

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Doku's patients (SPEC.md §14): their fields, the start page's order, the archive, deleting and the search. */
@RunWith(AndroidJUnit4::class)
class PatientsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val doku = Doku(
        keyFile = File(context.noBackupFilesDir, "patients-test.key"),
        fingerprint = FakeFingerprint(),
        openDatabase = { testDokuDatabase(context) },
        clipboard = DokuClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    ).also { runBlocking { it.load(); it.setUp("geheim-geheim") } }

    private val patients = Patients(doku, clock, CoroutineScope(Dispatchers.Unconfined))

    @After
    fun closeDatabase() {
        doku.lock()
        context.deleteDatabase(DokuDatabase.NAME)
    }

    private fun add(name: String, birthYear: String = ""): Long = runBlocking { checkNotNull(patients.create(PatientDraft(name, birthYear))) }

    private fun later(minutes: Long = 1) {
        clock.now = clock.now.plusSeconds(minutes * 60)
    }

    private fun on(date: String) {
        clock.now = LocalDate.parse(date).atTime(9, 0).atZone(clock.zone).toInstant()
    }

    private fun get(id: Long) = runBlocking { checkNotNull(patients.get(id)) }

    @Test
    fun `a patient has a name or initials, a year of birth if given, and empty texts`() {
        val id = add("  Frau   M. ", "1983")

        val patient = get(id)

        assertEquals("Frau M.", patient.name)
        assertEquals(1983, patient.birthYear)
        PatientField.entries.forEach { assertEquals("", patient.textOf(it)) }
        assertNull(patient.closedOn)
    }

    @Test
    fun `the name is required and at most 80 characters`() {
        assertEquals(setOf(PatientProblem.NameMissing), PatientDraft("  ​ ").problems(2026))
        assertEquals(setOf(PatientProblem.NameTooLong), PatientDraft("x".repeat(81)).problems(2026))
        assertEquals(emptySet<PatientProblem>(), PatientDraft("x".repeat(80)).problems(2026))
        assertThrows(IllegalArgumentException::class.java) { add("") }
    }

    @Test
    fun `the year of birth lies from 1900 to the year it is`() {
        assertEquals(setOf(PatientProblem.BirthYearInvalid), PatientDraft("K.", "1899").problems(2026))
        assertEquals(setOf(PatientProblem.BirthYearInvalid), PatientDraft("K.", "2027").problems(2026))
        assertEquals(setOf(PatientProblem.BirthYearInvalid), PatientDraft("K.", "83").problems(2026))
        assertEquals(setOf(PatientProblem.BirthYearInvalid), PatientDraft("K.", "19a3").problems(2026))
        assertEquals(emptySet<PatientProblem>(), PatientDraft("K.", "1900").problems(2026))
        assertEquals(emptySet<PatientProblem>(), PatientDraft("K.", " 2026 ").problems(2026))
        assertNull(PatientDraft("K.", "").cleanedBirthYear())
    }

    @Test
    fun `a text is set with its trailing spaces gone, and at most 40000 characters`() {
        val id = add("Frau M.")

        runBlocking { patients.setText(id, PatientField.Diagnosis, "  Schulter **links**  \n") }

        assertEquals("  Schulter **links**", get(id).diagnosis)
        assertTrue(Patient.textTooLong("x".repeat(40_001)))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { patients.setText(id, PatientField.Other, "x".repeat(40_001)) }
        }
    }

    @Test
    fun `name and year of birth are changed`() {
        val id = add("Frau M.", "1983")

        runBlocking { patients.rename(id, PatientDraft("Frau Me.", "")) }

        assertEquals("Frau Me.", get(id).name)
        assertNull(get(id).birthYear)
    }

    @Test
    fun `by default the start page lists the active patients added last first, as none has had an appointment`() {
        add("Herr A.")
        later()
        add("Frau B.")
        later()
        add("Frau C.")

        val rows = runBlocking { checkNotNull(patients.active(patients.order())) }

        assertEquals(listOf("Frau C.", "Frau B.", "Herr A."), rows.map { it.name })
        assertTrue(rows.all { it.lastSeen == null })
    }

    @Test
    fun `by name, in German order, and the choice is kept`() {
        add("Zoe")
        add("Ärztin Ö.")
        add("Anna")

        runBlocking { patients.setOrder(PatientOrder.Name) }

        assertEquals(PatientOrder.Name, runBlocking { patients.order() })
        assertEquals(listOf("Anna", "Ärztin Ö.", "Zoe"), runBlocking { checkNotNull(patients.active(PatientOrder.Name)) }.map { it.name })
    }

    @Test
    fun `closing moves a patient to the archive with today, and only to read`() {
        val id = add("Frau M.")
        add("Herr K.")

        runBlocking { patients.close(id) }
        runBlocking { patients.setText(id, PatientField.Diagnosis, "geändert") }
        runBlocking { patients.rename(id, PatientDraft("anders")) }

        assertEquals(listOf("Herr K."), runBlocking { checkNotNull(patients.active(PatientOrder.Name)) }.map { it.name })
        assertEquals(
            listOf(ArchivedPatient(id, "Frau M.", LocalDate.parse("2026-10-23"), overdue = false)),
            runBlocking { patients.archive() },
        )
        assertEquals("", get(id).diagnosis)
        assertEquals("Frau M.", get(id).name)
    }

    @Test
    fun `the archive lists the one closed last first`() {
        val first = add("Erste")
        val second = add("Zweite")
        runBlocking { patients.close(first) }
        on("2026-10-30")
        runBlocking { patients.close(second) }

        assertEquals(listOf("Zweite", "Erste"), runBlocking { checkNotNull(patients.archive()) }.map { it.name })
    }

    @Test
    fun `restoring makes a patient active again`() {
        val id = add("Frau M.")
        runBlocking { patients.close(id) }

        runBlocking { patients.restore(id) }

        assertNull(get(id).closedOn)
        assertEquals(listOf("Frau M."), runBlocking { checkNotNull(patients.active(PatientOrder.Name)) }.map { it.name })
        assertEquals(emptyList<ArchivedPatient>(), runBlocking { patients.archive() })
    }

    @Test
    fun `deleting for good removes a patient with all that belongs to them`() {
        val id = add("Frau M.")
        val other = add("Herr K.")

        runBlocking { patients.delete(id) }

        assertNull(runBlocking { patients.get(id) })
        assertEquals(other, get(other).id)
    }

    @Test
    fun `the Doku's database overwrites what is deleted`() {
        val secureDelete = runBlocking {
            doku.withDatabase { database -> database.query("PRAGMA secure_delete", null).use { it.moveToFirst(); it.getInt(0) } }
        }

        assertEquals(1, secureDelete)
    }

    @Test
    fun `a patient counts as overdue for deleting from exactly twelve months after closing`() {
        val id = add("Frau M.")
        runBlocking { patients.close(id) }

        on("2027-10-22")
        assertEquals(0, runBlocking { patients.overdueCount() })

        on("2027-10-23")
        assertEquals(1, runBlocking { patients.overdueCount() })
        assertTrue(runBlocking { checkNotNull(patients.archive()) }.single().overdue)
    }

    @Test
    fun `the overdue ones are deleted together, the others stay`() {
        val old = add("Alt 1")
        val older = add("Alt 2")
        runBlocking { patients.close(old) }
        runBlocking { patients.close(older) }
        on("2027-01-10")
        val recent = add("Neu")
        runBlocking { patients.close(recent) }
        val active = add("Aktiv")
        on("2027-10-23")

        val shown = runBlocking { checkNotNull(patients.archive()) }.filter(ArchivedPatient::overdue).map(ArchivedPatient::id)
        assertEquals(2, runBlocking { patients.deleteOverdue(shown) })

        assertEquals(listOf("Neu"), runBlocking { checkNotNull(patients.archive()) }.map { it.name })
        assertEquals(active, get(active).id)
    }

    @Test
    fun `only the overdue ones shown are deleted, not those overdue since`() {
        val old = add("Alt")
        runBlocking { patients.close(old) }
        on("2027-01-10")
        val later = add("Später")
        runBlocking { patients.close(later) }
        on("2027-10-23")
        val shown = runBlocking { checkNotNull(patients.archive()) }.filter(ArchivedPatient::overdue).map(ArchivedPatient::id)
        // The dialog stays open into the day "Später" turns overdue too.
        on("2028-01-10")

        assertEquals(1, runBlocking { patients.deleteOverdue(shown) })

        assertEquals(listOf("Später"), runBlocking { checkNotNull(patients.archive()) }.map { it.name })
    }

    @Test
    fun `patients restored meanwhile are not deleted with the overdue ones`() {
        val old = add("Alt")
        runBlocking { patients.close(old) }
        on("2027-10-23")
        val shown = runBlocking { checkNotNull(patients.archive()) }.map(ArchivedPatient::id)
        runBlocking { patients.restore(old) }

        assertEquals(0, runBlocking { patients.deleteOverdue(shown) })

        assertEquals(old, get(old).id)
    }

    @Test
    fun `by the latest entry, a patient's last appointment or the day they were added, a newer one first`() {
        val older = add("Älter")
        on("2026-10-30")
        val newer = add("Neuer")

        assertEquals(listOf(newer, older), runBlocking { checkNotNull(patients.active(PatientOrder.LastSeen)) }.map { it.id })
    }

    @Test
    fun `the search finds patients by name and their texts, active and archived alike, every word`() {
        val shoulder = add("Frau M.")
        runBlocking { patients.setText(shoulder, PatientField.Diagnosis, "Impingement der Schulter") }
        val knee = add("Herr K.")
        runBlocking { patients.setText(knee, PatientField.Goals, "Treppensteigen ohne Schmerz") }
        runBlocking { patients.close(knee) }
        add("Frau S.")

        assertEquals(listOf(PatientHit(shoulder, "Frau M.", archived = false)), runBlocking { patients.search("SCHULTER") })
        assertEquals(listOf(PatientHit(knee, "Herr K.", archived = true)), runBlocking { patients.search("treppen schmerz") })
        assertEquals(listOf("Frau M.", "Frau S."), runBlocking { patients.search("frau") }.map { it.name })
        assertEquals(emptyList<PatientHit>(), runBlocking { patients.search("schulter knie") })
        assertEquals(emptyList<PatientHit>(), runBlocking { patients.search("  ") })
    }

    @Test
    fun `once the Doku is locked, nothing is read or written`() {
        val id = add("Frau M.")
        doku.lock()

        assertNull(runBlocking { patients.get(id) })
        assertNull(runBlocking { patients.active(PatientOrder.Name) })
        assertNull(runBlocking { patients.create(PatientDraft("Herr K.")) })
        assertEquals(emptyList<PatientHit>(), runBlocking { patients.search("frau") })
    }
}
