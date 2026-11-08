package de.emilyshub.doku

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import java.time.LocalTime

/**
 * A built-in form filled in at an appointment (SPEC.md §14): which form, by its stable [assessmentId], and its answers
 * ([AssessmentAnswer]); its results are reckoned from them each time, never stored.
 */
@Entity(
    tableName = "assessment_entry",
    foreignKeys = [ForeignKey(entity = Appointment::class, parentColumns = ["id"], childColumns = ["appointment_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("appointment_id")],
)
data class AssessmentEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "appointment_id") val appointmentId: Long,
    @ColumnInfo(name = "assessment_id") val assessmentId: String,
)

/** The answer to item [itemId] of a filled-in form, as [de.emilyshub.doku.assessments.AnswerCodec] stores it. */
@Entity(
    tableName = "assessment_answer",
    primaryKeys = ["entry_id", "item_id"],
    foreignKeys = [ForeignKey(entity = AssessmentEntry::class, parentColumns = ["id"], childColumns = ["entry_id"], onDelete = ForeignKey.CASCADE)],
)
data class AssessmentAnswer(
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "item_id") val itemId: String,
    val value: String,
    /** What it holds in words, for the search; empty for none. */
    val text: String,
)

/** A filled-in form with the [date] of its appointment, and its time, if any, to order those of a day. */
data class DatedAssessmentEntry(@Embedded val entry: AssessmentEntry, val date: LocalDate, val time: LocalTime?)

/** The forms filled in, in `doku.db`, each with its answers. */
@Dao
abstract class AssessmentDao {
    /** Form [id], if there is one. */
    @Query("SELECT * FROM assessment_entry WHERE id = :id")
    abstract suspend fun get(id: Long): AssessmentEntry?

    /** The forms filled in at appointment [appointmentId], in the order they were added. */
    @Query("SELECT * FROM assessment_entry WHERE appointment_id = :appointmentId ORDER BY id")
    abstract suspend fun at(appointmentId: Long): List<AssessmentEntry>

    /** The answers of form [entryId]. */
    @Query("SELECT * FROM assessment_answer WHERE entry_id = :entryId")
    abstract suspend fun answers(entryId: Long): List<AssessmentAnswer>

    /** The forms of patient [patientId], each with the day of its appointment, in no order. */
    @Query(
        "SELECT e.*, a.date AS date, a.time AS time FROM assessment_entry e JOIN appointment a ON a.id = e.appointment_id " +
            "WHERE a.patient_id = :patientId",
    )
    abstract suspend fun ofPatient(patientId: Long): List<DatedAssessmentEntry>

    /** The answers of the forms of patient [patientId]. */
    @Query(
        "SELECT an.* FROM assessment_answer an JOIN assessment_entry e ON e.id = an.entry_id " +
            "JOIN appointment a ON a.id = e.appointment_id WHERE a.patient_id = :patientId",
    )
    abstract suspend fun answersOfPatient(patientId: Long): List<AssessmentAnswer>

    /** Every answer in words, for the Doku's search. */
    @Query("SELECT * FROM assessment_answer WHERE text != ''")
    abstract suspend fun worded(): List<AssessmentAnswer>

    /** Every form filled in, with its appointment's day, for the Doku's search. */
    @Query("SELECT e.*, a.date AS date, a.time AS time FROM assessment_entry e JOIN appointment a ON a.id = e.appointment_id")
    abstract suspend fun allDated(): List<DatedAssessmentEntry>

    /** Writes [entry], adding it if it has no id, with [answers] in place of its own; its id. */
    @Transaction
    open suspend fun save(entry: AssessmentEntry, answers: List<AssessmentAnswer>): Long {
        val id = if (entry.id == 0L) insert(entry) else entry.id.also { update(entry) }
        deleteAnswers(id)
        answers.forEach { insertAnswer(it.copy(entryId = id)) }
        return id
    }

    /** Deletes form [id] with its answers. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteAnswers(id)
        deleteOne(id)
    }

    @Insert
    protected abstract suspend fun insert(entry: AssessmentEntry): Long

    @Update
    protected abstract suspend fun update(entry: AssessmentEntry)

    @Insert
    protected abstract suspend fun insertAnswer(answer: AssessmentAnswer)

    @Query("DELETE FROM assessment_answer WHERE entry_id = :id")
    protected abstract suspend fun deleteAnswers(id: Long)

    @Query("DELETE FROM assessment_entry WHERE id = :id")
    protected abstract suspend fun deleteOne(id: Long)
}
