package de.emilyshub.todos

import java.io.Serializable

/**
 * A to-do being edited (SPEC.md §5): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]), the [seriesNumber]th of its series, as its end tells how many are left. Serializable, so that the editor
 * keeps it across rotation and the app lock.
 */
data class TodoEditing(val id: Long?, val original: TodoDraft?, val seriesNumber: Int = 1) : Serializable {
    companion object {
        val New = TodoEditing(id = null, original = null)
    }
}
