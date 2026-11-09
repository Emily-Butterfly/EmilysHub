package de.emilyshub.doku

import de.emilyshub.data.InputText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

/**
 * The patients' appointments (SPEC.md §14), each documented in Markdown, and the Doku's appointment template, which
 * fills in each new one. An active patient's appointments are written here; a closed one's are only to read. All of it
 * is read and written only while [doku] is open, and its changes count in [Doku.changes].
 */
class Appointments(private val doku: DokuStore, private val clock: Clock, private val background: CoroutineScope) {

    /** The new appointments as [create] made them, by id, in memory only: to tell whether one was left untouched. */
    private val made = ConcurrentHashMap<Long, Appointment>()

    /** The appointments of patient [patientId], the latest first ([Appointment.LATEST_FIRST]); none once locked. */
    suspend fun of(patientId: Long): List<AppointmentRow>? = doku.withDatabase { database ->
        database.appointments().of(patientId)
            .sortedWith(Appointment.LATEST_FIRST)
            .map { AppointmentRow(it.id, it.date, it.time, previewOf(it.text)) }
    }

    /** Appointment [id]; none once deleted or the Doku is locked. */
    suspend fun get(id: Long): Appointment? = doku.withDatabase { it.appointments().get(id) }

    /**
     * Appointment [id] as its page shows it, in one read: with its patient's name and whether it may be changed
     * ([editableAppointment]); none once deleted, its patient gone, or the Doku locked.
     */
    suspend fun page(id: Long): AppointmentView? = doku.withDatabase { database ->
        val appointment = database.appointments().get(id) ?: return@withDatabase null
        val patient = database.patients().get(appointment.patientId) ?: return@withDatabase null
        AppointmentView(appointment, patient.name, patient.canChange)
    }

    /**
     * Adds an appointment today, without a time, to active patient [patientId], its text the template's as it is now;
     * its id, none for a closed patient or once locked.
     */
    suspend fun create(patientId: Long): Long? = doku.changing { database ->
        database.editablePatient(patientId) ?: return@changing null
        val text = database.settings().get(TEMPLATE).orEmpty()
        val appointment = Appointment(patientId = patientId, date = LocalDate.now(clock), text = text)
        database.appointments().insert(appointment).also { made[it] = appointment.copy(id = it) }
    }

    /** Moves appointment [id] to [date], in the past or to come. */
    suspend fun setDate(id: Long, date: LocalDate) = change(id) { it.copy(date = date) }

    /** Sets the time of day of appointment [id], or none. */
    suspend fun setTime(id: Long, time: LocalTime?) = change(id) { it.copy(time = time?.withSecond(0)?.withNano(0)) }

    /** Sets the text of appointment [id] to [text], which must not be too long ([Appointment.textTooLong]). */
    suspend fun setText(id: Long, text: String) {
        require(!Appointment.textTooLong(text)) { "The appointment's text is too long" }
        doku.changing { writeText(it, id, text) }
    }

    /**
     * What the page of appointment [id], opened with text [original], has not saved yet: [text] once changed; and, for a
     * new one, that it goes again if left as [create] made it: on the same day, at no time, with the template's text and
     * no measurement or form at it, so that "+ Termin" alone keeps nothing, as in the note editor (SPEC.md §14). Not as
     * the screen turns, nor under a page opened over it, as one to measure at; once asked as it goes either way, it is
     * no longer new.
     */
    fun draft(id: Long, original: String, text: String): DokuDraft {
        val typed = TextDraft(doku, background, original, text) { database, written -> writeText(database, id, written) }
        val changed = text != original
        return object : DokuDraft {
            override fun pending(): (suspend (DokuDatabase) -> Unit)? =
                typed.pending() ?: if (changed) null else made.remove(id)?.let { created -> { dropIfUntouched(it, created) } }

            override fun close(leaving: Leaving) {
                typed.close(leaving)
                if (leaving == Leaving.Turning) return
                val created = made.remove(id)
                if (created != null && !changed && leaving == Leaving.Closed) {
                    background.launch { doku.changing { dropIfUntouched(it, created) } }
                }
            }
        }
    }

    /** Deletes appointment [id] for good; one of a closed patient stays, as it is only to read. */
    suspend fun delete(id: Long) {
        doku.changing { database -> database.editableAppointment(id)?.let { database.appointments().delete(it.id) } }
    }

    /** The appointment template, empty for none; none once locked. */
    suspend fun template(): String? = doku.withDatabase { it.settings().get(TEMPLATE).orEmpty() }

    /** Sets the template to [text], which must not be too long; empty for none. What is there already stays as it is. */
    suspend fun setTemplate(text: String) {
        require(!Appointment.textTooLong(text)) { "The appointment template is too long" }
        doku.changing { writeTemplate(it, text) }
    }

    /** What the template's page, opened with [original], has not saved yet: [text], once changed. */
    fun templateDraft(original: String, text: String): DokuDraft = TextDraft(doku, background, original, text, ::writeTemplate)

    /** Sets the text of appointment [id] to [text], cleaned; a closed patient's is only to read. */
    private suspend fun writeText(database: DokuDatabase, id: Long, text: String) {
        val appointment = database.editableAppointment(id) ?: return
        database.appointments().update(appointment.copy(text = InputText.multiLine(text)))
    }

    /** Deletes the appointment [created] if it is still as it was and nothing was measured or filled in at it. */
    private suspend fun dropIfUntouched(database: DokuDatabase, created: Appointment) {
        val appointment = database.editableAppointment(created.id) ?: return
        if (appointment == created && database.measurements().at(created.id).isEmpty() && database.assessments().at(created.id).isEmpty()) {
            database.appointments().delete(created.id)
        }
    }

    /** Sets the template to [text], cleaned. */
    private suspend fun writeTemplate(database: DokuDatabase, text: String) {
        database.settings().put(DokuSetting(TEMPLATE, InputText.multiLine(text)))
    }

    /** Changes appointment [id] as [change] has it; a closed patient's is only to read. */
    private suspend fun change(id: Long, change: (Appointment) -> Appointment) {
        doku.changing { database -> database.editableAppointment(id)?.let { database.appointments().update(change(it)) } }
    }

    companion object {
        private const val TEMPLATE = "appointment-template"
    }
}

/** An appointment as its page shows it: with its [patientName], and whether it [canChange] (SPEC.md §14). */
data class AppointmentView(val appointment: Appointment, val patientName: String, val canChange: Boolean)
