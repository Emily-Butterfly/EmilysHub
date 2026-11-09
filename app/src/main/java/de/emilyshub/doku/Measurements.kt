package de.emilyshub.doku

import java.time.LocalDate

/**
 * The measurements of the Doku's tests (SPEC.md §14), always at an appointment, with its date; several at one, the
 * same test twice too. Each finds a number for one value or more of its test, and may have a remark. An active
 * patient's are written here, a closed one's only read. The patient's tab Tests shows a card for each test measured,
 * and its history. All of it is read and written only while [doku] is open, and its changes count in [Doku.changes].
 */
class Measurements(private val doku: Doku) {

    /** The measurements at appointment [appointmentId], in the order they were added; none once locked. */
    suspend fun at(appointmentId: Long): List<MeasurementView>? = doku.withDatabase { database ->
        val tests = TestsRead(database)
        database.measurements().at(appointmentId).map { measurement ->
            val numbers = database.measurements().values(measurement.id).associate { it.valueId to it.amount }
            val test = tests.test(measurement.testId)
            val values = tests.values(measurement.testId)
            val sum = formsSum(test, values)
            MeasurementView(
                id = measurement.id,
                test = test,
                entries = values.filter { !it.retired || it.id in numbers }.map { it to numbers[it.id] },
                sum = if (sum) sumOf(values.filterNot(TestValue::retired), numbers) else null,
                formsSum = sum,
                remark = measurement.remark,
            )
        }
    }

    /**
     * The form for a new measurement of test [testId] at appointment [appointmentId]: a field for each value not
     * retired; none for a closed patient's appointment ([editableAppointment]), an archived test, or once locked.
     */
    suspend fun newForm(appointmentId: Long, testId: Long): MeasurementForm? = doku.withDatabase { database ->
        database.editableAppointment(appointmentId) ?: return@withDatabase null
        val test = database.tests().get(testId)?.takeUnless(ClinicalTest::isArchived) ?: return@withDatabase null
        val values = database.tests().values(testId)
        MeasurementForm(test, values.filterNot(TestValue::retired), formsSum(test, values), MeasurementDraft())
    }

    /**
     * The form to change measurement [id] at appointment [appointmentId] with: the values not retired, and those retired
     * it has a number for; none for a closed patient's ([editableAppointment]), one at another appointment, or once locked.
     */
    suspend fun form(appointmentId: Long, id: Long): MeasurementForm? = doku.withDatabase { database ->
        database.editableAppointment(appointmentId) ?: return@withDatabase null
        val measurement = database.measurements().get(id)?.takeIf { it.appointmentId == appointmentId } ?: return@withDatabase null
        val test = database.tests().get(measurement.testId) ?: return@withDatabase null
        val values = database.tests().values(test.id)
        val numbers = database.measurements().values(id).associate { it.valueId to it.amount }
        MeasurementForm(
            test = test,
            fields = values.filter { !it.retired || it.id in numbers },
            formsSum = formsSum(test, values),
            initial = MeasurementDraft(numbers.mapValues { TestNumber.typed(it.value) }, measurement.remark),
        )
    }

    /**
     * Saves [draft] for [form], which must have no [MeasurementForm.problems], as measurement [id] or as a new one at
     * appointment [appointmentId]; its id, none for a closed patient's or once locked.
     */
    suspend fun save(appointmentId: Long, id: Long?, form: MeasurementForm, draft: MeasurementDraft): Long? {
        require(form.problems(draft).isEmpty) { "The measurement has problems" }
        return doku.changing { database ->
            database.editableAppointment(appointmentId) ?: return@changing null
            if (id != null && database.measurements().get(id)?.appointmentId != appointmentId) return@changing null
            val measurement = Measurement(id = id ?: 0, appointmentId = appointmentId, testId = form.test.id, remark = draft.cleanedRemark())
            val values = form.numbers(draft).map { (valueId, amount) -> MeasurementValue(0, valueId, amount) }
            database.measurements().save(measurement, values)
        }
    }

    /** Deletes measurement [id]; one of a closed patient stays, as it is only to read. */
    suspend fun delete(id: Long) {
        doku.changing { database ->
            val measurement = database.measurements().get(id) ?: return@changing
            if (database.editableAppointment(measurement.appointmentId) != null) database.measurements().delete(id)
        }
    }

    /**
     * A card for each test measured of patient [patientId], archived ones too, the one measured last first: the day of
     * its last measurement, and for the sum if the test forms one, else for each value not retired, the last number
     * and how it changed since the first.
     */
    suspend fun cards(patientId: Long): List<TestCard>? = doku.withDatabase { database ->
        val read = PatientMeasurements.of(database, patientId)
        read.byTest.map { (testId, measurements) ->
            val test = read.tests.test(testId)
            val values = read.tests.values(testId)
            val lines = if (formsSum(test, values)) {
                listOfNotNull(line(null, sumUnit(values), test.sumDirection, measurements.mapNotNull { m -> read.sum(m, values)?.let { m.date to it } }))
            } else {
                values.filterNot(TestValue::retired).mapNotNull { value ->
                    line(value.name, value.unit, value.direction, measurements.mapNotNull { m -> read.number(m, value)?.let { m.date to it } })
                }
            }
            TestCard(test, measurements.last().date, lines)
        }.sortedWith(compareByDescending<TestCard> { it.lastMeasured }.thenBy { it.test.name })
    }

