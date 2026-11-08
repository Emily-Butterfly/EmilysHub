package de.emilyshub.ui.doku

import de.emilyshub.doku.PatientField
import java.io.Serializable

/** A page of the Doku, opened within its tab (SPEC.md §14). Only ids: nothing of the Doku goes into the saved state. */
sealed interface DokuPage : Serializable {
    /** The patient the page is of, if any. */
    val patientId: Long? get() = null

    /** The start page: the active patients, the search, and the hint to delete those closed long ago. */
    data object Home : DokuPage

    /** The Doku's archive. */
    data object Archive : DokuPage

    /** A patient's page, on its [tab]. */
    data class Patient(val id: Long, val tab: PatientTab = PatientTab.Overview) : DokuPage {
        override val patientId: Long get() = id
    }

    /** One of the four texts of patient [id], to write. */
    data class Text(val id: Long, val field: PatientField) : DokuPage {
        override val patientId: Long get() = id
    }

    /**
     * Appointment [id] of patient [patientId], to document; [new] when just added by "+ Termin", to go again if left
     * untouched.
     */
    data class Appointment(override val patientId: Long, val id: Long, val new: Boolean = false) : DokuPage

    /** The appointment template, to write. */
    data object Template : DokuPage

    /** The Doku's tests, those in use and those archived. */
    data object Tests : DokuPage

    /** Test [id] to change, or a new one. */
    data class TestEditor(val id: Long?) : DokuPage

    /** Measurement [id] of test [testId] at appointment [appointmentId] of patient [patientId], or a new one. */
    data class Measurement(override val patientId: Long, val appointmentId: Long, val testId: Long, val id: Long?) : DokuPage

    /** The history of test [testId] for patient [patientId]. */
    data class History(override val patientId: Long, val testId: Long) : DokuPage

    /**
     * Filled-in form [id] of [assessmentId] at appointment [appointmentId] of patient [patientId]; [new] when just added by
     * "+ Bogen", to go again if left empty.
     */
    data class Assessment(
        override val patientId: Long,
        val appointmentId: Long,
        val assessmentId: String,
        val id: Long,
        val new: Boolean = false,
    ) : DokuPage

    /** The history of form [assessmentId] for patient [patientId]. */
    data class AssessmentHistory(override val patientId: Long, val assessmentId: String) : DokuPage

    /** The built-in forms, each to hide from "+ Bogen". */
    data object Assessments : DokuPage
}

/** The tabs of a patient's page. */
enum class PatientTab { Overview, Appointments, Tests, Assessments }

/**
 * The pages of the Doku opened one over the other, its start page at the bottom. Within the Doku's tab, not over the
 * app's tabs: a screen over them leaves the Doku, which locks it. Serializable, so that a rotation keeps the page.
 */
data class DokuPages(private val pages: List<DokuPage> = listOf(DokuPage.Home)) : Serializable {
    /** The page shown. */
    val top: DokuPage get() = pages.last()

    /** Whether a page lies under the one shown, for back to lead to. */
    val canGoBack: Boolean get() = pages.size > 1

    /** With [page] opened over the one shown. */
    fun open(page: DokuPage) = DokuPages(pages + page)

    /** Back to the page under the one shown; the start page stays. */
    fun back() = if (canGoBack) DokuPages(pages.dropLast(1)) else this

    /** With [page] in place of the top one. */
    fun replaceTop(page: DokuPage) = DokuPages(pages.dropLast(1) + page)

    /** Without the pages of patient [id], as once they are deleted. */
    fun without(id: Long) = DokuPages(pages.filterNot { it.patientId == id })
}
