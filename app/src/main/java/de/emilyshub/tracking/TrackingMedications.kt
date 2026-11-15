package de.emilyshub.tracking

import java.time.Clock
import java.time.LocalDate

/** The medications as their page lists them: those still taken on top, the ones stopped below. */
data class MedicationList(val active: List<Medication>, val ended: List<Medication>)

/** A medication with its dose changes in the order of their dates, as its timeline shows them. */
data class MedicationTimeline(val medication: Medication, val doseChanges: List<DoseChange>)

/** What keeps a medication from being saved. */
enum class MedicationProblem { NoName, NoTime, EndBeforeStart }

/**
 * The medications (SPEC.md §17, "Grunddaten & Medikamente"), typed in freely, with no list to choose from, and their
 * timelines; none of them linked to routines. Changed or stopped only with those treating, which their pages say.
 * Read and written only while Tracking is open.
 */
class TrackingMedications(private val tracking: TrackingStore, private val clock: Clock) {

    /** The medications, those still taken on top, each list by name; none once locked. */
    suspend fun list(): MedicationList? = tracking.withDatabase { database ->
        val today = LocalDate.now(clock)
        val (ended, active) = database.medications().all().partition { it.end != null && it.end.isBefore(today) }
        MedicationList(active, ended)
    }

    /** Medication [id] with its timeline; none if gone, or once locked. */
    suspend fun timeline(id: Long): MedicationTimeline? = tracking.withDatabase { database ->
        database.medications().get(id)?.let { MedicationTimeline(it, database.medications().doseChanges(id)) }
    }

    /** Saves [medication], a new one if its id is 0: its id, or null if it has a [problemOf] or Tracking is locked. */
    suspend fun save(medication: Medication): Long? = tracking.changing { write(it, medication) }

    /** Writes [medication] into [database], as [save] does. */
    suspend fun write(database: TrackingDatabase, medication: Medication): Long? {
        if (problemOf(medication) != null) return null
        val cleaned = medication.cleaned()
        return if (cleaned.id == 0L) {
            database.medications().insert(cleaned)
        } else {
            database.medications().get(cleaned.id) ?: return null
            database.medications().update(cleaned)
            cleaned.id
        }
    }

    /** Stops medication [id] on [day], the last day it is taken. */
    suspend fun end(id: Long, day: LocalDate) {
        tracking.changing { database ->
            val medication = database.medications().get(id)?.takeIf { !day.isBefore(it.start) } ?: return@changing
            database.medications().update(medication.copy(end = day))
        }
    }

    /** Notes that the dose of [medicationId] changed on [date] from [from] to [to]; false if either is empty. */
    suspend fun addDoseChange(medicationId: Long, date: LocalDate, from: String, to: String): Boolean = tracking.changing { database ->
        if (from.isBlank() || to.isBlank() || database.medications().get(medicationId) == null) return@changing false
        database.medications().insertDoseChange(DoseChange(medicationId = medicationId, date = date, from = from.trim(), to = to.trim()))
        true
    } ?: false

    /** Takes [change] off its medication's timeline. */
    suspend fun removeDoseChange(change: DoseChange) {
        tracking.changing { it.medications().deleteDoseChange(change) }
    }

    companion object {
        /** The longest a name, a form or what it is for can be. */
        const val SHORT_MAX = 100

        /** The longest the side effects watched for can be. */
        const val TEXT_MAX = 2_000

        /** What keeps [medication] from being saved, if anything: a name, at least one time and an end not before its start. */
        fun problemOf(medication: Medication): MedicationProblem? = when {
            medication.name.isBlank() -> MedicationProblem.NoName
            medication.times.isEmpty() -> MedicationProblem.NoTime
            medication.end != null && medication.end.isBefore(medication.start) -> MedicationProblem.EndBeforeStart
            else -> null
        }

        private fun Medication.cleaned() = copy(
            name = name.trim().take(SHORT_MAX),
            form = form.trim().take(SHORT_MAX),
            purpose = purpose.trim().take(SHORT_MAX),
            sideEffects = sideEffects.trim().take(TEXT_MAX),
            times = times.distinct().sorted(),
        )
    }
}