    /**
     * The history of test [testId] for patient [patientId]: a column for each value it has numbers for, retired ones
     * too, and for the sum if the test forms one; and a row for each measurement, the latest first.
     */
    suspend fun history(patientId: Long, testId: Long): TestHistory? = doku.withDatabase { database ->
        val patient = database.patients().get(patientId) ?: return@withDatabase null
        val read = PatientMeasurements.of(database, patientId)
        val measurements = read.byTest[testId] ?: return@withDatabase null
        val test = read.tests.test(testId)
        val values = read.tests.values(testId)
        val measured = values.filter { value -> !value.retired || measurements.any { read.number(it, value) != null } }
        val sum = formsSum(test, values)
        val columns = measured.map { HistoryColumn(it.name, it.unit, it.direction, it.min, it.max) } +
            listOfNotNull(HistoryColumn(null, sumUnit(values), test.sumDirection, null, null).takeIf { sum })
        val rows = measurements.reversed().map { m ->
            HistoryRow(
                appointmentId = m.measurement.appointmentId,
                date = m.date,
                numbers = measured.map { read.number(m, it) } + if (sum) listOf(read.sum(m, values)) else emptyList(),
                remark = m.measurement.remark,
            )
        }
        TestHistory(test, patient.name, columns, rows)
    }

    /** The line of a card for what [label] names, from its [numbers] by day, oldest first; none without any. */
    private fun line(label: String?, unit: String, direction: Direction, numbers: List<Pair<LocalDate, Long>>): CardLine? {
        val (since, first) = numbers.firstOrNull() ?: return null
        val last = numbers.last().second
        return CardLine(label, unit, last, changeOf(first, last, since, direction, measuredOnce = numbers.size == 1))
    }

    /** The unit of a sum: that of its values, if they all have the same; else none. */
    private fun sumUnit(values: List<TestValue>): String = values.filterNot(TestValue::retired).map(TestValue::unit).distinct().singleOrNull().orEmpty()
}

/** The tests read once for a page: few enough to keep. */
private class TestsRead(private val database: DokuDatabase) {
    private val tests = mutableMapOf<Long, ClinicalTest>()
    private val values = mutableMapOf<Long, List<TestValue>>()

    /** Test [id], which a measurement has. */
    suspend fun test(id: Long): ClinicalTest = tests[id] ?: checkNotNull(database.tests().get(id)).also { tests[id] = it }

    /** The values of test [testId], in their order. */
    suspend fun values(testId: Long): List<TestValue> = values[testId] ?: database.tests().values(testId).also { values[testId] = it }
}

/** What a patient's measurements found, read at once: [byTest], each test's measurements oldest first. */
private class PatientMeasurements(
    val tests: TestsRead,
    val byTest: Map<Long, List<DatedMeasurement>>,
    private val numbers: Map<Long, Map<Long, Long>>,
) {
    /** What [m] found for [value], if anything. */
    fun number(m: DatedMeasurement, value: TestValue): Long? = numbers[m.measurement.id]?.get(value.id)

    /** The sum [m] makes of [values], once it found a number for each not retired. */
    fun sum(m: DatedMeasurement, values: List<TestValue>): Long? = sumOf(values.filterNot(TestValue::retired), numbers[m.measurement.id].orEmpty())

    companion object {
        /** By day, those without a time first, then by time, then as added. */
        private val OLDEST_FIRST: Comparator<DatedMeasurement> = compareBy<DatedMeasurement> { it.date }
            .thenComparator { a, b -> compareValues(a.time, b.time) }
            .thenBy { it.measurement.id }

        /** The measurements of patient [patientId], read at once. */
        suspend fun of(database: DokuDatabase, patientId: Long): PatientMeasurements {
            val dao = database.measurements()
            val numbers = dao.valuesOfPatient(patientId).groupBy(MeasurementValue::measurementId)
                .mapValues { (_, found) -> found.associate { it.valueId to it.amount } }
            return PatientMeasurements(TestsRead(database), dao.ofPatient(patientId).sortedWith(OLDEST_FIRST).groupBy { it.measurement.testId }, numbers)
        }
    }
}

/** A measurement at an appointment: what it found for each value of its test it shows, and the sum, if formed. */
data class MeasurementView(
    /** The measurement's id, to change it. */
    val id: Long,
    val test: ClinicalTest,
    /** The values not retired, and those retired it has a number for, each with its number, if any. */
    val entries: List<Pair<TestValue, Long?>>,
    /** The sum, once every value not retired has a number; none until then. */
    val sum: Long?,
    /** Whether its test forms a sum, so that a missing one shows as "Summe: –". */
    val formsSum: Boolean,
    /** Its remark, empty for none. */
    val remark: String,
)

/** A test measured of a patient, [lastMeasured] on that day, with how its values, or its sum, developed. */
data class TestCard(val test: ClinicalTest, val lastMeasured: LocalDate, val lines: List<CardLine>)

/** The last number of a value named [label], or of the sum without, in [unit], and how it changed since the first. */
data class CardLine(val label: String?, val unit: String, val last: Long, val change: Change)

/** A test's history for a patient: [columns] of values, the sum last if any, and [rows] of measurements, the latest first. */
data class TestHistory(val test: ClinicalTest, val patientName: String, val columns: List<HistoryColumn>, val rows: List<HistoryRow>)

/** A value of a history, by [name], or the sum without; with its range, if any, for the chart's scale. */
data class HistoryColumn(val name: String?, val unit: String, val direction: Direction, val min: Long?, val max: Long?)

/** A measurement in a history: on [date], at [appointmentId], with a number, or none, for each column. */
data class HistoryRow(val appointmentId: Long, val date: LocalDate, val numbers: List<Long?>, val remark: String)
