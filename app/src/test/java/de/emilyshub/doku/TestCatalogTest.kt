package de.emilyshub.doku

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.NameProblem
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
 * The Doku's tests (SPEC.md §14): their fields and checks, the sum, values removed or retired, archiving and deleting
 * with their measurements, and how many measurements a change touches.
 */
@RunWith(AndroidJUnit4::class)
class TestCatalogTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val doku = Doku(
        keyFile = File(context.noBackupFilesDir, "tests-test.key"),
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

    @After
    fun closeDatabase() {
        doku.lock()
        context.deleteDatabase(DokuDatabase.NAME)
        File(context.noBackupFilesDir, "tests-test.key").delete()
    }

    private fun save(draft: TestDraft, id: Long? = null): Long = runBlocking { checkNotNull(catalog.save(id, draft)) }

    private fun details(id: Long) = runBlocking { checkNotNull(catalog.details(id)) }

    private fun draft(id: Long) = catalog.draftOf(details(id))

    private val barthel = TestDraft(
        name = "Barthel-Index",
        values = listOf(
            ValueDraft(name = "Essen", unit = "Punkte", min = "0", max = "10"),
            ValueDraft(name = "Baden", unit = "Punkte", min = "0", max = "5"),
        ),
        sum = true,
    )

    /** A measurement of [testId] for a new appointment of patient [name], finding [found] by value name. */
    private fun measure(testId: Long, name: String = "Frau M.", vararg found: Pair<String, Long>): Long = runBlocking {
        val patient = checkNotNull(patients.create(PatientDraft(name)))
        val appointment = checkNotNull(appointments.create(patient))
        val values = details(testId).values.associateBy(TestValue::name)
        checkNotNull(
            doku.withDatabase { database ->
                database.measurements().save(
                    Measurement(appointmentId = appointment, testId = testId),
                    found.map { (value, amount) -> MeasurementValue(0, checkNotNull(values[value]).id, amount) },
                )
            },
        )
    }

    @Test
    fun `a test has a name and its values, in their order, each as typed`() {
        val id = save(
            TestDraft(
                name = "  Handkraft ",
                values = listOf(ValueDraft(name = " rechts ", unit = " kg ", min = "0", max = "80,5", decimals = true, direction = Direction.HigherIsBetter)),
            ),
        )

        val details = details(id)

        assertEquals("Handkraft", details.test.name)
        assertFalse(details.test.hasSum)
        assertEquals(
            listOf(TestValue(details.values.single().id, id, 0, "rechts", "kg", 0, 8050, decimals = true, direction = Direction.HigherIsBetter)),
            details.values,
        )
        assertEquals(Affected(0, 0), details.affected)
    }

    @Test
    fun `the name is required, at most 80 characters, and no other test's in whatever case`() {
        save(barthel)

        assertEquals(NameProblem.Missing, TestDraft(" ").problems(emptyList()).name)
        assertEquals(NameProblem.TooLong, TestDraft("x".repeat(81)).problems(emptyList()).name)
        assertEquals(NameProblem.Taken, runBlocking { catalog.problems(barthel.copy(name = "BARTHEL-index"), id = null) }?.name)
        assertThrows(IllegalArgumentException::class.java) { save(barthel) }
    }

    @Test
    fun `a test keeps its own name when changed`() {
        val id = save(barthel)

        assertTrue(runBlocking { checkNotNull(catalog.problems(draft(id), id)) }.isEmpty)
    }

    @Test
    fun `each value has a name of its own in the test, a short unit, and a range from low to high`() {
        val problems = TestDraft(
            name = "ROM",
            values = listOf(
                ValueDraft(name = "Flexion", unit = "°", min = "0", max = "180"),
                ValueDraft(name = "flexion", unit = "x".repeat(21)),
                ValueDraft(name = "", min = "90", max = "10"),
                ValueDraft(name = "Abduktion", min = "abc", max = "1,5"),
                ValueDraft(name = "Extension", min = "-10"),
            ),
        ).problems(emptyList())

        assertEquals(
            listOf(
                setOf(ValueProblem.NameTaken),
                setOf(ValueProblem.NameTaken, ValueProblem.UnitTooLong),
                setOf(ValueProblem.NameMissing, ValueProblem.RangeReversed),
                // Without decimals, 1,5 is no number of the value.
                setOf(ValueProblem.MinInvalid, ValueProblem.MaxInvalid),
                emptySet(),
            ),
            problems.values,
        )
    }

    @Test
    fun `numbers are typed with a comma, decimals only where allowed, and shown without trailing zeros`() {
        assertEquals(1250L, TestNumber.parse("12,5", decimals = true))
        assertEquals(1250L, TestNumber.parse("12.5", decimals = true))
        assertEquals(1234L, TestNumber.parse(" 12,34 ", decimals = true))
        assertNull(TestNumber.parse("12,345", decimals = true))
        assertNull(TestNumber.parse("12,5", decimals = false))
        assertEquals(-1000L, TestNumber.parse("-10", decimals = false))
        assertNull(TestNumber.parse("1.000", decimals = false))
        assertNull(TestNumber.parse("", decimals = false))
        assertEquals("12,5", TestNumber.format(1250))
        assertEquals("120", TestNumber.format(12000))
        assertEquals("−10", TestNumber.format(-1000))
        assertEquals("0,05", TestNumber.format(5))
    }

    @Test
    fun `a sum is formed only of two values or more`() {
        val one = TestDraft("Handkraft", listOf(ValueDraft(name = "rechts")), sum = true)

        assertFalse(one.canSum)
        assertFalse(details(save(one)).formsSum)
        assertTrue(barthel.canSum)
        val id = save(barthel.copy(sumDirection = Direction.LowerIsBetter))
        assertTrue(details(id).formsSum)
        assertEquals(Direction.LowerIsBetter, details(id).test.sumDirection)
    }

    @Test
    fun `a sum waits while only one value is not retired, and comes back with a second`() {
        val id = save(barthel)
        measure(id, "Frau M.", "Baden" to 500)

        save(draft(id).let { it.copy(values = it.values.map { value -> value.copy(retired = value.name == "Baden") }) }, id)
        assertFalse(details(id).formsSum)

        save(draft(id).let { it.copy(values = it.values.map { value -> value.copy(retired = false) }) }, id)
        assertTrue(details(id).formsSum)
    }

    @Test
    fun `the values are reordered`() {
        val id = save(barthel)

        save(draft(id).let { it.copy(values = it.values.reversed()) }, id)

        assertEquals(listOf("Baden", "Essen"), details(id).values.map(TestValue::name))
        assertEquals(listOf(0, 1), details(id).values.map(TestValue::position))
    }

    @Test
    fun `a value never measured is deleted when removed, one measured is retired and its numbers stay`() {
        val id = save(barthel.copy(values = barthel.values + ValueDraft(name = "Gehen")))
        val measurement = measure(id, "Frau M.", "Baden" to 500)

        save(draft(id).let { it.copy(values = it.values.filter { value -> value.name == "Essen" }) }, id)

        val values = details(id).values
        assertEquals(listOf("Essen" to false, "Baden" to true), values.map { it.name to it.retired })
        val found = runBlocking { doku.withDatabase { it.measurements().values(measurement) } }
        assertEquals(listOf(500L), found?.map(MeasurementValue::amount))
    }

    @Test
    fun `a retired value is activated again`() {
        val id = save(barthel)
        measure(id, "Frau M.", "Baden" to 500)
        save(draft(id).let { it.copy(values = it.values.map { value -> value.copy(retired = value.name == "Baden") }) }, id)

        save(draft(id).let { it.copy(values = it.values.map { value -> value.copy(retired = false) }) }, id)

        assertEquals(listOf(false, false), details(id).values.map(TestValue::retired))
    }

    @Test
    fun `a test needs a value that is not retired`() {
        val all = TestDraft("Handkraft", listOf(ValueDraft(id = 1, name = "rechts", retired = true)))

        assertTrue(all.problems(emptyList()).noActiveValue)
        assertTrue(TestDraft("Handkraft", emptyList()).problems(emptyList()).noActiveValue)
        assertTrue(TestDraft("Handkraft").problems(emptyList()).values.single().contains(ValueProblem.NameMissing))
    }

    @Test
    fun `a value added later has no numbers in the old measurements, which keep theirs out of a new range`() {
        val id = save(barthel)
        val measurement = measure(id, "Frau M.", "Essen" to 1000)

        save(draft(id).let { it.copy(values = it.values.map { value -> value.copy(max = "5") } + ValueDraft(name = "Gehen")) }, id)

        assertEquals(listOf("Essen", "Baden", "Gehen"), details(id).values.map(TestValue::name))
        assertEquals(listOf(1000L), runBlocking { doku.withDatabase { it.measurements().values(measurement) } }?.map(MeasurementValue::amount))
    }

    @Test
    fun `the measurements a change touches are counted, with their patients`() {
        val id = save(barthel)
        measure(id, "Frau M.", "Essen" to 1000)
        measure(id, "Herr K.", "Essen" to 500)
        val again = runBlocking { checkNotNull(patients.search("Herr")).first().id }
        runBlocking {
            val appointment = checkNotNull(appointments.create(again))
            doku.withDatabase { it.measurements().save(Measurement(appointmentId = appointment, testId = id), emptyList()) }
        }

        assertEquals(Affected(measurements = 3, patients = 2), runBlocking { catalog.affected(id) })
    }

    @Test
    fun `an archived test is not to choose for new measurements, and comes back by restoring`() {
        val id = save(barthel)
        save(TestDraft("Handkraft", listOf(ValueDraft(name = "rechts"))))

        runBlocking { catalog.archive(id) }

        val list = runBlocking { checkNotNull(catalog.list()) }
        assertEquals(listOf("Handkraft"), list.active.map(ClinicalTest::name))
        assertEquals(listOf("Barthel-Index"), list.archived.map(ClinicalTest::name))
        assertEquals(LocalDate.parse("2026-10-23"), list.archived.single().archivedOn)
        assertEquals(listOf("Handkraft"), runBlocking { catalog.choosable() }?.map(ClinicalTest::name))
        runBlocking { catalog.restore(id) }
        assertEquals(emptyList<ClinicalTest>(), runBlocking { catalog.list() }?.archived)
    }

    @Test
    fun `an archived test keeps being archived when changed`() {
        val id = save(barthel)
        runBlocking { catalog.archive(id) }

        save(draft(id).copy(name = "Barthel"), id)

        assertTrue(details(id).test.isArchived)
    }

    @Test
    fun `deleting a test deletes its measurements, and the appointments stay`() {
        val id = save(barthel)
        val measurement = measure(id, "Frau M.", "Essen" to 1000)

        runBlocking { catalog.delete(id) }

        assertNull(runBlocking { catalog.details(id) })
        assertNull(runBlocking { doku.withDatabase { it.measurements().get(measurement) } })
        val patient = runBlocking { checkNotNull(patients.search("Frau")).single().id }
        assertEquals(1, runBlocking { appointments.of(patient) }?.size)
    }

    @Test
    fun `deleting an appointment or a patient deletes their measurements`() {
        val id = save(barthel)
        val first = measure(id, "Frau M.", "Essen" to 1000)
        val second = measure(id, "Herr K.", "Essen" to 500)
        val firstAppointment = runBlocking { doku.withDatabase { checkNotNull(it.measurements().get(first)).appointmentId } }
        val herr = runBlocking { checkNotNull(patients.search("Herr")).single().id }

        runBlocking { appointments.delete(checkNotNull(firstAppointment)) }
        runBlocking { patients.delete(herr) }

        assertNull(runBlocking { doku.withDatabase { it.measurements().get(first) } })
        assertNull(runBlocking { doku.withDatabase { it.measurements().get(second) } })
        assertEquals(Affected(0, 0), runBlocking { catalog.affected(id) })
    }
}
