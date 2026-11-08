package de.emilyshub.doku

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import de.emilyshub.notes.Block
import de.emilyshub.notes.Markdown
import java.time.LocalDate
import java.time.LocalTime

/**
 * An appointment of a patient (SPEC.md §14): a day, in the past or to come, a time of day if given, and what was done,
 * in Markdown. Only a date to document at: nothing reminds of it.
 */
@Entity(
    tableName = "appointment",
    foreignKeys = [
        ForeignKey(entity = Patient::class, parentColumns = ["id"], childColumns = ["patient_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("patient_id")],
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "patient_id") val patientId: Long,
    val date: LocalDate,
    val time: LocalTime? = null,
    val text: String = "",
) {
    companion object {
        /** As long as one of a patient's texts may be. */
        const val TEXT_MAX = Patient.TEXT_MAX

        /** What keeps [text] from being saved as an appointment's, or as the template: too long, once cleaned. */
        fun textTooLong(text: String): Boolean = Patient.textTooLong(text)

        /** The latest first: by day, on the same day by time, those without a time last; the one added later first. */
        val LATEST_FIRST: Comparator<Appointment> = compareByDescending<Appointment> { it.date }
            .thenComparator { a, b ->
                when {
                    a.time == b.time -> 0
                    a.time == null -> 1
                    b.time == null -> -1
                    else -> b.time.compareTo(a.time)
                }
            }
            .thenByDescending { it.id }
    }
}

/** An appointment in the tab Termine: its day, its time if any, and the line of its text that shows it ([previewOf]). */
data class AppointmentRow(val id: Long, val date: LocalDate, val time: LocalTime?, val preview: String?)

/**
 * The line an appointment shows by in a list: the first one with anything on it that is no heading, without its
 * Markdown, so that one written from the template does not always read "Befund"; else the first heading; none if the
 * text holds nothing, which the list shows as "(leer)".
 */
fun previewOf(text: String): String? {
    var heading: String? = null
    for (line in text.lineSequence()) {
        if (line.isBlank()) continue
        val block = Markdown.parse(line).firstOrNull() ?: continue
        val plain = InputText.singleLine(plainLine(block))
        if (!InputText.isVisible(plain)) continue
        if (block !is Block.Heading) return plain
        if (heading == null) heading = plain
    }
    return heading
}

/** What [block], read from one line, says, without its marks; nothing for a rule. */
private fun plainLine(block: Block): String = when (block) {
    is Block.Heading -> Markdown.plainText(block.text)
    is Block.Paragraph -> Markdown.plainText(block.text)
    is Block.Quote -> Markdown.plainText(block.text)
    is Block.Bullets -> block.items.joinToString(" ") { Markdown.plainText(it.text) }
    is Block.Numbered -> block.items.joinToString(" ") { Markdown.plainText(it.text) }
    // A fence alone, as "```kotlin", says nothing; its code is read line by line as text.
    is Block.Code -> ""
    Block.Rule -> ""
}
