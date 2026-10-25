package de.emilyshub.todos

import de.emilyshub.data.InputText
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.Checklists
import de.emilyshub.routines.Priority
import de.emilyshub.routines.RoutineDraft
import java.io.Serializable
import java.time.Instant

/** What keeps a [TodoDraft] from being saved, each shown at its field in the editor. */
enum class TodoProblem {
    TitleMissing,
    TitleTooLong,
    DescriptionTooLong,
    ChecklistTooLong,
    ChecklistItemTooLong,
    WeekdaysMissing,
    IntervalOutOfRange,
}

/**
 * A to-do as typed into the editor, before it is checked. The checks apply to the [cleaned] draft, and saving refuses
 * a draft with [problems] (SPEC.md §9: strict input checks). Serializable, so that the editor keeps it across rotation
 * and the app lock.
 */
data class TodoDraft(
    val title: String,
    val description: String = "",
    val due: Due = Due.None,
    val priority: Priority = Priority.Normal,
    /** Its sub-items in their order; one with the id of a saved item keeps its tick. */
    val checklist: List<ChecklistDraftItem> = emptyList(),
    /** Its tags; one deleted meanwhile is left out on saving. */
    val tagIds: Set<Long> = emptySet(),
    /** The places it reminds at (SPEC.md §4); one deleted meanwhile is left out on saving. */
    val placeIds: Set<Long> = emptySet(),
    /** The goal it is part of (SPEC.md §6), if any; one deleted meanwhile is left out on saving. */
    val goalId: Long? = null,
    /** How it comes again once ticked off, if it does; only with a [due] day, left out without. */
    val recurrence: Recurrence? = null,
) : Serializable {

    val problems: Set<TodoProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(title)) add(TodoProblem.TitleMissing)
                if (InputText.lengthOf(title) > TITLE_MAX) add(TodoProblem.TitleTooLong)
                if (InputText.lengthOf(description) > DESCRIPTION_MAX) add(TodoProblem.DescriptionTooLong)
                if (checklist.size > Checklists.ITEMS_MAX) add(TodoProblem.ChecklistTooLong)
                if (checklist.any { InputText.lengthOf(it.text) > Checklists.ITEM_LENGTH_MAX }) add(TodoProblem.ChecklistItemTooLong)
                when (recurrence) {
                    is Recurrence.Weekdays -> if (recurrence.days.isEmpty()) add(TodoProblem.WeekdaysMissing)
                    is Recurrence.EveryNDays -> if (recurrence.days !in RoutineDraft.INTERVAL_DAYS) add(TodoProblem.IntervalOutOfRange)
                    // Their day comes from the day the to-do is due.
                    null, Recurrence.Daily, is Recurrence.Monthly, is Recurrence.Yearly -> Unit
                }
            }
        }

    /**
     * Without surrounding spaces; the title and each checklist item on a single line, empty items left out; without a
     * recurrence unless due.
     */
    fun cleaned() = copy(
        title = InputText.singleLine(title),
        description = description.trim(),
        checklist = checklist.map(ChecklistDraftItem::cleaned).filter { InputText.isVisible(it.text) },
        recurrence = recurrence.takeIf { due != Due.None },
    )

    /**
     * The draft due [due] instead. If [recurrenceFollows], one coming again monthly or yearly comes again on the day of
     * the month or of the year it is due on now, as when a recurrence is just chosen; otherwise it keeps its day, as when
     * one of a series is put off.
     */
    fun dueOn(due: Due, recurrenceFollows: Boolean): TodoDraft {
        val moved = due.date?.takeIf { recurrenceFollows && it != this.due.date }
        return copy(due = due, recurrence = moved?.let { recurrence?.anchoredTo(it) } ?: recurrence)
    }

    /**
     * The to-do this draft makes: [id] if saved already, ticked off [doneAt] then if it is, its reminder as [reminding],
     * followed by [nextId] if it came again.
     */
    fun toTodo(id: Long = 0, doneAt: Instant? = null, reminding: Reminding = Reminding(), nextId: Long? = null) =
        Todo(id, title, description, due.date, (due as? Due.At)?.time, priority, doneAt, reminding, goalId, recurrence, nextId)

    companion object {
        const val TITLE_MAX = 80
        const val DESCRIPTION_MAX = 1000
    }
}

/** The to-do with its [checklist], [tagIds] and [placeIds] as the editor starts out with it. */
fun Todo.toDraft(checklist: List<TodoChecklistItem>, tagIds: Set<Long>, placeIds: Set<Long>) = TodoDraft(
    title, description, due, priority, checklist.map { ChecklistDraftItem(it.id, it.text) }, tagIds, placeIds, goalId, recurrence,
)
