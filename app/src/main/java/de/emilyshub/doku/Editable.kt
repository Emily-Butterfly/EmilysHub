package de.emilyshub.doku

/**
 * What of the Doku may be changed (SPEC.md §14): what belongs to an active patient; a closed patient's, with their
 * appointments, measurements and forms, is only to read until they are restored. The one place that decides it: every
 * write goes through [editablePatient] or [editableAppointment], and every page reads it from what these give.
 */
internal suspend fun DokuDatabase.editablePatient(id: Long): Patient? = patients().get(id)?.takeIf(Patient::canChange)

/** Appointment [id], if its patient may be changed ([editablePatient]); with all that is measured and filled in at it. */
internal suspend fun DokuDatabase.editableAppointment(id: Long): Appointment? =
    appointments().get(id)?.takeIf { editablePatient(it.patientId) != null }
