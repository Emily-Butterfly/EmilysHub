package de.emilyshub.ui.tracking

import de.emilyshub.tracking.HintRule
import de.emilyshub.tracking.HistoryRange
import java.io.Serializable
import java.time.LocalDate

/** A page of Tracking, opened within its tab (SPEC.md §17). Only days and ids: nothing of Tracking goes into the saved state. */
sealed interface TrackingPage : Serializable {
    /** The start page: the day's entries. */
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

    /** Garmin's values from Health Connect: setting up, reading, where they come from. */
    data object Garmin : TrackingPage

    /** A new Stehtest: its variant, the checklist and what came before. */
    data object NewStandTest : TrackingPage

    /** Stehtest [id]: as it runs, or its result once ended. */
    data class StandTest(val id: Long) : TrackingPage

    /** All Stehtests, the latest first. */
    data object StandTests : TrackingPage

    /** Event [id]: its fields, its timer and Garmin's curve around it. */
    data class Event(val id: Long) : TrackingPage

    /** All events, the latest first. */
    data object Events : TrackingPage

    /** Exertion [id], or a new one on [day] if none, made from episode [triggerOf] as one of its triggers if given. */
    data class Exertion(val id: Long?, val day: LocalDate? = null, val triggerOf: Long? = null) : TrackingPage

    /** All exertions, the latest first. */
    data object Exertions : TrackingPage

    /** Episode [id]: kind, start, end, severity and suspected triggers. */
    data class Episode(val id: Long) : TrackingPage

    /** All episodes, the latest first. */
    data object Episodes : TrackingPage

    /** The pacing threshold. */
    data object Pacing : TrackingPage

    /** The week evaluation of the week beginning on [monday]. */
    data class Week(val monday: LocalDate) : TrackingPage

    /** The history over [range]. */
    data class History(val range: HistoryRange) : TrackingPage

    /** The hint of [rule], with its sources and "Erledigt". */
    data class Hint(val rule: HintRule) : TrackingPage

    /** The doctor's summary, with its preview and the PDF. */
    data object Summary : TrackingPage
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
