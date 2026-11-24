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
 * list shows, and the pages of the Doku and Tracking open there ([doku], [tracking]), only by their ids and days; whether
 * Mein Warum is being edited ([warumEditing]), which it is not as it opens. Serializable, so that it outlives rotation
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
    val warumEditing: Boolean = false,
) : Serializable {
    /** With Sonstiges' list showing again, the pages of the Doku and Tracking closed, and Mein Warum no longer edited. */
    fun closeMore(): TabViews = copy(more = null, doku = DokuPages(), tracking = TrackingPages(), warumEditing = false)

    /**
     * Back within Sonstiges: within the Doku or Tracking to the page it was opened from first, from editing Mein Warum to
     * looking at it, else to the list ([closeMore]).
     */
    fun backInMore(): TabViews = when {
        more == MoreEntry.Doku && doku.canGoBack -> copy(doku = doku.back())
        more == MoreEntry.Warum && warumEditing -> copy(warumEditing = false)
        more == MoreEntry.Tracking && tracking.canGoBack -> copy(tracking = tracking.back())
        else -> closeMore()
    }
}
