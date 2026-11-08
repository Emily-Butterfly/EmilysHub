package de.emilyshub.ui.doku

import de.emilyshub.doku.PatientField
import java.io.Serializable

/** A page of the Doku, opened within its tab (SPEC.md §14). Only ids: nothing of the Doku goes into the saved state. */
sealed interface DokuPage : Serializable {
    /** The start page: the active patients, the search, and the hint to delete those closed long ago. */
    data object Home : DokuPage

    /** The Doku's archive. */
    data object Archive : DokuPage

    /** A patient's page, on its [tab]. */
    data class Patient(val id: Long, val tab: PatientTab = PatientTab.Overview) : DokuPage

    /** One of the four texts of patient [id], to write. */
    data class Text(val id: Long, val field: PatientField) : DokuPage
}

/** The tabs of a patient's page. */
enum class PatientTab { Overview, Appointments, Tests }

/**
 * The pages of the Doku opened one over the other, its start page at the bottom. Within the Doku's tab, not over the
 * app's tabs: a screen over them leaves the Doku, which locks it. Serializable, so that a rotation keeps the page.
 */
data class DokuPages(private val pages: List<DokuPage> = listOf(DokuPage.Home)) : Serializable {
    val top: DokuPage get() = pages.last()

    val canGoBack: Boolean get() = pages.size > 1

    fun open(page: DokuPage) = DokuPages(pages + page)

    fun back() = if (canGoBack) DokuPages(pages.dropLast(1)) else this

    /** With [page] in place of the top one. */
    fun replaceTop(page: DokuPage) = DokuPages(pages.dropLast(1) + page)

    /** Without the pages of patient [id], as once they are deleted. */
    fun without(id: Long) = DokuPages(pages.filterNot { it is DokuPage.Patient && it.id == id || it is DokuPage.Text && it.id == id })
}
