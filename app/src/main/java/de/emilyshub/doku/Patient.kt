package de.emilyshub.doku

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import java.time.Instant
import java.time.LocalDate

/**
 * A patient of the Doku (SPEC.md §14), with as little about them as the work needs: a name or initials, the year of
 * birth at most, and four texts in Markdown. Active until [closedOn], from when they lie in the Doku's archive.
 */
@Entity(tableName = "patient")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "birth_year") val birthYear: Int? = null,
    val diagnosis: String = "",
    val anamnesis: String = "",
    val goals: String = "",
    val other: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "closed_on") val closedOn: LocalDate? = null,
) {
    /** Whether they are closed: in the archive, only to read. */
    val isClosed: Boolean get() = closedOn != null

    /** Whether they and what belongs to them may be changed: only while active ([editablePatient]). */
    val canChange: Boolean get() = !isClosed

    /** All four texts, in the order of [PatientField]. */
    val texts: List<String> get() = PatientField.entries.map(::textOf)

    /** The text of [field]. */
    fun textOf(field: PatientField): String = when (field) {
        PatientField.Diagnosis -> diagnosis
        PatientField.Anamnesis -> anamnesis
        PatientField.Goals -> goals
        PatientField.Other -> other
    }

    /** With [text] for [field]. */
    fun withText(field: PatientField, text: String): Patient = when (field) {
        PatientField.Diagnosis -> copy(diagnosis = text)
        PatientField.Anamnesis -> copy(anamnesis = text)
        PatientField.Goals -> copy(goals = text)
        PatientField.Other -> copy(other = text)
    }

    companion object {
        /** Some twenty pages for each of the four texts. */
        const val TEXT_MAX = 40_000

        /** What keeps [text] from being saved as one of the four texts: too long, once cleaned. */
        fun textTooLong(text: String): Boolean = InputText.lengthOf(InputText.multiLine(text)) > TEXT_MAX
    }
}

/** The four texts of a patient, each optional, in Markdown, in the order the overview shows them. */
enum class PatientField { Diagnosis, Anamnesis, Goals, Other }

/** What keeps a [PatientDraft] from being saved, each shown at its field. */
enum class PatientProblem { NameMissing, NameTooLong, BirthYearInvalid }

/**
 * A patient's name or initials and year of birth as typed, before they are checked (SPEC.md §9: strict input checks):
 * the [birthYear] as the digits typed, none for none.
 */
data class PatientDraft(val name: String, val birthYear: String = "") {

    /** What keeps it from being saved in [year], the year it is: the year of birth lies from 1900 to it. */
    fun problems(year: Int): Set<PatientProblem> {
        val cleaned = InputText.singleLine(name)
        return buildSet {
            if (!InputText.isVisible(cleaned)) add(PatientProblem.NameMissing)
            if (InputText.lengthOf(cleaned) > NAME_MAX) add(PatientProblem.NameTooLong)
            if (birthYear.isNotBlank() && year(birthYear)?.takeIf { it in FIRST_YEAR..year } == null) add(PatientProblem.BirthYearInvalid)
        }
    }

    /** The name on a single line, without surrounding spaces. */
    fun cleanedName(): String = InputText.singleLine(name)

    /** The year of birth, none if none was typed. */
    fun cleanedBirthYear(): Int? = birthYear.takeIf { it.isNotBlank() }?.let(::year)

    private fun year(typed: String) = typed.trim().takeIf { it.length == 4 && it.all(Char::isDigit) }?.toInt()

    companion object {
        const val NAME_MAX = 80
        const val FIRST_YEAR = 1900
    }
}

/** The draft to change [Patient]'s name and year of birth with. */
fun Patient.toDraft() = PatientDraft(name, birthYear?.toString() ?: "")

/** A patient in a list: the active ones on the start page with the day they were [lastSeen] last, if ever. */
data class PatientRow(val id: Long, val name: String, val lastSeen: LocalDate?)

/** A patient in the Doku's archive, closed on [closedOn]; [overdue] once that is twelve months ago or more. */
data class ArchivedPatient(val id: Long, val name: String, val closedOn: LocalDate, val overdue: Boolean)

/** A patient found by the Doku's search, [archived] or active; by the text of one of their appointments if [appointment]. */
data class PatientHit(
    val id: Long,
    val name: String,
    val archived: Boolean,
    val appointment: AppointmentHit? = null,
    val assessment: AssessmentHit? = null,
)

/** An appointment the Doku's search found, of [date]. */
data class AppointmentHit(val id: Long, val date: LocalDate)

/** A filled-in form the Doku's search found by its words: [id], of form [assessmentId] named [name], at [appointmentId] of [date]. */
data class AssessmentHit(val id: Long, val assessmentId: String, val appointmentId: Long, val date: LocalDate, val name: String)

/** How the start page orders the active patients. */
enum class PatientOrder {
    /** By the last appointment up to today, the latest first; those without one by when they were added. */
    LastSeen,

    /** By name, in German alphabetical order. */
    Name,
}
