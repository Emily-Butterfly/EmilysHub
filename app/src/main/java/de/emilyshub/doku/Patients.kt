package de.emilyshub.doku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import de.emilyshub.data.InputText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * The patients of the Doku (SPEC.md §14): the active ones on its start page, the closed ones in its archive, until they
 * are deleted for good, which the start page suggests twelve months after they were closed. All of it is read and
 * written only while [doku] is open; once it is locked, reads give nothing and writes do nothing.
 *
 * [changes] counts the changes made here, as Compose state, for the screens to read anew. What an editor saves as it
 * closes is written on [background], so that it outlives the editor.
 */
class Patients(private val doku: Doku, private val clock: Clock, private val background: CoroutineScope) {

    var changes by mutableIntStateOf(0)
        private set

    /** The day it is. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The active patients in [order], each with the day of their last appointment up to today, if any. */
    suspend fun active(order: PatientOrder): List<PatientRow>? = doku.withDatabase { database ->
        val active = database.patients().all().filterNot(Patient::isClosed)
        // Appointments come with #60; until then none has had one.
        val rows = active.map { it to null as LocalDate? }
        when (order) {
            PatientOrder.LastSeen -> rows.sortedWith(
                compareByDescending<Pair<Patient, LocalDate?>> { (patient, lastSeen) -> lastSeen ?: dayOf(patient.createdAt) }
                    .thenByDescending { (patient, _) -> patient.createdAt },
            )
            PatientOrder.Name -> rows.sortedWith(compareBy(InputText.byName) { (patient, _) -> patient.name })
        }.map { (patient, lastSeen) -> PatientRow(patient.id, patient.name, lastSeen) }
    }

    /** The closed patients, the one closed last first, those closed twelve months ago or more marked. */
    suspend fun archive(): List<ArchivedPatient>? = doku.withDatabase { database ->
        val today = today()
        database.patients().all()
            .mapNotNull { patient -> patient.closedOn?.let { ArchivedPatient(patient.id, patient.name, it, isOverdue(it, today)) } }
            .sortedWith(compareByDescending<ArchivedPatient> { it.closedOn }.thenBy(InputText.byName) { it.name })
    }

    /** How many patients were closed twelve months ago or more, for the start page to suggest deleting them. */
    suspend fun overdueCount(): Int? = archive()?.count(ArchivedPatient::overdue)

    suspend fun get(id: Long): Patient? = doku.withDatabase { it.patients().get(id) }

    /** Adds a patient as [draft] has them, which must have no [PatientDraft.problems]; their id, none once locked. */
    suspend fun create(draft: PatientDraft): Long? {
        require(draft.problems(today().year).isEmpty()) { "The patient's draft has problems" }
        val patient = Patient(name = draft.cleanedName(), birthYear = draft.cleanedBirthYear(), createdAt = now())
        return changing { it.patients().insert(patient) }
    }

    /** Changes the name and year of birth of active patient [id] as [draft] has them, which must have no problems. */
    suspend fun rename(id: Long, draft: PatientDraft) {
        require(draft.problems(today().year).isEmpty()) { "The patient's draft has problems" }
        changeActive(id) { it.copy(name = draft.cleanedName(), birthYear = draft.cleanedBirthYear()) }
    }

    /** Sets [field] of active patient [id] to [text], which must not be too long ([PatientDraft.textTooLong]). */
    suspend fun setText(id: Long, field: PatientField, text: String) {
        require(!PatientDraft.textTooLong(text)) { "The patient's text is too long" }
        changeActive(id) { it.withText(field, InputText.multiLine(text)) }
    }

    /** As [setText], but on [background], for an editor that saves as it closes; not once the Doku is locked. */
    fun setTextLater(id: Long, field: PatientField, text: String) {
        background.launch { setText(id, field, text) }
    }

    /** What a text editor has typed for [field] of patient [id], to be written as the Doku locks ([DokuSaver]). */
    fun pendingText(field: PatientField, id: Long, text: String): suspend (DokuDatabase) -> Unit = { database ->
        val patient = database.patients().get(id)?.takeUnless(Patient::isClosed)
        if (patient != null) database.patients().update(patient.withText(field, InputText.multiLine(text)))
    }

    /** Closes patient [id] today: off the start page, into the archive, only to read there. */
    suspend fun close(id: Long) = changeActive(id) { it.copy(closedOn = today()) }

    /** Makes closed patient [id] active again. */
    suspend fun restore(id: Long) {
        changing { database ->
            val patient = database.patients().get(id)?.takeIf(Patient::isClosed) ?: return@changing
            database.patients().update(patient.copy(closedOn = null))
        }
    }

    /** Deletes patient [id] for good, with all that belongs to them. */
    suspend fun delete(id: Long) {
        changing { it.patients().delete(listOf(id)) }
    }

    /** Deletes for good all patients closed twelve months ago or more, with all that belongs to them; how many. */
    suspend fun deleteOverdue(): Int {
        val overdue = archive()?.filter(ArchivedPatient::overdue)?.map(ArchivedPatient::id).orEmpty()
        if (overdue.isEmpty()) return 0
        changing { it.patients().delete(overdue) }
        return overdue.size
    }

    /**
     * The patients, active and closed, whose name or texts hold every word of [typed], in whatever case and however
     * their letters were typed, by name; nothing for nothing typed. Appointments and remarks follow (#60, #62).
     */
    suspend fun search(typed: String): List<PatientHit> {
        val words = InputText.searchable(InputText.singleLine(typed).take(MAX_SEARCH)).split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return emptyList()
        return doku.withDatabase { database ->
            database.patients().all()
                .filter { patient ->
                    val text = InputText.searchable(listOf(patient.name, patient.diagnosis, patient.anamnesis, patient.goals, patient.other).joinToString("\n"))
                    words.all { it in text }
                }
                .sortedWith(compareBy(InputText.byName) { it.name })
                .map { PatientHit(it.id, it.name, it.isClosed) }
        }.orEmpty()
    }

    /** How the start page orders the active patients, as last chosen. */
    suspend fun order(): PatientOrder = doku.withDatabase { database ->
        database.settings().get(ORDER)?.let { stored -> PatientOrder.entries.find { it.name == stored } }
    } ?: PatientOrder.LastSeen

    suspend fun setOrder(order: PatientOrder) {
        changing { it.settings().put(DokuSetting(ORDER, order.name)) }
    }

    /** Changes active patient [id] as [change] has it; a closed one is only to read. */
    private suspend fun changeActive(id: Long, change: (Patient) -> Patient) {
        changing { database ->
            val patient = database.patients().get(id)?.takeUnless(Patient::isClosed) ?: return@changing
            database.patients().update(change(patient))
        }
    }

    private suspend fun <T> changing(block: suspend (DokuDatabase) -> T): T? =
        doku.withDatabase(block).also { if (it != null) changes++ }

    private fun now(): Instant = clock.instant()

    private fun dayOf(instant: Instant): LocalDate = instant.atZone(clock.zone).toLocalDate()

    companion object {
        /** As long as a search term need be. */
        private const val MAX_SEARCH = 100
        private const val ORDER = "patient-order"

        /** Whether a patient closed on [closedOn] has been for twelve months or more on [today]. */
        fun isOverdue(closedOn: LocalDate, today: LocalDate): Boolean = !closedOn.plusMonths(OVERDUE_MONTHS).isAfter(today)

        /** After how long a closed patient is suggested for deleting (SPEC.md §14). */
        const val OVERDUE_MONTHS = 12L
    }
}
