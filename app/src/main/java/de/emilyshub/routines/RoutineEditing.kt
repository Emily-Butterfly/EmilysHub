package de.emilyshub.routines

import java.io.Serializable

/**
 * A routine being edited (SPEC.md §2): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]) and how it was in use then ([status]). What may be saved depends on it ([Routines.problems]).
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class RoutineEditing(val id: Long?, val original: RoutineDraft?, val status: RoutineStatus?) : Serializable {
    companion object {
        val New = RoutineEditing(id = null, original = null, status = null)
    }
}
