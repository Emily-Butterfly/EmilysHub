package de.emilyshub.notes

import java.io.Serializable

/**
 * A note being edited (SPEC.md §7): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]); one [archived], itself or in a folder archived, only to read. Serializable, so that the editor keeps it
 * across rotation and the app lock.
 */
data class NoteEditing(val id: Long?, val original: NoteDraft?, val archived: Boolean = false) : Serializable {
    companion object {
        val New = NoteEditing(id = null, original = null)
    }
}
