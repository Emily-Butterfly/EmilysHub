package de.emilyshub.languages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import de.emilyshub.ledger.Money
import java.io.Serializable
import java.time.LocalDate

/**
 * A language taught (SPEC.md §15): by its [name], which no other reads the same as. Archived, it is offered no more,
 * and its sessions stay.
 */
@Entity(tableName = "languages")
data class Language(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
) {
    companion object {
        /** The longest a language's name may be, in characters. */
        const val NAME_MAX = 50
    }
}

/**
 * A teacher (SPEC.md §15): by [name], with a [note], teaching one language or more ([TeacherLanguage]). Archived, they
 * are offered no more, and their sessions stay.
 */
@Entity(tableName = "teachers")
data class Teacher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
) {
    companion object {
        /** The longest a teacher's name may be, in characters. */
        const val NAME_MAX = 80

        /** The longest a teacher's note may be, in characters. */
        const val NOTE_MAX = 1000
    }
}

/** That the teacher [teacherId] teaches the language [languageId]. Gone with either; a language with sessions never goes. */
@Entity(
    tableName = "teacher_languages",
    primaryKeys = ["teacher_id", "language_id"],
    foreignKeys = [
        ForeignKey(entity = Teacher::class, parentColumns = ["id"], childColumns = ["teacher_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Language::class, parentColumns = ["id"], childColumns = ["language_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("language_id")],
)
data class TeacherLanguage(
    @ColumnInfo(name = "teacher_id") val teacherId: Long,
    @ColumnInfo(name = "language_id") val languageId: Long,
)

/**
 * A session of a language held (SPEC.md §15), called a lesson here so as not to be taken for an editor's session: on
 * [date], for [minutes], of the language [languageId] with the teacher [teacherId], what it cost if known ([priceCents];
 * for the evaluation only, never a booking) and a [note] in Markdown. Neither its language nor its teacher can be deleted
 * while it is there.
 */
@Entity(
    tableName = "lessons",
    foreignKeys = [
        ForeignKey(entity = Language::class, parentColumns = ["id"], childColumns = ["language_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Teacher::class, parentColumns = ["id"], childColumns = ["teacher_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("language_id"), Index("teacher_id")],
)
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val minutes: Int,
    @ColumnInfo(name = "language_id") val languageId: Long,
    @ColumnInfo(name = "teacher_id") val teacherId: Long,
    @ColumnInfo(name = "price_cents") val priceCents: Long? = null,
    val note: String = "",
)

/** What keeps a [LessonDraft] from being saved, each shown at its field in the editor. */
enum class LessonProblem {
    DateMissing,
    MinutesInvalid,
    LanguageMissing,
    TeacherMissing,
    PriceInvalid,
    NoteTooLong,

    /** Its language is archived or gone, and it was not the one saved: only when saving, by [Languages.save]. */
    LanguageNotOffered,

    /** Its teacher is archived, gone or does not teach its language, and was not the one saved: only when saving. */
    TeacherNotOffered,

    /** A topic typed is missing or too long as a name. */
    TopicInvalid,

    /**
     * A topic chosen or typed is not of its language, or is gone, or is of an exam taken, and it was not saved with it:
     * only when saving.
     */
    TopicNotOffered,
}

/**
 * A lesson as typed into its editor (SPEC.md §15): its [date], its [minutes] as typed, its language and teacher, a
 * [price] in euros with a comma, none if left empty, its [note], and the topics it treated: those of its language
 * chosen ([topicIds]) and those typed anew ([newTopics]), which saving puts on the language's list. Serializable, so that
 * the editor keeps it.
 */
data class LessonDraft(
    val date: LocalDate?,
    val minutes: String = "",
    val languageId: Long? = null,
    val teacherId: Long? = null,
    val price: String = "",
    val note: String = "",
    val topicIds: Set<Long> = emptySet(),
    val newTopics: List<String> = emptyList(),
) : Serializable {

    /** Whether it has topics, chosen or typed. */
    val hasTopics: Boolean get() = topicIds.isNotEmpty() || newTopics.isNotEmpty()

    /** Of the language [id]: another loses its topics, which are those of the language it had. */
    fun withLanguage(id: Long): LessonDraft =
        if (id == languageId) this else copy(languageId = id, topicIds = emptySet(), newTopics = emptyList())

    /** Its note as it is saved. */
    val cleanedNote: String get() = InputText.multiLine(note)

    /** Its price in cents; none if left empty or not a price. */
    val priceCents: Long? get() = price.takeIf { it.isNotBlank() }?.let(Money::parse)

    private val minutesValue: Int? get() = minutes.trim().toIntOrNull()?.takeIf { it in MINUTES }

    /** What keeps it from being saved; none if it can be. */
    val problems: Set<LessonProblem>
        get() = buildSet {
            if (date == null) add(LessonProblem.DateMissing)
            if (minutesValue == null) add(LessonProblem.MinutesInvalid)
            if (languageId == null) add(LessonProblem.LanguageMissing)
            if (teacherId == null) add(LessonProblem.TeacherMissing)
            if (price.isNotBlank() && Money.parse(price) == null) add(LessonProblem.PriceInvalid)
            if (InputText.lengthOf(cleanedNote) > NOTE_MAX) add(LessonProblem.NoteTooLong)
            if (newTopics.any { InputText.nameProblem(it, Topic.NAME_MAX, emptyList()) != null }) add(LessonProblem.TopicInvalid)
        }

    /** The lesson it saves as, under [id]; only without [problems]. */
    fun toLesson(id: Long = 0): Lesson {
        require(problems.isEmpty()) { "Invalid lesson: $problems" }
        return Lesson(
            id = id, date = checkNotNull(date), minutes = checkNotNull(minutesValue), languageId = checkNotNull(languageId),
            teacherId = checkNotNull(teacherId), priceCents = priceCents, note = cleanedNote,
        )
    }

    companion object {
        /** How long a lesson may be, in minutes: up to a whole day. */
        val MINUTES = 1..1440

        /** The lengths offered at a tap (SPEC.md §15). */
        val QUICK_MINUTES = listOf(30, 45, 60, 90)

        /** The longest a lesson's note may be, in characters (SPEC.md §15). */
        const val NOTE_MAX = 100_000

        /** [lesson] as its editor opens it, with the topics [topicIds] it treated. */
        fun of(lesson: Lesson, topicIds: Set<Long> = emptySet()) = LessonDraft(
            date = lesson.date, minutes = lesson.minutes.toString(), languageId = lesson.languageId, teacherId = lesson.teacherId,
            price = lesson.priceCents?.let(Money::typed).orEmpty(), note = lesson.note, topicIds = topicIds,
        )
    }
}

/**
 * A lesson being edited (SPEC.md §15): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]). Serializable, so that the editor keeps it.
 */
data class LessonEditing(val id: Long?, val original: LessonDraft?) : Serializable {
    companion object {
        val New = LessonEditing(id = null, original = null)
    }
}

/** What keeps a [TeacherDraft] from being saved (SPEC.md §15), each shown at its field in the editor. */
enum class TeacherProblem {
    NameMissing,
    NameTooLong,
    NoteTooLong,
    LanguagesMissing,
}

/** A teacher as typed into their editor: [name], [note] and the languages they teach. Serializable, so the editor keeps it. */
data class TeacherDraft(val name: String = "", val note: String = "", val languageIds: Set<Long> = emptySet()) : Serializable {
    /** Their name on a single line, as it is saved. */
    val cleanedName: String get() = InputText.singleLine(name)

    /** Their note as it is saved. */
    val cleanedNote: String get() = InputText.multiLine(note)

    /** What keeps their name from being saved; none if it can be. */
    val nameProblem: TeacherProblem?
        get() = when {
            !InputText.isVisible(cleanedName) -> TeacherProblem.NameMissing
            InputText.lengthOf(cleanedName) > Teacher.NAME_MAX -> TeacherProblem.NameTooLong
            else -> null
        }

    /** What keeps them from being saved; none if they can be. */
    val problems: Set<TeacherProblem>
        get() = buildSet {
            nameProblem?.let(::add)
            if (InputText.lengthOf(cleanedNote) > Teacher.NOTE_MAX) add(TeacherProblem.NoteTooLong)
            if (languageIds.isEmpty()) add(TeacherProblem.LanguagesMissing)
        }
}

/** A teacher being edited: a new one ([New]), or the one saved under [id]. Serializable, so that the editor keeps it. */
data class TeacherEditing(val id: Long?, val original: TeacherDraft?) : Serializable {
    companion object {
        val New = TeacherEditing(id = null, original = null)
    }
}
