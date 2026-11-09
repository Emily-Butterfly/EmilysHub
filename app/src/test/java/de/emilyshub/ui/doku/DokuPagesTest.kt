package de.emilyshub.ui.doku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the Doku's pages lead to each other (SPEC.md §14): what the search found opens over the pages back leads to, a
 * measurement or form opens over its appointment, which it covers rather than closes, and a patient gone takes along
 * what was theirs.
 */
class DokuPagesTest {

    private val appointment = DokuPage.Appointment(patientId = 1, id = 10)

    @Test
    fun `an appointment found opens over the patient's appointments, for back to lead there`() {
        val pages = DokuPages().openFoundAppointment(patientId = 1, id = 10)

        assertEquals(appointment, pages.top)
        assertEquals(DokuPage.Patient(1, PatientTab.Appointments), pages.back().top)
        assertEquals(DokuPage.Home, pages.back().back().top)
    }

    @Test
    fun `a form found opens over its appointment, over the patient's forms`() {
        val pages = DokuPages().openFoundAssessment(patientId = 1, appointmentId = 10, assessmentId = "phq9", id = 5)

        assertEquals(DokuPage.Assessment(1, 10, "phq9", 5), pages.top)
        assertEquals(appointment, pages.back().top)
        assertEquals(DokuPage.Patient(1, PatientTab.Assessments), pages.back().back().top)
    }

    @Test
    fun `a measurement or form opens over its appointment, which it covers, and back leads to it again`() {
        val atAppointment = DokuPages().open(DokuPage.Patient(1)).open(appointment)

        val measuring = atAppointment.measureAt(appointment, testId = 3, id = null)
        assertEquals(DokuPage.Measurement(1, 10, 3, null), measuring.top)
        assertTrue(measuring.covers(appointment))
        assertEquals(atAppointment, measuring.back())

        val filling = atAppointment.fillAt(appointment, "phq9", id = 5, new = true)
        assertEquals(DokuPage.Assessment(1, 10, "phq9", 5, new = true), filling.top)
        assertTrue(filling.covers(appointment))
    }

    @Test
    fun `a page shown, or gone back from, is not covered but closed`() {
        val atAppointment = DokuPages().open(DokuPage.Patient(1)).open(appointment)

        assertFalse(atAppointment.covers(appointment))
        assertFalse(atAppointment.back().covers(appointment))
        assertFalse(atAppointment.measureAt(appointment, 3, null).back().back().covers(appointment))
    }

    @Test
    fun `a patient deleted takes all their pages along, one closed only the page shown`() {
        val pages = DokuPages().open(DokuPage.Patient(2)).open(DokuPage.Patient(1)).open(appointment).open(DokuPage.Patient(1, PatientTab.Tests))

        assertEquals(DokuPage.Patient(2), pages.gone(patientId = 1, deleted = true).top)
        assertEquals(appointment, pages.gone(patientId = 1, deleted = false).top)
    }
}
