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
import java.time.ZoneId

/**
 * The measurements at appointments (SPEC.md §14): what is typed and how it is checked, the sum, how values changed
 * and whether for the better, the cards and history of a patient, deleting, and the search in remarks.
 */
@RunWith(AndroidJUnit4::class)
class MeasurementsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val doku = Doku(
        keyFile = File(context.noBackupFilesDir, "measurements-test.key"),
        fingerprint = FakeFingerprint(),
        openDatabase = { testDokuDatabase(context) },
        clipboard = DokuClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    ).also { runBlocking { it.load(); it.setUp("geheim-geheim") } }

    private val patients = Patients(doku, clock, CoroutineScope(Dispatchers.Unconfined))
    private val appointments = Appointments(doku, patients, CoroutineScope(Dispatchers.Unconfined))
    private val catalog = TestCatalog(doku, patients)
    private val measurements = Measurements(doku, patients)

    @After
    fun closeDatabase() {
        doku.lock()
        context.deleteDatabase(DokuDatabase.NAME)
        File(context.noBackupFilesDir, "measurements-test.key").delete()
    }

    private val patient = runBlocking { checkNotNull(patients.create(PatientDraft("Frau M."))) }

    private val rom = test(
        TestDraft(
            "Bewegungsumfang Schulter",
            listOf(
                ValueDraft(name = "Flexion", unit = "°", min = "0", max = "180"),
                ValueDraft(name = "Abduktion", unit = "°", min = "0", max = "180", direction = Direction.HigherIsBetter),
            ),
        ),
    )

    private val pain = test(TestDraft("Schmerz", listOf(ValueDraft(name = "VAS", min = "0", max = "10", decimals = true, direction = Direction.LowerIsBetter))))

    private val barthel = test(
        TestDraft(
            "Barthel-Index",
            listOf(ValueDraft(name = "Essen", unit = "Punkte", min = "0", max = "10"), ValueDraft(name = "Baden", unit = "Punkte", min = "0", max = "5")),
            sum = true,
        ),
    )

    private fun test(draft: TestDraft): Long = runBlocking { checkNotNull(catalog.save(null, draft)) }

    private fun values(testId: Long) = runBlocking { checkNotNull(catalog.details(testId)).values }

    private fun valueId(testId: Long, name: String) = values(testId).single { it.name == name }.id

    private fun appointment(date: String, of: Long = patient): Long = runBlocking {
        checkNotNull(appointments.create(of)).also { appointments.setDate(it, LocalDate.parse(date)) }
    }

    /** Measures [testId] at [appointmentId], typing [typed] by value name; the measurement's id. */
    private fun measure(appointmentId: Long, testId: Long, vararg typed: Pair<String, String>, remark: String = ""): Long = runBlocking {
        val form = checkNotNull(measurements.newForm(testId))
        val draft = MeasurementDraft(typed.associate { (name, number) -> valueId(testId, name) to number }, remark)
        checkNotNull(measurements.save(appointmentId, null, form, draft))
    }

    private fun form(testId: Long) = runBlocking { checkNotNull(measurements.newForm(testId)) }

    private fun draftOf(testId: Long, vararg typed: Pair<String, String>) =
        MeasurementDraft(typed.associate { (name, number) -> valueId(testId, name) to number })

    private fun cards() = runBlocking { checkNotNull(measurements.cards(patient)) }

    @Test
    fun `numbers are typed with a comma, with no more decimals than allowed, within the range`() {
        val romForm = form(rom)
        val painForm = form(pain)

        assertEquals(emptyMap<Long, FieldProblem>(), painForm.problems(draftOf(pain, "VAS" to "3,25")).values)
        assertEquals(mapOf(valueId(pain, "VAS") to FieldProblem.NotANumber), painForm.problems(draftOf(pain, "VAS" to "3,255")).values)
        assertEquals(mapOf(valueId(rom, "Flexion") to FieldProblem.NotANumber), romForm.problems(draftOf(rom, "Flexion" to "90,5")).values)
        assertEquals(mapOf(valueId(rom, "Flexion") to FieldProblem.OutOfRange), romForm.problems(draftOf(rom, "Flexion" to "181")).values)
        assertEquals(mapOf(valueId(rom, "Abduktion") to FieldProblem.OutOfRange), romForm.problems(draftOf(rom, "Abduktion" to "-1")).values)
        assertEquals(mapOf(valueId(rom, "Flexion") to FieldProblem.NotANumber), romForm.problems(draftOf(rom, "Flexion" to "viel")).values)
    }

    @Test
    fun `values may stay empty, but one is needed`() {
        val form = form(rom)

        assertTrue(form.problems(draftOf(rom, "Flexion" to "120")).isEmpty)
        assertTrue(form.problems(draftOf(rom, "Flexion" to " ")).nothingEntered)
        assertTrue(form.problems(MeasurementDraft(remark = "nur eine Bemerkung")).nothingEntered)
        assertThrows(IllegalArgumentException::class.java) { measure(appointment("2026-10-23"), rom) }
    }

    @Test
    fun `the sum is there once every value is`() {
        val form = form(barthel)

        assertNull(form.sum(draftOf(barthel, "Essen" to "10")))
        assertEquals(1500L, form.sum(draftOf(barthel, "Essen" to "10", "Baden" to "5")))
        assertNull(form.sum(draftOf(barthel, "Essen" to "10", "Baden" to "6")))
        assertNull(form(rom).sum(draftOf(rom, "Flexion" to "10", "Abduktion" to "20")))
    }

    @Test
    fun `an appointment has several measurements, the same test twice too, as typed`() {
        val at = appointment("2026-10-20")
        measure(at, pain, "VAS" to "6", remark = "vorher  ")
        measure(at, pain, "VAS" to "3,5", remark = "nachher")
        measure(at, barthel, "Essen" to "10", "Baden" to "5")

        val shown = runBlocking { checkNotNull(measurements.at(at)) }

        assertEquals(listOf("Schmerz", "Schmerz", "Barthel-Index"), shown.map { it.test.name })
        assertEquals(listOf(600L, 350L), shown.take(2).map { it.entries.single().second })
        assertEquals(listOf("vorher", "nachher"), shown.take(2).map(MeasurementView::remark))
        assertEquals(1500L, shown[2].sum)
        assertEquals(3, runBlocking { checkNotNull(measurements.at(at)).size })
    }

    @Test
    fun `a measurement is changed, a retired value showing only if it has a number`() {
        val at = appointment("2026-10-20")
        val id = measure(at, rom, "Flexion" to "120", "Abduktion" to "90")
        val other = measure(at, rom, "Abduktion" to "95")
        runBlocking {
            val details = checkNotNull(catalog.details(rom))
            catalog.save(rom, catalog.draftOf(details).let { it.copy(values = it.values.map { value -> value.copy(retired = value.name == "Flexion") }) })
        }

        val form = runBlocking { checkNotNull(measurements.form(id)) }
        assertEquals(listOf("Flexion", "Abduktion"), form.fields.map(TestValue::name))
        assertEquals("120", form.initial.typed[valueId(rom, "Flexion")])
        assertEquals(listOf("Abduktion"), runBlocking { checkNotNull(measurements.form(other)) }.fields.map(TestValue::name))
        assertEquals(listOf("Abduktion"), runBlocking { checkNotNull(measurements.newForm(rom)) }.fields.map(TestValue::name))

        runBlocking { measurements.save(at, id, form, form.initial.copy(typed = form.initial.typed + (valueId(rom, "Abduktion") to "100"))) }
        assertEquals(listOf(12000L, 10000L), runBlocking { checkNotNull(measurements.at(at)) }.first().entries.map { it.second })
    }

    @Test
    fun `an old number stays as it was when its value no longer takes it, until it is changed`() {
        val at = appointment("2026-10-20")
        val id = measure(at, pain, "VAS" to "3,5")
        runBlocking {
            val details = checkNotNull(catalog.details(pain))
            catalog.save(pain, catalog.draftOf(details).let { it.copy(values = it.values.map { value -> value.copy(decimals = false, max = "3") }) })
        }

        val form = runBlocking { checkNotNull(measurements.form(id)) }
        val vas = valueId(pain, "VAS")

        assertTrue(form.problems(form.initial).isEmpty)
        assertEquals(mapOf(vas to 350L), form.numbers(form.initial))
        assertEquals(mapOf(vas to FieldProblem.NotANumber), form.problems(form.initial.copy(typed = mapOf(vas to "2,5"))).values)
        assertEquals(mapOf(vas to FieldProblem.OutOfRange), form.problems(form.initial.copy(typed = mapOf(vas to "4"))).values)
    }

    @Test
    fun `an archived test is not measured anew`() {
        runBlocking { catalog.archive(pain) }

        assertNull(runBlocking { measurements.newForm(pain) })
    }

    @Test
    fun `a measurement is deleted, and an appointment deleted takes its measurements along`() {
        val at = appointment("2026-10-20")
        val first = measure(at, pain, "VAS" to "6")
        measure(at, pain, "VAS" to "4")

        runBlocking { measurements.delete(first) }
        assertEquals(1, runBlocking { checkNotNull(measurements.at(at)).size })

        runBlocking { appointments.delete(at) }
        assertEquals(0, runBlocking { checkNotNull(measurements.at(at)).size })
        assertEquals(emptyList<TestCard>(), cards())
    }

    @Test
    fun `a closed patient's measurements are only to read`() {
        val at = appointment("2026-10-20")
        val id = measure(at, pain, "VAS" to "6")
        runBlocking { patients.close(patient) }

        val form = form(pain)
        assertNull(runBlocking { measurements.save(at, null, form, draftOf(pain, "VAS" to "1")) })
        runBlocking { measurements.delete(id) }

        assertEquals(1, runBlocking { checkNotNull(measurements.at(at)).size })
    }

    @Test
    fun `a card shows the last number and the change since the first, for the better the way the value says`() {
        measure(appointment("2026-09-03"), rom, "Flexion" to "90", "Abduktion" to "100")
        measure(appointment("2026-10-01"), rom, "Flexion" to "110")
        measure(appointment("2026-10-20"), rom, "Flexion" to "120", "Abduktion" to "100")
        measure(appointment("2026-09-10"), pain, "VAS" to "7")
        measure(appointment("2026-10-10"), pain, "VAS" to "4,5")

        val cards = cards()

        assertEquals(listOf("Bewegungsumfang Schulter", "Schmerz"), cards.map { it.test.name })
        assertEquals(LocalDate.parse("2026-10-20"), cards[0].lastMeasured)
        assertEquals(
            listOf(
                CardLine("Flexion", "°", 12000, Change.Changed(3000, better = true, since = LocalDate.parse("2026-09-03"))),
                CardLine("Abduktion", "°", 10000, Change.Unchanged),
            ),
            cards[0].lines,
        )
        // Pain is better lower.
        assertEquals(listOf(CardLine("VAS", "", 450, Change.Changed(-250, better = true, since = LocalDate.parse("2026-09-10")))), cards[1].lines)
    }

    @Test
    fun `worse is worse either way, and once measured there is no change`() {
        assertEquals(Change.Changed(-500, better = false, LocalDate.parse("2026-09-01")), changeOf(1000, 500, LocalDate.parse("2026-09-01"), Direction.HigherIsBetter, measuredOnce = false))
        assertEquals(Change.Changed(500, better = false, LocalDate.parse("2026-09-01")), changeOf(500, 1000, LocalDate.parse("2026-09-01"), Direction.LowerIsBetter, measuredOnce = false))
        measure(appointment("2026-10-20"), pain, "VAS" to "3")

        assertEquals(Change.MeasuredOnce, cards().single().lines.single().change)
    }

    @Test
    fun `a card of a test with a sum shows only the sum, of the measurements complete`() {
        measure(appointment("2026-09-01"), barthel, "Essen" to "5", "Baden" to "0")
        measure(appointment("2026-09-15"), barthel, "Essen" to "10")
        measure(appointment("2026-10-01"), barthel, "Essen" to "10", "Baden" to "5")

        val line = cards().single().lines.single()

        assertEquals(CardLine(null, "Punkte", 1500, Change.Changed(1000, better = true, LocalDate.parse("2026-09-01"))), line)
        assertEquals(LocalDate.parse("2026-10-01"), cards().single().lastMeasured)
    }

    @Test
    fun `a card leaves out a retired value, and shows an archived test`() {
        measure(appointment("2026-09-01"), rom, "Flexion" to "90", "Abduktion" to "100")
        runBlocking {
            val details = checkNotNull(catalog.details(rom))
            catalog.save(rom, catalog.draftOf(details).let { it.copy(values = it.values.map { value -> value.copy(retired = value.name == "Flexion") }) })
            catalog.archive(rom)
        }

        val card = cards().single()

        assertTrue(card.test.isArchived)
        assertEquals(listOf("Abduktion"), card.lines.map(CardLine::label))
    }

    @Test
    fun `the history has a column for each value measured, the sum last, and a row for each measurement, the latest first`() {
        val first = appointment("2026-09-01")
        measure(first, barthel, "Essen" to "5", "Baden" to "0", remark = "Eingang")
        val second = appointment("2026-09-15")
        measure(second, barthel, "Essen" to "10")
        appointment("2026-09-20").also { measure(it, pain, "VAS" to "2") }

        val history = runBlocking { checkNotNull(measurements.history(patient, barthel)) }

        assertEquals("Frau M.", history.patientName)
        assertEquals(listOf("Essen", "Baden", null), history.columns.map(HistoryColumn::name))
        assertEquals(
            listOf(
                HistoryRow(second, LocalDate.parse("2026-09-15"), listOf(1000L, null, null), ""),
                HistoryRow(first, LocalDate.parse("2026-09-01"), listOf(500L, 0L, 500L), "Eingang"),
            ),
            history.rows,
        )
        assertNull(runBlocking { measurements.history(patient, rom) })
    }

    @Test
    fun `the history keeps a retired value with numbers, and leaves out one without`() {
        val other = runBlocking { checkNotNull(catalog.save(null, TestDraft("ROM Knie", listOf(ValueDraft(name = "Flexion"), ValueDraft(name = "Extension"))))) }
        measure(appointment("2026-09-01"), other, "Flexion" to "100", "Extension" to "0")
        runBlocking {
            val details = checkNotNull(catalog.details(other))
            catalog.save(other, catalog.draftOf(details).let { it.copy(values = it.values.map { value -> value.copy(retired = value.name == "Extension") } + ValueDraft(name = "Neu")) })
        }

        assertEquals(listOf("Flexion", "Extension", "Neu"), runBlocking { checkNotNull(measurements.history(patient, other)) }.columns.map(HistoryColumn::name))
        assertFalse(runBlocking { checkNotNull(measurements.history(patient, other)) }.columns.any { it.name == null })
    }

    @Test
    fun `the search finds the remarks of measurements and opens their appointment`() {
        val at = appointment("2026-10-20")
        measure(at, pain, "VAS" to "6", remark = "nach Treppensteigen")

        assertEquals(
            listOf(PatientHit(patient, "Frau M.", archived = false, AppointmentHit(at, LocalDate.parse("2026-10-20")))),
            runBlocking { patients.search("treppen") },
        )
    }
}
