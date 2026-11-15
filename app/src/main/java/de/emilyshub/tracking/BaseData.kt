package de.emilyshub.tracking

/**
 * The fields of the base data (SPEC.md §17, "Grunddaten"), once written and kept up, each in Markdown; [key] is where it
 * is kept in Tracking's settings.
 */
enum class BaseField(val key: String) {
    Course("base.course"),
    Family("base.family"),
    Sleep("base.sleep"),
    Nutrition("base.nutrition"),
    Other("base.other"),
}

/** The base data, read and written only while Tracking is open. */
class TrackingBaseData(private val tracking: TrackingStore) {

    /** Each field's text, empty if never written; none once locked. */
    suspend fun all(): Map<BaseField, String>? = tracking.withDatabase { database ->
        BaseField.entries.associateWith { database.settings().get(it.key).orEmpty() }
    }

    /** Writes [text] as [field]'s, into [database]. */
    suspend fun write(database: TrackingDatabase, field: BaseField, text: String) {
        database.settings().put(TrackingSetting(field.key, text.take(TEXT_MAX)))
    }

    companion object {
        /** The longest a field can be. */
        const val TEXT_MAX = 40_000
    }
}
