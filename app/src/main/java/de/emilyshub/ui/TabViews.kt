package de.emilyshub.ui

import de.emilyshub.todos.TodoOrder
import de.emilyshub.ui.doku.DokuPages
import de.emilyshub.ui.notes.NotesView
import de.emilyshub.ui.tracking.TrackingPages
import java.io.Serializable

/**
 * How each tab shows what it lists, kept while an entry is open over it and while another tab shows: the tag the
 * routines, to-dos and goals are filtered by, if any, the to-dos' order, and the notes' tab and folder. Not
 * navigation: the screens over the tabs are the back stack's. And what is open in "Sonstiges" ([more]), none while its
 * list shows, and the pages of the Doku and Tracking open there ([doku], [tracking]), only by their ids and days. Serializable, so that it outlives rotation
 * and the lock.
 */
data class TabViews(
    val routineTagFilter: Long? = null,
    val todoOrder: TodoOrder = TodoOrder.ByDue,
    val todoTagFilter: Long? = null,
    val wishTagFilter: Long? = null,
    val notes: NotesView = NotesView(),
    val more: MoreEntry? = null,
    val doku: DokuPages = DokuPages(),
    val tracking: TrackingPages = TrackingPages(),
) : Serializable {
    /** With Sonstiges' list showing again, and the pages of the Doku and Tracking closed. */
    fun closeMore(): TabViews = copy(more = null, doku = DokuPages(), tracking = TrackingPages())

    /**
     * Back within Sonstiges: within the Doku or Tracking to the page it was opened from first, else to the list
     * ([closeMore]).
     */
    fun backInMore(): TabViews = when {
        more == MoreEntry.Doku && doku.canGoBack -> copy(doku = doku.back())
        more == MoreEntry.Tracking && tracking.canGoBack -> copy(tracking = tracking.back())
        else -> closeMore()
    }
}
