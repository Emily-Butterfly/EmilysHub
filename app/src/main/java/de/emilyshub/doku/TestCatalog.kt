package de.emilyshub.doku

import de.emilyshub.data.InputText

/**
 * The Doku's tests (SPEC.md §14), set up once for all patients in its menu. A test changed after it was measured
 * changes for its old measurements too, which keep their numbers; a value measured once is retired rather than deleted,
 * so that no history goes unnoticed. Archived, a test is no longer chosen for new measurements; deleted, it goes with
 * all its measurements. As [patients], all of it is read and written only while [doku] is open, and its changes count in
 * [Patients.changes].
 */
class TestCatalog(private val doku: Doku, private val patients: Patients) {

    /** The tests in use and those archived, each by name; none once locked. */
    suspend fun list(): TestList? = doku.withDatabase { database ->
        val (archived, active) = database.tests().all().sortedWith(compareBy(InputText.byName) { it.name }).partition(ClinicalTest::isArchived)
        TestList(active, archived)
    }

    /** The tests to choose for a new measurement: those not archived, by name. */
    suspend fun choosable(): List<ClinicalTest>? = list()?.active

    /** Test [id] with its values in their order, which of them were measured, and how many measurements it has. */
    suspend fun details(id: Long): TestDetails? = doku.withDatabase { database ->
        val test = database.tests().get(id) ?: return@withDatabase null
        val tests = database.tests()
        TestDetails(test, tests.values(id), tests.measuredValues(id).toSet(), tests.affected(id))
    }

    /** The draft to change test [id] with. */
    fun draftOf(details: TestDetails) = TestDraft(
        name = details.test.name,
        values = details.values.map(TestValue::toDraft),
        sum = details.test.hasSum,
        sumDirection = details.test.sumDirection,
    )

    /** The names of the tests other than [id], which no other may read the same as; none once locked. */
    suspend fun takenNames(id: Long?): List<String>? = doku.withDatabase { takenNames(it, id) }

    /**
     * Saves [draft], which must have no [problems], as test [id] or a new one; its id, none once locked. Values left out
     * of the draft are deleted if never measured, else retired. A sum asked for of two values or more stays asked for,
     * though some are retired, formed only while two or more are not ([TestDetails.formsSum]).
     */
    suspend fun save(id: Long?, draft: TestDraft): Long? = patients.changing { database ->
        val tests = database.tests()
        val problems = draft.problems(taken = takenNames(database, id))
        require(problems.isEmpty) { "The test's draft has problems: $problems" }
        val stored = id?.let { tests.get(it) ?: return@changing null }
        val storedValues = id?.let { tests.values(it) }.orEmpty()
        val measured = id?.let { tests.measuredValues(it).toSet() }.orEmpty()
        val kept = draft.values.mapNotNull(ValueDraft::id).toSet()
        val leftOut = storedValues.filter { it.id !in kept }
        val values = draft.values.map { value ->
            TestValue(
                id = value.id ?: 0,
                testId = id ?: 0,
                position = 0,
                name = value.cleanedName(),
                unit = value.cleanedUnit(),
                min = value.cleanedMin(),
                max = value.cleanedMax(),
                decimals = value.decimals,
                direction = value.direction,
                // Only a measured value stays retired: one never measured has nothing to keep.
                retired = value.retired && value.id in measured,
            )
        } + leftOut.filter { it.id in measured }.map { it.copy(retired = true) }
        val test = ClinicalTest(
            id = id ?: 0,
            name = InputText.singleLine(draft.name),
            hasSum = draft.sum && values.size >= 2,
            sumDirection = draft.sumDirection,
            archivedOn = stored?.archivedOn,
        )
        tests.save(test, values, deleted = leftOut.filterNot { it.id in measured }.map(TestValue::id))
    }

    /** Archives test [id] today: no longer to choose for new measurements; its measurements stay. */
    suspend fun archive(id: Long) = change(id) { it.copy(archivedOn = it.archivedOn ?: patients.today()) }

    /** Makes archived test [id] choosable again. */
    suspend fun restore(id: Long) = change(id) { it.copy(archivedOn = null) }

    /** Deletes test [id] for good, with all its measurements. */
    suspend fun delete(id: Long) {
        patients.changing { it.tests().delete(id) }
    }

    /** The names of the tests other than [id]. */
    private suspend fun takenNames(database: DokuDatabase, id: Long?) = database.tests().all().filter { it.id != id }.map(ClinicalTest::name)

    /** Changes test [id] as [change] has it. */
    private suspend fun change(id: Long, change: (ClinicalTest) -> ClinicalTest) {
        patients.changing { database -> database.tests().get(id)?.let { database.tests().update(change(it)) } }
    }
}

/** The tests [active], to choose for measurements, and those [archived], each by name. */
data class TestList(val active: List<ClinicalTest>, val archived: List<ClinicalTest>)

/** A test with its [values] in their order, the ids of those [measured], and how many measurements it has. */
data class TestDetails(val test: ClinicalTest, val values: List<TestValue>, val measured: Set<Long>, val affected: Affected) {
    /** Whether the test forms a sum: asked for, and of two values or more. */
    val formsSum: Boolean get() = formsSum(test, values)
}

/** Whether [test] with its [values] forms a sum: asked for, and of two values that are not retired, or more. */
fun formsSum(test: ClinicalTest, values: List<TestValue>): Boolean = test.hasSum && values.count { !it.retired } >= 2
