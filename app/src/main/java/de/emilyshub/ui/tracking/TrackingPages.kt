package de.emilyshub.ui.tracking

import java.io.Serializable
import java.time.LocalDate

/** A page of Tracking, opened within its tab (SPEC.md §17). Only days and ids: nothing of Tracking goes into the saved state. */
sealed interface TrackingPage : Serializable {
    /** The start page: "Akute Beschwerden?" and the day's entries. */
    data object Home : TrackingPage

    /** The morning entry of [day]. */
    data class Morning(val day: LocalDate) : TrackingPage

    /** The evening entry of [day]. */
    data class Evening(val day: LocalDate) : TrackingPage

    /** The symptoms asked for in the evening. */
    data object Symptoms : TrackingPage

    /** The medications, those taken and those stopped. */
    data object Medications : TrackingPage

    /** Medication [id] with its timeline, or a new one. */
    data class Medication(val id: Long?) : TrackingPage

    /** The base data. */
    data object BaseData : TrackingPage
}

/**
 * The pages of Tracking opened one over the other, its start page at the bottom, as the Doku's are. Serializable, so
 * that a rotation keeps the page.
 */
data class TrackingPages(private val pages: List<TrackingPage> = listOf(TrackingPage.Home)) : Serializable {
    /** The page shown. */
    val top: TrackingPage get() = pages.last()

    /** Whether a page lies under the one shown, for back to lead to. */
    val canGoBack: Boolean get() = pages.size > 1

    /** With [page] opened over the one shown. */
    fun open(page: TrackingPage) = TrackingPages(pages + page)

    /** Back to the page under the one shown; the start page stays. */
    fun back() = if (canGoBack) TrackingPages(pages.dropLast(1)) else this

    /** With [page] in place of the top one. */
    fun replaceTop(page: TrackingPage) = TrackingPages(pages.dropLast(1) + page)
}
