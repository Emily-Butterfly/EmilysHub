package de.emilyshub.languages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

/** What an attachment is (SPEC.md §15): a photo, kept as JPEG, or a PDF. By [stored] in the database. */
enum class AttachmentKind(val stored: String) {
    Photo("photo"),
    Pdf("pdf"),
}

/**
 * A photo or PDF attached to the lesson [lessonId] (SPEC.md §15), at [position] among its own: kept encrypted in the
 * [AttachmentStore] under [file], [bytes] large as it is shown, under the [name] it came by. Gone with its lesson.
 */
@Entity(
    tableName = "lesson_attachments",
    foreignKeys = [
        ForeignKey(entity = Lesson::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("lesson_id"), Index(value = ["file"], unique = true)],
)
data class LessonAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "lesson_id") val lessonId: Long,
    val kind: AttachmentKind,
    val file: String,
    val bytes: Long,
    val name: String,
    val position: Int,
)

/**
 * An attachment as a lesson's editor holds it: one saved already, or one imported but not saved yet, whose [file] lies
 * in the store without a lesson until the lesson is saved. Serializable, so that the editor keeps it.
 */
data class AttachmentRef(val kind: AttachmentKind, val file: String, val bytes: Long, val name: String) : Serializable {
    companion object {
        /** [attachment] as its lesson's editor holds it. */
        fun of(attachment: LessonAttachment) = AttachmentRef(attachment.kind, attachment.file, attachment.bytes, attachment.name)
    }
}
