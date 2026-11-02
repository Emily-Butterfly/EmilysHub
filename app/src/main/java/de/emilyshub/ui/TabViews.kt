package de.emilyshub.ui

import de.emilyshub.todos.TodoOrder
import de.emilyshub.ui.notes.NotesView
import java.io.Serializable

/**
 * How each tab shows what it lists, kept while an entry is open over it and while another tab shows: the tag the
 * routines, to-dos and goals are filtered by, if any, the to-dos' order, and the notes' tab and folder. Not
 * navigation: the screens over the tabs are the back stack's. Serializable, so that it outlives rotation and the lock.
 */
data class TabViews(
    val routineTagFilter: Long? = null,
    val todoOrder: TodoOrder = TodoOrder.ByDue,
    val todoTagFilter: Long? = null,
    val wishTagFilter: Long? = null,
    val notes: NotesView = NotesView(),
) : Serializable
