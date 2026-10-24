package de.emilyshub.todos

import de.emilyshub.data.InputText
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.Checklists
import de.emilyshub.routines.Priority
import java.io.Serializable
import java.time.Instant

/** What keeps a [TodoDraft] from being saved, each shown at its field in the editor. */
enum class TodoProblem {
    TitleMissing,
    TitleTooLong,
    DescriptionTooLong,
    ChecklistTooLong,
    ChecklistItemTooLong,
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
) : Serializable {

    val problems: Set<TodoProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(title)) add(TodoProblem.TitleMissing)
                if (InputText.lengthOf(title) > TITLE_MAX) add(TodoProblem.TitleTooLong)
                if (InputText.lengthOf(description) > DESCRIPTION_MAX) add(TodoProblem.DescriptionTooLong)
                if (checklist.size > Checklists.ITEMS_MAX) add(TodoProblem.ChecklistTooLong)
                if (checklist.any { InputText.lengthOf(it.text) > Checklists.ITEM_LENGTH_MAX }) add(TodoProblem.ChecklistItemTooLong)
            }
        }

    /** Without surrounding spaces; the title and each checklist item on a single line, empty items left out. */
    fun cleaned() = copy(
        title = InputText.singleLine(title),
        description = description.trim(),
        checklist = checklist.map(ChecklistDraftItem::cleaned).filter { InputText.isVisible(it.text) },
    )

    /** The to-do this draft makes, [id] if it is saved already, ticked off [doneAt] then if it is. */
    fun toTodo(id: Long = 0, doneAt: Instant? = null) =
        Todo(id, title, description, due.date, (due as? Due.At)?.time, priority, doneAt)

    companion object {
        const val TITLE_MAX = 80
        const val DESCRIPTION_MAX = 1000
    }
}

/** The to-do with its [checklist] and [tagIds] as the editor starts out with it. */
fun Todo.toDraft(checklist: List<TodoChecklistItem>, tagIds: Set<Long>) = TodoDraft(
    title, description, due, priority, checklist.map { ChecklistDraftItem(it.id, it.text) }, tagIds,
)
