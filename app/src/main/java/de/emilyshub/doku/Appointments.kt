package de.emilyshub.doku

import de.emilyshub.data.InputText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * The patients' appointments (SPEC.md §14), each documented in Markdown, and the Doku's appointment template, which
 * fills in each new one. An active patient's appointments are written here; a closed one's are only to read. As
 * [patients], all of it is read and written only while [doku] is open, and its changes count in [Patients.changes].
 */
class Appointments(private val doku: Doku, private val patients: Patients, private val background: CoroutineScope) {

    /** The appointments of patient [patientId], the latest first ([Appointment.LATEST_FIRST]); none once locked. */
    suspend fun of(patientId: Long): List<AppointmentRow>? = doku.withDatabase { database ->
        database.appointments().of(patientId)
            .sortedWith(Appointment.LATEST_FIRST)
            .map { AppointmentRow(it.id, it.date, it.time, previewOf(it.text)) }
    }

    /** Appointment [id]; none once deleted or the Doku is locked. */
    suspend fun get(id: Long): Appointment? = doku.withDatabase { it.appointments().get(id) }

    /** Whether appointment [id] may be changed: it is there, and its patient is active. */
    suspend fun canChange(id: Long): Boolean = doku.withDatabase { changeable(it, id) != null } == true

    /**
     * Adds an appointment today, without a time, to active patient [patientId], its text the template's as it is now;
     * its id, none for a closed patient or once locked.
     */
    suspend fun create(patientId: Long): Long? = patients.changing { database ->
        database.patients().get(patientId)?.takeUnless(Patient::isClosed) ?: return@changing null
        val text = database.settings().get(TEMPLATE).orEmpty()
        database.appointments().insert(Appointment(patientId = patientId, date = patients.today(), text = text))
    }

    /** Moves appointment [id] to [date], in the past or to come. */
    suspend fun setDate(id: Long, date: LocalDate) = change(id) { it.copy(date = date) }

    /** Sets the time of day of appointment [id], or none. */
    suspend fun setTime(id: Long, time: LocalTime?) = change(id) { it.copy(time = time?.withSecond(0)?.withNano(0)) }

    /** Sets the text of appointment [id] to [text], which must not be too long ([Appointment.textTooLong]). */
    suspend fun setText(id: Long, text: String) {
        require(!Appointment.textTooLong(text)) { "The appointment's text is too long" }
        patients.changing { writeText(it, id, text) }
    }

    /** As [setText], but on [background], for an editor that saves as it closes; not once the Doku is locked. */
    fun setTextLater(id: Long, text: String) {
        background.launch { setText(id, text) }
    }

    /** What the editor has typed for appointment [id], to be written as the Doku locks ([DokuSaver]). */
    fun pendingText(id: Long, text: String): suspend (DokuDatabase) -> Unit = { writeText(it, id, text) }

    /** Deletes appointment [id] for good; one of a closed patient stays, as it is only to read. */
    suspend fun delete(id: Long) {
        patients.changing { database -> changeable(database, id)?.let { database.appointments().delete(it.id) } }
    }

    /** The appointment template, empty for none; none once locked. */
    suspend fun template(): String? = doku.withDatabase { it.settings().get(TEMPLATE).orEmpty() }

    /** Sets the template to [text], which must not be too long; empty for none. What is there already stays as it is. */
    suspend fun setTemplate(text: String) {
        require(!Appointment.textTooLong(text)) { "The appointment template is too long" }
        patients.changing { writeTemplate(it, text) }
    }

    /** As [setTemplate], but on [background], for an editor that saves as it closes. */
    fun setTemplateLater(text: String) {
        background.launch { setTemplate(text) }
    }

    /** What the editor has typed for the template, to be written as the Doku locks ([DokuSaver]). */
    fun pendingTemplate(text: String): suspend (DokuDatabase) -> Unit = { writeTemplate(it, text) }

    private suspend fun writeText(database: DokuDatabase, id: Long, text: String) {
        val appointment = changeable(database, id) ?: return
        database.appointments().update(appointment.copy(text = InputText.multiLine(text)))
    }

    private suspend fun writeTemplate(database: DokuDatabase, text: String) {
        database.settings().put(DokuSetting(TEMPLATE, InputText.multiLine(text)))
    }

    private suspend fun change(id: Long, change: (Appointment) -> Appointment) {
        patients.changing { database -> changeable(database, id)?.let { database.appointments().update(change(it)) } }
    }

    /** Appointment [id], if its patient is active; a closed patient's are only to read. */
    private suspend fun changeable(database: DokuDatabase, id: Long): Appointment? {
        val appointment = database.appointments().get(id) ?: return null
        return appointment.takeIf { database.patients().get(it.patientId)?.isClosed == false }
    }

    companion object {
        private const val TEMPLATE = "appointment-template"
    }
}
