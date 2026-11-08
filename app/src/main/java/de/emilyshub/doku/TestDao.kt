package de.emilyshub.doku

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/** The tests in `doku.db`, with their values. */
@Dao
abstract class TestDao {
    /** Test [id], if there is one. */
    @Query("SELECT * FROM doku_test WHERE id = :id")
    abstract suspend fun get(id: Long): ClinicalTest?

    /** All tests, archived or not: few enough to sort here. */
    @Query("SELECT * FROM doku_test")
    abstract suspend fun all(): List<ClinicalTest>

    /** The values of test [testId], in their order. */
    @Query("SELECT * FROM doku_test_value WHERE test_id = :testId ORDER BY position")
    abstract suspend fun values(testId: Long): List<TestValue>

    /** The values of test [testId] measured at least once. */
    @Query(
        "SELECT DISTINCT mv.value_id FROM measurement_value mv JOIN measurement m ON m.id = mv.measurement_id " +
            "WHERE m.test_id = :testId",
    )
    abstract suspend fun measuredValues(testId: Long): List<Long>

    /** How many measurements of test [testId] there are, and of how many patients. */
    @Query(
        "SELECT COUNT(*) AS measurements, COUNT(DISTINCT a.patient_id) AS patients FROM measurement m " +
            "JOIN appointment a ON a.id = m.appointment_id WHERE m.test_id = :testId",
    )
    abstract suspend fun affected(testId: Long): Affected

    /** Writes [test] over the one with its id. */
    @Update
    abstract suspend fun update(test: ClinicalTest)

    /**
     * Writes [test] and its [values], in their order, adding those without an id; deletes its values of [deleted], which
     * no measurement has. The test's id.
     */
    @Transaction
    open suspend fun save(test: ClinicalTest, values: List<TestValue>, deleted: Collection<Long>): Long {
        val id = if (test.id == 0L) insert(test) else test.id.also { update(test) }
        deleted.forEach { deleteValue(it) }
        values.forEachIndexed { position, value ->
            val placed = value.copy(testId = id, position = position)
            if (value.id == 0L) insertValue(placed) else updateValue(placed)
        }
        return id
    }

    /** Deletes test [id] with its values and all its measurements, or nothing. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteMeasuredValues(id)
        deleteMeasurements(id)
        deleteValues(id)
        deleteTest(id)
    }

    @Insert
    protected abstract suspend fun insert(test: ClinicalTest): Long

    @Insert
    protected abstract suspend fun insertValue(value: TestValue): Long

    @Update
    protected abstract suspend fun updateValue(value: TestValue)

    @Query("DELETE FROM doku_test_value WHERE id = :id")
    protected abstract suspend fun deleteValue(id: Long)

    @Query("DELETE FROM measurement_value WHERE measurement_id IN (SELECT id FROM measurement WHERE test_id = :testId)")
    protected abstract suspend fun deleteMeasuredValues(testId: Long)

    @Query("DELETE FROM measurement WHERE test_id = :testId")
    protected abstract suspend fun deleteMeasurements(testId: Long)

    @Query("DELETE FROM doku_test_value WHERE test_id = :testId")
    protected abstract suspend fun deleteValues(testId: Long)

    @Query("DELETE FROM doku_test WHERE id = :id")
    protected abstract suspend fun deleteTest(id: Long)
}

/** How many [measurements] something touches, of how many [patients]. */
data class Affected(val measurements: Int, val patients: Int)
