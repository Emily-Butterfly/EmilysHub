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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The patients' appointments (SPEC.md §14): their order and preview line, the template that fills in new ones, saving
 * as the Doku locks, deleting, the last appointment on the start page and the search in their texts.
 */
@RunWith(AndroidJUnit4::class)
class AppointmentsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val doku = Doku(
        keyFile = File(context.noBackupFilesDir, "appointments-test.key"),
        databaseFile = context.getDatabasePath(DokuDatabase.NAME),
        fingerprint = FakeFingerprint(),
        openDatabase = { testDokuDatabase(context) },
        clipboard = DokuClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    ).also { runBlocking { it.load(); it.setUp(PASSWORD) } }

    private val patients = Patients(doku, clock, CoroutineScope(Dispatchers.Unconfined))
    private val appointments = Appointments(doku, clock, CoroutineScope(Dispatchers.Unconfined))

    @After
    fun closeDatabase() {
        doku.lock()
        context.deleteDatabase(DokuDatabase.NAME)
        File(context.noBackupFilesDir, "appointments-test.key").delete()
    }

    private fun patient(name: String = "Frau M."): Long = runBlocking { checkNotNull(patients.create(PatientDraft(name))) }

    private fun appointment(patientId: Long, date: String? = null, time: String? = null, text: String? = null): Long = runBlocking {
        val id = checkNotNull(appointments.create(patientId))
        date?.let { appointments.setDate(id, LocalDate.parse(it)) }
        time?.let { appointments.setTime(id, LocalTime.parse(it)) }
        text?.let { appointments.setText(id, it) }
        id
    }

    private fun get(id: Long) = runBlocking { checkNotNull(appointments.get(id)) }

    private fun rows(patientId: Long) = runBlocking { checkNotNull(appointments.of(patientId)) }

    /** The page of appointment [id] as it closes, its text as it was opened, unless to [keep]. */
    private fun close(id: Long, keep: Boolean = false, turning: Boolean = false) {
        val text = get(id).text
        appointments.draft(id, text, text, keep).close(turning)
    }

    @Test
    fun `a new appointment is today, without a time and, without a template, empty`() {
        val id = appointment(patient())

        val appointment = get(id)

        assertEquals(LocalDate.parse("2026-10-23"), appointment.date)
        assertNull(appointment.time)
        assertEquals("", appointment.text)
        assertEquals(listOf(AppointmentRow(id, LocalDate.parse("2026-10-23"), null, preview = null)), rows(appointment.patientId))
    }

    @Test
    fun `date, time and text are changed at any time, the date also to one to come`() {
        val id = appointment(patient())

        runBlocking {
            appointments.setDate(id, LocalDate.parse("2026-12-01"))
            appointments.setTime(id, LocalTime.parse("09:30"))
            appointments.setText(id, "Mobilisation  \n")
        }
        assertEquals(Appointment(id, get(id).patientId, LocalDate.parse("2026-12-01"), LocalTime.parse("09:30"), "Mobilisation"), get(id))

        runBlocking { appointments.setTime(id, null) }
        assertNull(get(id).time)
    }

    @Test
    fun `the text takes at most 40000 characters`() {
        val id = appointment(patient())

        assertThrows(IllegalArgumentException::class.java) { runBlocking { appointments.setText(id, "x".repeat(40_001)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { appointments.setTemplate("x".repeat(40_001)) } }
    }

    @Test
    fun `the latest first, on the same day by time, those without a time last`() {
        val patient = patient()
        val older = appointment(patient, date = "2026-10-20", time = "18:00")
        val noTime = appointment(patient, date = "2026-10-22")
        val morning = appointment(patient, date = "2026-10-22", time = "08:00")
        val evening = appointment(patient, date = "2026-10-22", time = "17:15")
        val toCome = appointment(patient, date = "2026-11-02")
        appointment(patient("Herr K."), date = "2026-10-21")

        assertEquals(listOf(toCome, evening, morning, noTime, older), rows(patient).map { it.id })
    }

    @Test
    fun `the preview is the first line that is no heading, without its Markdown`() {
        assertEquals("Schulter links frei beweglich", previewOf("\n# Befund\n\n  **Schulter** links _frei_ beweglich  \nmehr"))
        assertEquals("Wärme, dann Dehnen", previewOf("## Maßnahmen\n- Wärme, dann `Dehnen`"))
        assertEquals("Zitat", previewOf("---\n> Zitat"))
    }

    @Test
    fun `the preview reads code as written, not as Markdown`() {
        assertEquals("# kein Titel", previewOf("# Befund\n```\n\n# kein Titel\n- x\n```"))
        assertEquals("- x", previewOf("```\n- x\n```"))
    }

    @Test
    fun `the preview of headings alone is the first, and of nothing none`() {
        assertEquals("Befund", previewOf("# Befund\n\n## Maßnahmen\n"))
        assertNull(previewOf(""))
        assertNull(previewOf("  \n\n---\n"))
    }

    @Test
    fun `a new appointment starts with the template, and its preview skips the template's headings`() {
        runBlocking { appointments.setTemplate("## Befund\n\n## Maßnahmen\n") }
        val patient = patient()

        val id = appointment(patient)

        assertEquals("## Befund\n\n## Maßnahmen", get(id).text)
        assertEquals("Befund", rows(patient).single().preview)
        runBlocking { appointments.setText(id, "## Befund\nSchulter frei\n\n## Maßnahmen\n") }
        assertEquals("Schulter frei", rows(patient).single().preview)
    }

    @Test
    fun `a changed template changes only new appointments, and an empty one is none`() {
        runBlocking { appointments.setTemplate("# Befund") }
        val patient = patient()
        val before = appointment(patient)

        runBlocking { appointments.setTemplate("# Verlauf") }
        val after = appointment(patient)
        runBlocking { appointments.setTemplate("") }
        val without = appointment(patient)

        assertEquals("# Befund", get(before).text)
        assertEquals("# Verlauf", get(after).text)
        assertEquals("", get(without).text)
        assertEquals("", runBlocking { appointments.template() })
    }

    @Test
    fun `deleting an appointment leaves the others`() {
        val patient = patient()
        val kept = appointment(patient, text = "bleibt")
        val deleted = appointment(patient, text = "geht")

        runBlocking { appointments.delete(deleted) }

        assertNull(runBlocking { appointments.get(deleted) })
        assertEquals(listOf(kept), rows(patient).map { it.id })
    }

    @Test
    fun `deleting a patient for good deletes their appointments`() {
        val patient = patient()
        val id = appointment(patient, text = "Schulter")

        runBlocking { patients.delete(patient) }

        assertNull(runBlocking { appointments.get(id) })
        assertEquals(emptyList<PatientHit>(), runBlocking { patients.search("schulter") })
    }

    @Test
    fun `what is typed is saved as the Doku locks, also in a new appointment and the template`() {
        val id = appointment(patient())
        val savers = listOf(
            doku.addSaver(appointments.draft(id, get(id).text, "Getippt  ", keep = false)),
            doku.addSaver(appointments.templateDraft("", "# Befund")),
        )

        doku.lock()
        savers.forEach(AutoCloseable::close)
        runBlocking { doku.unlockWithPassword(PASSWORD) }

        assertEquals("Getippt", get(id).text)
        assertEquals("# Befund", runBlocking { appointments.template() })
    }

    @Test
    fun `a new appointment left as it was made goes again, also as the Doku locks`() {
        val patient = patient()
        val left = appointment(patient)
        close(left)
        assertNull(runBlocking { appointments.get(left) })

        val locked = appointment(patient)
        val saver = doku.addSaver(appointments.draft(locked, get(locked).text, get(locked).text, keep = false))
        doku.lock()
        saver.close()
        runBlocking { doku.unlockWithPassword(PASSWORD) }

        assertNull(runBlocking { appointments.get(locked) })
        assertEquals(emptyList<AppointmentRow>(), rows(patient))
    }

    @Test
    fun `a new appointment kept, or asked about once, is no longer new`() {
        val patient = patient()
        val kept = appointment(patient)
        close(kept, keep = true)
        val asked = appointment(patient)
        close(asked)
        val again = appointment(patient)

        close(kept)
        close(again)
        // Gone already: asked again, it is no longer new.
        appointments.draft(again, "", "", keep = false).close(turning = false)

        assertEquals(listOf(kept), rows(patient).map { it.id })
        assertNull(appointments.draft(kept, get(kept).text, get(kept).text, keep = false).pending())
    }

    @Test
    fun `a new appointment stays as the screen turns, and while typed in`() {
        val patient = patient()
        val turned = appointment(patient)
        close(turned, turning = true)
        assertEquals(listOf(turned), rows(patient).map { it.id })

        val typed = appointment(patient)
        appointments.draft(typed, get(typed).text, "Befund", keep = false).close(turning = false)

        assertEquals("Befund", get(typed).text)
        close(turned)
        assertEquals(listOf(typed), rows(patient).map { it.id })
    }

    @Test
    fun `a new appointment stays once its day, its time or its text was changed`() {
        val patient = patient()
        val changes: List<suspend (Long) -> Unit> = listOf(
            { appointments.setDate(it, LocalDate.parse("2026-10-22")) },
            { appointments.setTime(it, LocalTime.parse("09:30")) },
            { appointments.setText(it, "Mobilisation") },
        )

        val kept = changes.map { change ->
            val id = appointment(patient)
            runBlocking { change(id) }
            close(id)
            id
        }

        assertEquals(kept.toSet(), rows(patient).map { it.id }.toSet())
    }

    @Test
    fun `a closed patient's appointments are only to read`() {
        val patient = patient()
        val id = appointment(patient, text = "vorher")
        runBlocking { patients.close(patient) }

        runBlocking {
            appointments.setText(id, "nachher")
            appointments.setDate(id, LocalDate.parse("2026-01-01"))
            appointments.delete(id)
        }

        assertEquals("vorher", get(id).text)
        assertEquals(LocalDate.parse("2026-10-23"), get(id).date)
        assertFalse(runBlocking { checkNotNull(appointments.page(id)) }.canChange)
        assertNull(runBlocking { appointments.create(patient) })
        runBlocking { patients.restore(patient) }
        assertTrue(runBlocking { checkNotNull(appointments.page(id)) }.canChange)
    }

    @Test
    fun `the start page goes by the last appointment up to today, those without one after, by when added`() {
        val seenLongAgo = patient("Früher")
        clock.now = clock.now.plusSeconds(60)
        val seenToday = patient("Heute")
        clock.now = clock.now.plusSeconds(60)
        val toCome = patient("Bald")
        clock.now = clock.now.plusSeconds(60)
        val never = patient("Nie")
        appointment(seenLongAgo, date = "2026-09-01")
        appointment(seenLongAgo, date = "2026-09-15")
        appointment(seenToday, date = "2026-10-23")
        appointment(toCome, date = "2026-10-01")
        appointment(toCome, date = "2026-11-30")

        val rows = runBlocking { checkNotNull(patients.active(PatientOrder.LastSeen)) }

        // "Nie", without an appointment, comes after all with one, though added today.
        assertEquals(listOf(seenToday, toCome, seenLongAgo, never), rows.map { it.id })
        assertEquals(
            listOf(LocalDate.parse("2026-10-23"), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-09-15"), null),
            rows.map { it.lastSeen },
        )
    }

    @Test
    fun `the search finds text in appointments, active and archived, the latest first`() {
        val active = patient("Frau M.")
        val older = appointment(active, date = "2026-10-01", text = "Knie **rechts** geschwollen")
        val newer = appointment(active, date = "2026-10-20", text = "Knie besser")
        appointment(active, text = "Schulter")
        val closed = patient("Herr K.")
        val archived = appointment(closed, date = "2026-10-05", text = "knie")
        runBlocking { patients.close(closed) }

        assertEquals(
            listOf(
                PatientHit(active, "Frau M.", archived = false, AppointmentHit(newer, LocalDate.parse("2026-10-20"))),
                PatientHit(active, "Frau M.", archived = false, AppointmentHit(older, LocalDate.parse("2026-10-01"))),
                PatientHit(closed, "Herr K.", archived = true, AppointmentHit(archived, LocalDate.parse("2026-10-05"))),
            ),
            runBlocking { patients.search("KNIE") },
        )
        assertEquals(listOf(older), runBlocking { patients.search("frau rechts") }.mapNotNull { it.appointment?.id })
    }

    @Test
    fun `a search for a name finds the patient, not each of their appointments`() {
        val id = patient("Frau M.")
        appointment(id, text = "Frau M. kam pünktlich")

        assertEquals(listOf(PatientHit(id, "Frau M.", archived = false)), runBlocking { patients.search("Frau") })
    }

    private companion object {
        const val PASSWORD = "geheim-geheim"
    }
}
