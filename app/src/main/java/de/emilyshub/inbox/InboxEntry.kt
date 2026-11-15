package de.emilyshub.inbox

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A thought in the Eingang (SPEC.md §16), as it was captured, to be sorted in later: its [text] and when it was
 * captured. It reminds of nothing and has neither tags nor links.
 */
@Entity(tableName = "inbox")
data class InboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Instant,
) {
    companion object {
        /** The longest text, a thought rather than a note. */
        const val TEXT_MAX = 1000
    }
}
