package de.emilyshub.places

import java.io.Serializable

/**
 * A place being edited (SPEC.md §4): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]). Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class PlaceEditing(val id: Long?, val original: PlaceDraft?) : Serializable {
    companion object {
        val New = PlaceEditing(id = null, original = null)
    }
}
