package de.emilyshub.languages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import de.emilyshub.wishes.Wish
import java.io.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * An exam of a language (SPEC.md §15): by [name], on [date]; [taken] by hand, with a [result] as free text if wished.
 * Its topics are only to read once it is taken. Deleted, its topics stay, without an exam. It may be a part of the goal
 * [goalId] (SPEC.md §6), done once taken; without it once the goal is deleted.
 */
@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(entity = Language::class, parentColumns = ["id"], childColumns = ["language_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["goal_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("language_id"), Index("goal_id")],
)
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "language_id") val languageId: Long,
    val name: String,
    val date: LocalDate,
    @ColumnInfo(defaultValue = "0") val taken: Boolean = false,
    val result: String = "",
    @ColumnInfo(name = "goal_id") val goalId: Long? = null,
) {
    companion object {
        /** The longest an exam's name may be, in characters. */
        const val NAME_MAX = 80

        /** The longest an exam's result may be, in characters. */
        const val RESULT_MAX = 1000
    }
}

/**
 * A topic of a language (SPEC.md §15): by [name], which no other of the language reads the same as; for the exam
 * [examId] if any; [done] only by hand. The lessons that treated it are in [LessonTopic].
 */
@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(entity = Language::class, parentColumns = ["id"], childColumns = ["language_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Exam::class, parentColumns = ["id"], childColumns = ["exam_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("language_id"), Index("exam_id")],
)
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "language_id") val languageId: Long,
    val name: String,
    @ColumnInfo(name = "exam_id") val examId: Long? = null,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
) {
    companion object {
        /** The longest a topic's name may be, in characters. */
        const val NAME_MAX = 100
    }
}

/** That the lesson [lessonId] treated the topic [topicId]. Gone with either. */
@Entity(
    tableName = "lesson_topics",
    primaryKeys = ["lesson_id", "topic_id"],
    foreignKeys = [
        ForeignKey(entity = Lesson::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Topic::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("topic_id")],
)
data class LessonTopic(
    @ColumnInfo(name = "lesson_id") val lessonId: Long,
    @ColumnInfo(name = "topic_id") val topicId: Long,
)

/** What keeps an [ExamDraft] from being saved, each shown at its field. */
enum class ExamProblem {
    NameMissing,
    NameTooLong,
    DateMissing,
}

/** An exam as typed into its dialog: [name], [date] and the goal it is part of, if any. Serializable, so the dialog keeps it. */
data class ExamDraft(val name: String = "", val date: LocalDate? = null, val goalId: Long? = null) : Serializable {
    /** Its name on a single line, as it is saved. */
    val cleanedName: String get() = InputText.singleLine(name)

    /** What keeps it from being saved; none if it can be. */
    val problems: Set<ExamProblem>
        get() = buildSet {
            when {
                !InputText.isVisible(cleanedName) -> add(ExamProblem.NameMissing)
                InputText.lengthOf(cleanedName) > Exam.NAME_MAX -> add(ExamProblem.NameTooLong)
            }
            if (date == null) add(ExamProblem.DateMissing)
        }
}

/**
 * An exam with how far off it is and how its topics stand: [days] until it, 0 on the day, below 0 since it was; [open]
 * of all its [topics] not done yet.
 */
data class ExamStatus(val exam: Exam, val days: Long, val open: Int, val topics: Int) {
    companion object {
        /** [exam] as it stands [today], with [topics] its own. */
        fun of(exam: Exam, topics: List<Topic>, today: LocalDate) = ExamStatus(
            exam = exam,
            days = ChronoUnit.DAYS.between(today, exam.date),
            open = topics.count { !it.done },
            topics = topics.size,
        )

        /** The next exam not taken yet of [exams], the earliest, past or not, as it stands [today]; none if all are taken. */
        fun next(exams: List<Exam>, topics: List<Topic>, today: LocalDate): ExamStatus? =
            exams.filterNot { it.taken }.minWithOrNull(compareBy<Exam> { it.date }.thenBy { it.id })
                ?.let { exam -> of(exam, topics.filter { it.examId == exam.id }, today) }
    }
}

/** A topic on its list: in how many lessons it was treated ([lessons]) and when last ([last]), none if never. */
data class TopicRow(val topic: Topic, val lessons: Int, val last: LocalDate?) {
    /** Whether it can be done or opened again: not while its exam is taken. */
    fun checkable(exams: Map<Long, Exam>) = topic.examId?.let { exams[it]?.taken } != true
}

/** The topics of one exam, or of none ([exam] none), the open before the done, each by name. */
data class TopicGroup(val exam: ExamStatus?, val topics: List<TopicRow>)

/**
 * The tab "Themen" of a language's page (SPEC.md §15): the [next] exam on top; the topics grouped by exam, those not
 * taken by date ([upcoming]), then those without one ([withoutExam]), then those taken ([taken], the latest first).
 */
data class TopicsTab(
    val next: ExamStatus?,
    val upcoming: List<TopicGroup>,
    val withoutExam: TopicGroup,
    val taken: List<TopicGroup>,
) {
    /** Every exam of the language, by id. */
    val exams: Map<Long, Exam> get() = (upcoming + taken).mapNotNull { it.exam?.exam }.associateBy { it.id }

    companion object {
        /** The tab of the language whose [topics] and [exams] these are, with what [lessonTopics] of [lessons] treated them. */
        fun of(topics: List<Topic>, exams: List<Exam>, lessons: List<Lesson>, lessonTopics: List<LessonTopic>, today: LocalDate): TopicsTab {
            val dates = lessons.associate { it.id to it.date }
            val treated = lessonTopics.filter { it.lessonId in dates }.groupBy({ it.topicId }, { dates.getValue(it.lessonId) })
            val rows = topics.map { TopicRow(it, treated[it.id].orEmpty().size, treated[it.id]?.max()) }
            val order = compareBy<TopicRow> { it.topic.done }.thenBy(InputText.byName) { it.topic.name }
            fun group(exam: Exam) = TopicGroup(
                ExamStatus.of(exam, topics.filter { it.examId == exam.id }, today),
                rows.filter { it.topic.examId == exam.id }.sortedWith(order),
            )
            val byDate = compareBy<Exam> { it.date }.thenBy { it.id }
            return TopicsTab(
                next = ExamStatus.next(exams, topics, today),
                upcoming = exams.filterNot { it.taken }.sortedWith(byDate).map(::group),
                withoutExam = TopicGroup(null, rows.filter { it.topic.examId == null }.sortedWith(order)),
                taken = exams.filter { it.taken }.sortedWith(byDate.reversed()).map(::group),
            )
        }
    }
}
