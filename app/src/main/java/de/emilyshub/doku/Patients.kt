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

    /** How many changes were made here: Compose state, read by the screens to read anew. */
    var changes by mutableIntStateOf(0)
        private set

    /** The day it is. */
    fun today(): LocalDate = LocalDate.now(clock)

    /**
     * The active patients in [order], each with the day of their last appointment up to today, if any. By the latest
     * entry, a patient's last appointment or else the day they were added, in one list, the latest first (as decided on
     * 08.11.2026); on the same day, the one added later first.
     */
    suspend fun active(order: PatientOrder): List<PatientRow>? = doku.withDatabase { database ->
        val active = database.patients().all().filterNot(Patient::isClosed)
        // Appointments come with #60, which gives each its last one; until then none has had one.
        val lastSeen = emptyMap<Long, LocalDate>()
        val sorted = when (order) {
            PatientOrder.LastSeen -> active.sortedWith(
                compareByDescending<Patient> { lastSeen[it.id] ?: dayOf(it.createdAt) }.thenByDescending { it.createdAt },
            )
            PatientOrder.Name -> active.sortedWith(compareBy(InputText.byName) { it.name })
        }
        sorted.map { PatientRow(it.id, it.name, lastSeen[it.id]) }
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

    /** Patient [id], active or closed; none once deleted or the Doku is locked. */
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

    /** Sets [field] of active patient [id] to [text], which must not be too long ([Patient.textTooLong]). */
    suspend fun setText(id: Long, field: PatientField, text: String) {
        require(!Patient.textTooLong(text)) { "The patient's text is too long" }
        changing { writeText(it, id, field, text) }
    }

    /** As [setText], but on [background], for an editor that saves as it closes; not once the Doku is locked. */
    fun setTextLater(id: Long, field: PatientField, text: String) {
        background.launch { setText(id, field, text) }
    }

    /** What a text editor has typed for [field] of patient [id], to be written as the Doku locks ([DokuSaver]). */
    fun pendingText(id: Long, field: PatientField, text: String): suspend (DokuDatabase) -> Unit = { writeText(it, id, field, text) }

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

    /**
     * Deletes for good the patients of [shown], as the archive showed them overdue, with all that belongs to them: not
     * one that has turned overdue since, nor one restored meanwhile; how many.
     */
    suspend fun deleteOverdue(shown: Collection<Long>): Int = changing { database ->
        val today = today()
        val overdue = database.patients().all()
            .filter { patient -> patient.id in shown && patient.closedOn?.let { isOverdue(it, today) } == true }
            .map(Patient::id)
        database.patients().delete(overdue)
        overdue.size
    } ?: 0

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
                    val text = InputText.searchable((listOf(patient.name) + patient.texts).joinToString("\n"))
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

    /** Keeps [order] as the start page's, in the Doku's database. */
    suspend fun setOrder(order: PatientOrder) {
        changing { it.settings().put(DokuSetting(ORDER, order.name)) }
    }

    /** Sets [field] of active patient [id] to [text], cleaned; a closed one is only to read. */
    private suspend fun writeText(database: DokuDatabase, id: Long, field: PatientField, text: String) {
        val patient = database.patients().get(id)?.takeUnless(Patient::isClosed) ?: return
        database.patients().update(patient.withText(field, InputText.multiLine(text)))
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
