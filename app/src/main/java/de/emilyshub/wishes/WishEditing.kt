package de.emilyshub.wishes

import java.io.Serializable

/**
 * A wish being edited (SPEC.md §6): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]). Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class WishEditing(val id: Long?, val original: WishDraft?) : Serializable {
    companion object {
        val New = WishEditing(id = null, original = null)
    }
}
