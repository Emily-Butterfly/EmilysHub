package de.emilyshub.doku

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.doku.assessments.Answer
import de.emilyshub.doku.assessments.BodyRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The forms filled in at appointments (SPEC.md §14): saved as answered, changed and deleted; the last used and those
 * hidden; the cards and history of a patient; and the search in their words.
 */
@RunWith(AndroidJUnit4::class)
class AssessmentsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val doku = Doku(
        keyFile = File(context.noBackupFilesDir, "assessments-test.key"),
        databaseFile = context.getDatabasePath(DokuDatabase.NAME),
        fingerprint = FakeFingerprint(),
        openDatabase = { testDokuDatabase(context) },
        clipboard = DokuClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    ).also { runBlocking { it.load(); it.setUp("geheim-geheim") } }

    private val patients = Patients(doku, clock, CoroutineScope(Dispatchers.Unconfined))
    private val appointments = Appointments(doku, clock, CoroutineScope(Dispatchers.Unconfined))
    private val assessments = Assessments(doku, CoroutineScope(Dispatchers.Unconfined))

    @After
    fun closeDatabase() {
        doku.lock()
        context.deleteDatabase(DokuDatabase.NAME)
        File(context.noBackupFilesDir, "assessments-test.key").delete()
    }

    private val patient = runBlocking { checkNotNull(patients.create(PatientDraft("Frau M."))) }

    private fun appointment(date: String, of: Long = patient): Long = runBlocking {
        checkNotNull(appointments.create(of)).also { appointments.setDate(it, LocalDate.parse(date)) }
    }

    /** The PHQ-9 answered with [points], item by item. */
    private fun phq9(vararg points: Int): Map<String, Answer> = points.withIndex().associate { (i, p) -> "${i + 1}" to Answer.Chosen("$p") }

    private fun fill(at: Long, form: String, answers: Map<String, Answer>, id: Long? = null): Long =
        runBlocking { checkNotNull(assessments.save(at, id, form, answers)) }

    private fun cards() = runBlocking { checkNotNull(assessments.cards(patient)) }

    @Test
    fun `a form is saved as far as it is answered, and changed`() {
        val at = appointment("2026-10-23")
        val id = fill(at, "phq-9", phq9(1, 2))

        val saved = runBlocking { checkNotNull(assessments.get(id)) }
        assertEquals(phq9(1, 2), saved.answers)
        assertEquals(LocalDate.parse("2026-10-23"), saved.date)
        assertNull(saved.results.single().value)

        fill(at, "phq-9", phq9(1, 2, 0, 3, 1, 0, 2, 1, 0), id = id)

        val changed = runBlocking { checkNotNull(assessments.at(at)) }.single()
        assertEquals(1000L, changed.results.single().value)
        assertEquals("„leichtgradige“ Depression", changed.results.single().band)
    }

    @Test
    fun `an appointment has several forms, the same twice too, in the order filled in`() {
        val at = appointment("2026-10-23")
        fill(at, "phq-9", phq9(0))
        fill(at, "anamnese-v8", mapOf("1" to Answer.Regions(setOf(BodyRegion.KneeLeft))))
        fill(at, "phq-9", phq9(3))

        assertEquals(listOf("phq-9", "anamnese-v8", "phq-9"), runBlocking { checkNotNull(assessments.at(at)) }.map { it.assessmentId })
    }

    @Test
    fun `a form is deleted, and an appointment deleted takes its forms along`() {
        val at = appointment("2026-10-23")
        val first = fill(at, "phq-9", phq9(0))
        fill(at, "phq-9", phq9(3))

        runBlocking { assessments.delete(first) }
        assertEquals(1, runBlocking { checkNotNull(assessments.at(at)) }.size)

        runBlocking { appointments.delete(at) }
        assertEquals(AssessmentCards(emptyList(), emptyList()), cards())
    }

    @Test
    fun `a form added by + Bogen is used once answered, and goes again if left empty`() {
        val at = appointment("2026-10-23")
        val empty = checkNotNull(runBlocking { assessments.create(at, "anamnese-v8") })
        val answered = checkNotNull(runBlocking { assessments.create(at, "phq-9") })
        assertEquals(emptyList<String>(), runBlocking { checkNotNull(assessments.recent()) }.map { it.id })
        fill(at, "phq-9", phq9(2), id = answered)

        assessments.dropIfEmptyLater(empty)
        assessments.dropIfEmptyLater(answered)

        assertEquals(listOf(answered), runBlocking { checkNotNull(assessments.at(at)) }.map { it.id })
        assertEquals(listOf("phq-9"), runBlocking { checkNotNull(assessments.recent()) }.map { it.id })
        assertNull(runBlocking { assessments.create(at, "gibt-es-nicht") })
    }

    @Test
    fun `answers to items hidden by their condition are not kept`() {
        val at = appointment("2026-10-23")
        val answers = mapOf("26" to Answer.YesNo(false), "26-welche" to Answer.Text("Melanom"), "8" to Answer.Text("Kopfweh"))

        val id = fill(at, "anamnese-v8", answers)

        assertEquals(mapOf("26" to Answer.YesNo(false), "8" to Answer.Text("Kopfweh")), runBlocking { checkNotNull(assessments.get(id)) }.answers)
    }

    @Test
    fun `a patient deleted takes their forms along`() {
        val id = fill(appointment("2026-10-23"), "phq-9", phq9(0))

        runBlocking { patients.delete(patient) }

        assertNull(runBlocking { assessments.get(id) })
    }

    @Test
    fun `a new appointment with a form stays, though nothing else of it was changed`() {
        val at = checkNotNull(runBlocking { appointments.create(patient) })
        fill(at, "phq-9", phq9(0))

        appointments.dropIfUntouchedLater(at)

        assertEquals(1, runBlocking { checkNotNull(appointments.of(patient)) }.size)
    }

    @Test
    fun `a closed patient's forms are only to read`() {
        val at = appointment("2026-10-23")
        val id = fill(at, "phq-9", phq9(0))
        runBlocking { patients.close(patient) }

        assertNull(runBlocking { assessments.save(at, id, "phq-9", phq9(3)) })
        assertNull(runBlocking { assessments.save(at, null, "phq-9", phq9(3)) })
        runBlocking { assessments.delete(id) }

        assertEquals(phq9(0), runBlocking { checkNotNull(assessments.get(id)) }.answers)
    }

    @Test
    fun `the last five used come first, the latest on top, without those hidden`() {
        val at = appointment("2026-10-23")
        assertEquals(emptyList<String>(), runBlocking { checkNotNull(assessments.recent()) }.map { it.id })

        fill(at, "phq-9", phq9(0))
        fill(at, "anamnese-v8", mapOf("8" to Answer.Text("Kopfweh")))
        fill(at, "anamnese-v8", mapOf("8" to Answer.Text("Rücken")))

        assertEquals(listOf("anamnese-v8", "phq-9"), runBlocking { checkNotNull(assessments.recent()) }.map { it.id })

        runBlocking { assessments.setHidden("anamnese-v8", true) }
        assertEquals(listOf("phq-9"), runBlocking { checkNotNull(assessments.recent()) }.map { it.id })
    }

    @Test
    fun `the recent keep five at most`() {
        assertEquals(listOf("f", "e", "d", "c", "b"), recentAfter(listOf("a", "b", "c", "d", "e", "f")))
        assertEquals(listOf("b", "f", "e", "d", "c"), recentAfter(listOf("a", "b", "c", "d", "e", "f", "b")))
    }

    private fun recentAfter(used: List<String>): List<String> = used.fold(emptyList(), Assessments::withRecent)

    @Test
    fun `hidden forms stay hidden until shown again, and filled ones stay`() {
        val at = appointment("2026-10-23")
        fill(at, "phq-9", phq9(0, 0, 0, 0, 0, 0, 0, 0, 0))
        runBlocking { assessments.setHidden("phq-9", true) }

        assertEquals(setOf("phq-9"), runBlocking { assessments.hidden() })
        assertEquals(1, runBlocking { checkNotNull(assessments.at(at)) }.size)
        assertEquals(listOf("phq-9"), cards().scored.map { it.form.id })

        runBlocking { assessments.setHidden("phq-9", false) }
        assertEquals(emptySet<String>(), runBlocking { assessments.hidden() })
    }

    @Test
    fun `a card shows each result's last value, its band, and the change since the first, the way the form says is better`() {
        fill(appointment("2026-09-01"), "phq-9", phq9(3, 3, 3, 3, 3, 0, 0, 0, 0))
        fill(appointment("2026-10-01"), "phq-9", phq9(1, 1, 1, 1, 1, 0, 0, 0, 0))
        // Not complete: no result, leaves the card as it is.
        fill(appointment("2026-10-05"), "phq-9", phq9(0))
        fill(appointment("2026-10-10"), "anamnese-v8", mapOf("8" to Answer.Text("Kopfweh")))

        val card = cards().scored.single()

        assertEquals("phq-9", card.form.id)
        assertEquals(LocalDate.parse("2026-10-01"), card.lastFilled)
        val line = card.lines.single()
        assertEquals(500L, line.last)
        assertEquals("unauffällig", line.band)
        // Lower is better for the PHQ-9.
        assertEquals(Change.Changed(-1000, better = true, since = LocalDate.parse("2026-09-01")), line.change)
    }

    @Test
    fun `a form filled in once has no change, and the latest card comes first`() {
        fill(appointment("2026-09-01"), "phq-9", phq9(3, 3, 3, 3, 3, 3, 3, 3, 3))

        assertEquals(Change.MeasuredOnce, cards().scored.single().lines.single().change)
    }

    @Test
    fun `forms without a result are listed by date, the latest first`() {
        val older = fill(appointment("2026-09-01"), "anamnese-v8", emptyMap())
        val newer = fill(appointment("2026-10-01"), "anamnese-v8", emptyMap())

        val cards = cards()

        assertEquals(emptyList<AssessmentCard>(), cards.scored)
        assertEquals(listOf(newer, older), cards.unscored.map { it.id })
    }

    @Test
    fun `the history has a column for each result and a row for each form filled in, the latest first`() {
        val first = appointment("2026-09-01")
        fill(first, "phq-9", phq9(3, 3, 3, 3, 3, 0, 0, 0, 0))
        val second = appointment("2026-10-01")
        val latest = fill(second, "phq-9", phq9(1))
        fill(appointment("2026-10-02", of = runBlocking { checkNotNull(patients.create(PatientDraft("Herr K."))) }), "phq-9", phq9(0))

        val history = runBlocking { checkNotNull(assessments.history(patient, "phq-9")) }

        assertEquals(listOf(HistoryColumn("Summenwert", "Punkte", Direction.LowerIsBetter, 0, 2700)), history.columns)
        assertEquals(listOf(latest to second, history.rows[1].id to first), history.rows.map { it.id to it.appointmentId })
        assertEquals(listOf(listOf(null), listOf(1500L)), history.rows.map { it.numbers })
        assertEquals("Frau M.", history.patientName)
    }

    @Test
    fun `the search finds the words of forms and opens them`() {
        val at = appointment("2026-10-01")
        val id = fill(at, "anamnese-v8", mapOf("12a" to Answer.Checked(setOf("ruhe"), "Wärmflasche"), "1" to Answer.Regions(emptySet(), "Leiste links")))

        val hits = runBlocking { patients.search("wärmflasche") }

        assertEquals(listOf(AssessmentHit(id, "anamnese-v8", at, LocalDate.parse("2026-10-01"), "Anamnesebogen Version 8")), hits.map { it.assessment })
        assertEquals(1, runBlocking { patients.search("leiste") }.size)
        assertTrue(runBlocking { patients.search("ruhe") }.isEmpty())
    }

    @Test
    fun `a new form with nothing ticked but a result stays only once confirmed, and is used then`() {
        val at = appointment("2026-10-23")
        val left = checkNotNull(runBlocking { assessments.create(at, "rmdq") })
        val confirmed = checkNotNull(runBlocking { assessments.create(at, "rmdq") })
        val phq = checkNotNull(runBlocking { assessments.create(at, "phq-9") })

        assessments.dropIfEmptyLater(left)
        assessments.dropIfEmptyLater(confirmed, confirmed = true)
        assessments.dropIfEmptyLater(phq, confirmed = true)

        val kept = runBlocking { checkNotNull(assessments.at(at)) }
        assertEquals(listOf(confirmed), kept.map { it.id })
        assertEquals(listOf(0L), kept.single().results.map { it.value })
        assertEquals(listOf("rmdq"), runBlocking { checkNotNull(assessments.recent()) }.map { it.id })
    }
}
