package de.emilyshub.languages

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
import de.emilyshub.data.Saving
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * A language as its list shows it: and whether it can be deleted, which neither one with lessons can, nor one that is
 * the only language of a teacher, who needs one at least.
 */
data class LanguageItem(val language: Language, val deletable: Boolean)

/** A teacher as their list shows them: the [languages] they teach, and whether they have lessons. */
data class TeacherItem(val teacher: Teacher, val languages: List<Language>, val hasLessons: Boolean) {
    /** Whether they teach the language [languageId]. */
    fun teaches(languageId: Long) = languages.any { it.id == languageId }
}

/**
 * What a lesson's editor offers: the languages not archived, the teachers not archived with what they teach, and the
 * [topics] of all languages, by name, with the exams taken ([takenExams]), whose topics are offered no more.
 */
data class LessonChoices(
    val languages: List<Language>,
    val teachers: List<TeacherItem>,
    val topics: List<Topic> = emptyList(),
    val takenExams: Set<Long> = emptySet(),
) {
    /** The topics of the language [languageId] to choose: not those of an exam taken, unless among [kept]. */
    fun topicsOf(languageId: Long, kept: Set<Long> = emptySet()): List<Topic> =
        topics.filter { it.languageId == languageId && (it.examId !in takenExams || it.id in kept) }
}

/** A language on the start page, with what its lessons come to and its next exam not taken, if any. */
data class LanguageCard(val language: Language, val periods: Periods, val nextExam: ExamStatus? = null)

/** The start page of Sprachen (SPEC.md §15): what all lessons come to, and a card for each language not archived. */
data class LanguagesOverview(val all: Periods, val cards: List<LanguageCard>)

/** A lesson on its language's page, with its teacher. */
data class LessonRow(val lesson: Lesson, val teacher: Teacher)

/** What a teacher's lessons of one language come to. */
data class TeacherTally(val teacher: Teacher, val periods: Periods)

/**
 * A language's page (SPEC.md §15): its [lessons], the newest first, what they come to for each teacher who held them
 * ([byTeacher]), by name, and its [topics] with its exams.
 */
data class LanguagePage(
    val language: Language,
    val lessons: List<LessonRow>,
    val byTeacher: List<TeacherTally>,
    val topics: TopicsTab,
)

/** Everything of Sprachen, each table as read. */
private data class Snapshot(
    val languages: List<Language>,
    val teachers: List<Teacher>,
    val teaching: List<TeacherLanguage>,
    val lessons: List<Lesson>,
    val topics: List<Topic>,
    val exams: List<Exam>,
    val lessonTopics: List<LessonTopic>,
)

/** The tables of the topics and exams, as read. */
private data class Studies(val topics: List<Topic>, val exams: List<Exam>, val lessonTopics: List<LessonTopic>)

/**
 * Sprachen (SPEC.md §15): the languages, the teachers with the languages they teach, and the lessons held, each of one
 * language with one teacher; what they come to for the month, the year and in all. Each language has its topics, done
 * by hand only, and its exams, which group them; a lesson keeps which topics it treated. Languages and teachers are
 * archived rather than deleted while anything hangs on them. A lesson's photos and PDFs lie encrypted in the store of
 * [files], taken in when it is saved and deleted with it. [database] is only called on [io]; writes run to the end once started.
 */
class Languages(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val files: LessonFiles,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().languages()

    /** Today, by which the month and year are counted, and on which a new lesson is held. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The start page, as it changes. */
    fun overview(): Flow<LanguagesOverview> = observe { all ->
        val today = today()
        LanguagesOverview(
            all = Periods.of(all.lessons, today),
            cards = all.languages.filterNot { it.archived }.sortedWith(compareBy(InputText.byName) { it.name }).map { language ->
                LanguageCard(
                    language,
                    Periods.of(all.lessons.filter { it.languageId == language.id }, today),
                    ExamStatus.next(all.exams.filter { it.languageId == language.id }, all.topics, today),
                )
            },
        )
    }

    /** The page of the language [languageId], as it changes; none once it is gone. */
    fun page(languageId: Long): Flow<LanguagePage?> = observe { all ->
        val language = all.languages.find { it.id == languageId } ?: return@observe null
        val today = today()
        val byId = all.teachers.associateBy { it.id }
        // The tables are read one by one: a teacher just made may not be among them yet, for a moment.
        val own = all.lessons.filter { it.languageId == languageId && it.teacherId in byId }
        LanguagePage(
            language = language,
            lessons = own.sortedWith(compareByDescending<Lesson> { it.date }.thenByDescending { it.id })
                .map { LessonRow(it, byId.getValue(it.teacherId)) },
            byTeacher = own.groupBy { it.teacherId }
                .map { (teacherId, held) -> TeacherTally(byId.getValue(teacherId), Periods.of(held, today)) }
                .sortedWith(compareBy(InputText.byName) { it.teacher.name }),
            topics = TopicsTab.of(
                topics = all.topics.filter { it.languageId == languageId },
                exams = all.exams.filter { it.languageId == languageId },
                lessons = own,
                lessonTopics = all.lessonTopics,
                today = today,
            ),
        )
    }

    /** The lessons that treated the topic [topicId], the newest first, with their teachers, as they change. */
    fun lessonsOf(topicId: Long): Flow<List<LessonRow>> = observe { all ->
        val treated = all.lessonTopics.filter { it.topicId == topicId }.map { it.lessonId }.toSet()
        val byId = all.teachers.associateBy { it.id }
        all.lessons.filter { it.id in treated && it.teacherId in byId }
            .sortedWith(compareByDescending<Lesson> { it.date }.thenByDescending { it.id })
            .map { LessonRow(it, byId.getValue(it.teacherId)) }
    }

    /** All languages, by name, those archived after the others. */
    fun languages(): Flow<List<LanguageItem>> = observe { all ->
        val kept = all.lessons.map { it.languageId }.toSet() + onlyLanguages(all.teaching) +
            all.topics.map { it.languageId } + all.exams.map { it.languageId }
        all.languages.sortedWith(compareBy<Language> { it.archived }.thenBy(InputText.byName) { it.name })
            .map { LanguageItem(it, deletable = it.id !in kept) }
    }

    /** All teachers, by name, those archived after the others. */
    fun teachers(): Flow<List<TeacherItem>> = observe { all ->
        teacherItems(all.languages, all.teachers, all.teaching, all.lessons)
            .sortedWith(compareBy<TeacherItem> { it.teacher.archived }.thenBy(InputText.byName) { it.teacher.name })
    }

    /** What a lesson's editor offers, as it changes. */
    fun choices(): Flow<LessonChoices> = observe { all ->
        LessonChoices(
            languages = all.languages.filterNot { it.archived }.sortedWith(compareBy(InputText.byName) { it.name }),
            teachers = teacherItems(all.languages, all.teachers, all.teaching, all.lessons).filterNot { it.teacher.archived }
                .sortedWith(compareBy(InputText.byName) { it.teacher.name }),
            topics = all.topics.sortedWith(compareBy(InputText.byName) { it.name }),
            takenExams = all.exams.filter { it.taken }.map { it.id }.toSet(),
        )
    }

    /** A new language by [name]; refused if it is missing, too long, or reads as another's, archived or not. */
    suspend fun createLanguage(name: String): Saving<NameProblem> = write {
        database().withTransaction {
            when (val problem = nameProblem(name, except = null)) {
                null -> Saving.Saved(dao.insertLanguage(Language(name = InputText.singleLine(name))))
                else -> Saving.Refused(setOf(problem))
            }
        }
    }

    /** Renames the language [id]; refused as [createLanguage] is. */
    suspend fun renameLanguage(id: Long, name: String): Saving<NameProblem> = write {
        database().withTransaction {
            val saved = dao.language(id) ?: return@withTransaction Saving.Saved(id)
            when (val problem = nameProblem(name, except = id)) {
                null -> {
                    dao.updateLanguage(saved.copy(name = InputText.singleLine(name)))
                    Saving.Saved(id)
                }
                else -> Saving.Refused(setOf(problem))
            }
        }
    }

    /** Archives the language [id], or takes it back. */
    suspend fun setLanguageArchived(id: Long, archived: Boolean) = write {
        database().withTransaction { dao.language(id)?.let { dao.updateLanguage(it.copy(archived = archived)) } }
    }

    /**
     * Deletes the language [id], and who teaches it no more; refused, with nothing deleted, while it has lessons, topics or
     * exams, or is the only language of a teacher.
     */
    suspend fun deleteLanguage(id: Long): Boolean = write {
        database().withTransaction {
            if (dao.languageHasLessons(id) || dao.languageHasStudies(id) || id in onlyLanguages(dao.teaching())) {
                return@withTransaction false
            }
            dao.deleteLanguage(id)
            true
        }
    }

    /** The teacher [id] as their editor opens them; none once they are gone. */
    suspend fun teacherEditing(id: Long): TeacherEditing? = withContext(io) {
        val saved = dao.teacher(id) ?: return@withContext null
        TeacherEditing(id, TeacherDraft(saved.name, saved.note, dao.languagesOf(id).toSet()))
    }

    /**
     * Saves [draft] as [editing] has it: a new teacher, or the changes to the one opened; refused, with nothing saved, for
     * its problems. Languages deleted meanwhile are left out, as are those archived that they did not teach already. A
     * language taken away leaves their lessons of it as they are.
     */
    suspend fun saveTeacher(editing: TeacherEditing, draft: TeacherDraft): Saving<TeacherProblem> = write {
        database().withTransaction {
            val had = editing.id?.let { dao.languagesOf(it) }.orEmpty().toSet()
            val offered = dao.languages().filter { !it.archived || it.id in had }.map { it.id }.toSet()
            val checked = draft.copy(languageIds = draft.languageIds intersect offered)
            if (checked.problems.isNotEmpty()) return@withTransaction Saving.Refused(checked.problems)
            val id = editing.id
            val saved = if (id == null) {
                dao.insertTeacher(Teacher(name = checked.cleanedName, note = checked.cleanedNote))
            } else {
                val before = dao.teacher(id) ?: return@withTransaction Saving.Saved(id)
                dao.updateTeacher(before.copy(name = checked.cleanedName, note = checked.cleanedNote))
                dao.deleteTeachingOf(id)
                id
            }
            dao.insertTeaching(checked.languageIds.map { TeacherLanguage(saved, it) })
            Saving.Saved(saved)
        }
    }

    /** Archives the teacher [id], or takes them back. */
    suspend fun setTeacherArchived(id: Long, archived: Boolean) = write {
        database().withTransaction { dao.teacher(id)?.let { dao.updateTeacher(it.copy(archived = archived)) } }
    }

    /** Deletes the teacher [id]; refused, with nothing deleted, while they have lessons. */
    suspend fun deleteTeacher(id: Long): Boolean = write {
        database().withTransaction {
            if (dao.teacherHasLessons(id)) return@withTransaction false
            dao.deleteTeacher(id)
            true
        }
    }

    /** The teacher [teacherId] teaches the language [languageId] too ("Anderen Lehrer wählen"). */
    suspend fun teach(teacherId: Long, languageId: Long) = write {
        database().withTransaction {
            if (dao.teacher(teacherId) != null && dao.language(languageId) != null) {
                dao.insertTeaching(listOf(TeacherLanguage(teacherId, languageId)))
            }
        }
    }

    /** A new lesson, held today, of the language [languageId] if given. */
    fun newLesson(languageId: Long? = null) = LessonDraft(date = today(), languageId = languageId)

    /** The lesson [id] as its editor opens it, with the topics it treated and its attachments; none once it is deleted. */
    suspend fun editing(id: Long): LessonEditing? = withContext(io) {
        dao.lesson(id)?.let { lesson ->
            LessonEditing(id, LessonDraft.of(lesson, dao.topicsOfLesson(id).toSet(), dao.attachmentsOf(id).map(AttachmentRef::of)))
        }
    }

    /**
     * Saves [draft] as [editing] has it: a new lesson, or the changes to the one opened; refused, with nothing saved, for
     * its problems, and for a language or teacher not offered, unless it is the one it was saved with: so an old lesson
     * keeps an archived language, or a teacher who teaches it no more. One deleted meanwhile stays deleted.
     *
     * Its topics are those of its language: one chosen is refused if it is of another language, gone, or of an exam taken,
     * unless it was saved with it; one typed is that of the language which reads the same, else a new one, open and without
     * an exam. A lesson that treats a topic does not make it done.
     *
     * Its attachments are those of [draft], in its order: those it had and still has stay, those imported for it are
     * taken in, if they are still there, and those it had and has no more are deleted, files and all.
     */
    suspend fun save(editing: LessonEditing, draft: LessonDraft): Saving<LessonProblem> = write {
        files.lock.withLock { saveLocked(editing, draft) }
    }

    private suspend fun saveLocked(editing: LessonEditing, draft: LessonDraft): Saving<LessonProblem> {
        val gone = mutableListOf<String>()
        val saving = database().withTransaction {
            val id = editing.id
            val before = id?.let { dao.lesson(it) }
            if (id != null && before == null) return@withTransaction Saving.Saved(id)
            val problems = draft.problems.toMutableSet()
            val languageId = draft.languageId
            val teacherId = draft.teacherId
            if (languageId != null && languageId != before?.languageId) {
                if (dao.language(languageId)?.archived != false) problems += LessonProblem.LanguageNotOffered
            }
            if (teacherId != null && languageId != null && (teacherId != before?.teacherId || languageId != before.languageId)) {
                val teacher = dao.teacher(teacherId)
                if (teacher == null || teacher.archived || languageId !in dao.languagesOf(teacherId)) {
                    problems += LessonProblem.TeacherNotOffered
                }
            }
            val topics = languageId?.let { topicsFor(draft, it, before) }
            if (topics == null && languageId != null) problems += LessonProblem.TopicNotOffered
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            val saved = if (id == null) {
                dao.insertLesson(draft.toLesson())
            } else {
                dao.updateLesson(draft.toLesson(id))
                dao.deleteTopicsOfLesson(id)
                id
            }
            val (chosen, typed) = checkNotNull(topics)
            val made = typed.map { dao.insertTopic(Topic(languageId = checkNotNull(languageId), name = it)) }
            dao.insertLessonTopics((chosen + made).map { LessonTopic(saved, it) })
            gone += saveAttachments(saved, draft.attachments)
            Saving.Saved(saved)
        }
        if (saving is Saving.Saved) {
            files.taken(draft.attachments.map { it.file })
            gone.forEach(files.attachments::delete)
        }
        return saving
    }

    /**
     * Makes the attachments of the lesson [lessonId] those [wanted], in order; the files of those it no longer has, to be
     * deleted once this is done.
     */
    private suspend fun saveAttachments(lessonId: Long, wanted: List<AttachmentRef>): List<String> {
        val had = dao.attachmentsOf(lessonId)
        val byFile = had.associateBy { it.file }
        val kept = mutableListOf<LessonAttachment>()
        val added = mutableListOf<LessonAttachment>()
        wanted.distinctBy { it.file }.forEach { ref ->
            val old = byFile[ref.file]
            when {
                old != null -> kept += old.copy(position = kept.size + added.size)
                // Imported for it, though perhaps before Android ended the app and brought the editor back.
                ref.file !in dao.attachmentFiles() && files.attachments.has(ref.file) -> added += LessonAttachment(
                    lessonId = lessonId, kind = ref.kind, file = ref.file, bytes = ref.bytes, name = ref.name,
                    position = kept.size + added.size,
                )
            }
        }
        val dropped = had.filter { old -> kept.none { it.id == old.id } }
        dao.deleteAttachments(dropped.map { it.id })
        dao.updateAttachments(kept)
        dao.insertAttachments(added)
        return dropped.map { it.file }
    }

    /**
     * The topics of the language [languageId] that [draft] treats: those there already, and the names of those to be made;
     * none if one of them is not offered. [before] is the lesson as saved, if it is.
     */
    private suspend fun topicsFor(draft: LessonDraft, languageId: Long, before: Lesson?): Pair<Set<Long>, List<String>>? {
        val own = dao.topicsOf(languageId)
        val had = before?.takeIf { it.languageId == languageId }?.let { dao.topicsOfLesson(it.id) }.orEmpty().toSet()
        val taken = own.mapNotNull { it.examId }.distinct().filter { dao.exam(it)?.taken == true }.toSet()
        val offered = own.filter { it.examId !in taken || it.id in had }.map { it.id }.toSet()
        val chosen = draft.topicIds.toMutableSet()
        val typed = mutableListOf<String>()
        for (name in draft.newTopics.map(InputText::singleLine)) {
            val same = own.find { InputText.readTheSame(it.name, name) }
            when {
                same != null -> chosen += same.id
                typed.none { InputText.readTheSame(it, name) } -> typed += name
            }
        }
        return if (chosen.all { it in offered }) chosen to typed else null
    }

    /** A new topic of the language [languageId], open, for the exam [examId] if given; refused for its name. */
    suspend fun createTopic(languageId: Long, name: String, examId: Long? = null): Saving<NameProblem> = write {
        database().withTransaction {
            val problem = topicNameProblem(languageId, name, except = null)
            when {
                problem != null -> Saving.Refused(setOf(problem))
                dao.language(languageId) == null -> Saving.Saved(0)
                else -> {
                    val exam = examId?.takeIf { openExamOf(languageId, it) }
                    Saving.Saved(dao.insertTopic(Topic(languageId = languageId, name = InputText.singleLine(name), examId = exam)))
                }
            }
        }
    }

    /** Renames the topic [id]; refused for its name as [createTopic] is. One of an exam taken is left as it is. */
    suspend fun renameTopic(id: Long, name: String): Saving<NameProblem> = write {
        database().withTransaction {
            val topic = dao.topic(id) ?: return@withTransaction Saving.Saved(id)
            when (val problem = topicNameProblem(topic.languageId, name, except = id)) {
                null -> {
                    if (!isClosed(topic)) dao.updateTopic(topic.copy(name = InputText.singleLine(name)))
                    Saving.Saved(id)
                }
                else -> Saving.Refused(setOf(problem))
            }
        }
    }

    /** The topic [id] done, or open again; refused, with nothing changed, while its exam is taken. */
    suspend fun setTopicDone(id: Long, done: Boolean): Boolean = write {
        database().withTransaction {
            val topic = dao.topic(id) ?: return@withTransaction false
            if (isClosed(topic)) return@withTransaction false
            dao.updateTopic(topic.copy(done = done))
            true
        }
    }

    /**
     * The topics [ids] to the exam [examId] of their language, or to none: only to one not taken, and of an exam taken
     * only those open. The others stay where they are.
     */
    suspend fun moveTopics(ids: Collection<Long>, examId: Long?) = write {
        database().withTransaction {
            val moved = ids.mapNotNull { dao.topic(it) }.filter { topic ->
                (examId == null || openExamOf(topic.languageId, examId)) && (!isClosed(topic) || !topic.done)
            }
            dao.updateTopics(moved.map { it.copy(examId = examId) })
        }
    }

    /** All open topics of the exam [fromExamId] to the exam [examId], or to none, as [moveTopics] moves them. */
    suspend fun moveOpenTopics(fromExamId: Long, examId: Long?) = write {
        database().withTransaction {
            val exam = dao.exam(fromExamId) ?: return@withTransaction
            val open = dao.topicsOf(exam.languageId).filter { it.examId == fromExamId && !it.done }
            moveTopics(open.map { it.id }, examId)
        }
    }

    /** Deletes the topic [id]; the lessons lose it. Refused while its exam is taken. The screen asks first. */
    suspend fun deleteTopic(id: Long): Boolean = write {
        database().withTransaction {
            val topic = dao.topic(id) ?: return@withTransaction false
            if (isClosed(topic)) return@withTransaction false
            dao.deleteTopic(id)
            true
        }
    }

    /** A new exam of the language [languageId], as [draft] has it; refused, with nothing saved, for its problems. */
    suspend fun createExam(languageId: Long, draft: ExamDraft): Saving<ExamProblem> = write {
        database().withTransaction {
            when {
                draft.problems.isNotEmpty() -> Saving.Refused(draft.problems)
                dao.language(languageId) == null -> Saving.Saved(0)
                else -> Saving.Saved(
                    dao.insertExam(Exam(languageId = languageId, name = draft.cleanedName, date = checkNotNull(draft.date))),
                )
            }
        }
    }

    /** Changes the name and date of the exam [id] to what [draft] has; refused as [createExam] is. */
    suspend fun saveExam(id: Long, draft: ExamDraft): Saving<ExamProblem> = write {
        database().withTransaction {
            if (draft.problems.isNotEmpty()) return@withTransaction Saving.Refused(draft.problems)
            dao.exam(id)?.let { dao.updateExam(it.copy(name = draft.cleanedName, date = checkNotNull(draft.date))) }
            Saving.Saved(id)
        }
    }

    /** The exam [id] as its dialog opens it. */
    suspend fun examDraft(id: Long): ExamDraft? = withContext(io) { dao.exam(id)?.let { ExamDraft(it.name, it.date) } }

    /**
     * Marks the exam [id] taken, with [result] if any, or takes it back, which forgets its result; refused, with nothing
     * changed, for a result too long.
     */
    suspend fun setExamTaken(id: Long, taken: Boolean, result: String = ""): Boolean = write {
        val cleaned = InputText.multiLine(result).trim()
        if (taken && InputText.lengthOf(cleaned) > Exam.RESULT_MAX) return@write false
        database().withTransaction {
            dao.exam(id)?.let { dao.updateExam(it.copy(taken = taken, result = if (taken) cleaned else "")) }
        }
        true
    }

    /** Deletes the exam [id]; its topics stay, without an exam. The screen asks first. */
    suspend fun deleteExam(id: Long) = write { dao.deleteExam(id) }

    /** Whether [topic] is only to read, its exam being taken. */
    private suspend fun isClosed(topic: Topic) = topic.examId?.let { dao.exam(it)?.taken } == true

    /** Whether [examId] is an exam of the language [languageId] not taken. */
    private suspend fun openExamOf(languageId: Long, examId: Long) = dao.exam(examId)?.let { it.languageId == languageId && !it.taken } == true

    private suspend fun topicNameProblem(languageId: Long, name: String, except: Long?): NameProblem? =
        InputText.nameProblem(name, Topic.NAME_MAX, dao.topicsOf(languageId).filter { it.id != except }.map { it.name })

    /** Deletes the lesson [id], with its attachments. The screen asks first. */
    suspend fun delete(id: Long) = write {
        files.lock.withLock {
            val gone = database().withTransaction {
                dao.attachmentsOf(id).map { it.file }.also { dao.deleteLesson(id) }
            }
            gone.forEach(files.attachments::delete)
        }
    }

    /**
     * Deletes the files of attachments that no lesson has, none is importing, and that are older than [olderThan]: what a
     * crash or an editor lost left. Puts back those a restore that broke off left aside.
     */
    suspend fun clearUpAttachments(olderThan: Duration = Duration.ofDays(1)) = write {
        files.lock.withLock { files.clearUp(dao.attachmentFiles().toSet(), olderThan.toMillis()) }
    }

    /** The languages some teacher teaches alone, as their only one. */
    private fun onlyLanguages(teaching: List<TeacherLanguage>): Set<Long> =
        teaching.groupBy { it.teacherId }.values.mapNotNull { it.singleOrNull()?.languageId }.toSet()

    private suspend fun nameProblem(name: String, except: Long?): NameProblem? =
        InputText.nameProblem(name, Language.NAME_MAX, dao.languages().filter { it.id != except }.map { it.name })

    private fun teacherItems(
        languages: List<Language>,
        teachers: List<Teacher>,
        teaching: List<TeacherLanguage>,
        lessons: List<Lesson>,
    ): List<TeacherItem> {
        val byId = languages.associateBy { it.id }
        // The tables are read one by one: a language just deleted may still be taught here, for a moment.
        val taught = teaching.filter { it.languageId in byId }.groupBy({ it.teacherId }, { byId.getValue(it.languageId) })
        val used = lessons.map { it.teacherId }.toSet()
        return teachers.map { teacher ->
            TeacherItem(teacher, taught[teacher.id].orEmpty().sortedWith(compareBy(InputText.byName) { it.name }), teacher.id in used)
        }
    }

    /** All tables, as they change, made into what [transform] makes of them, on [io]. */
    private fun <T> observe(transform: (Snapshot) -> T): Flow<T> = flow {
        val studies = combine(dao.observeTopics(), dao.observeExams(), dao.observeLessonTopics(), ::Studies)
        emitAll(
            combine(dao.observeLanguages(), dao.observeTeachers(), dao.observeTeaching(), dao.observeLessons(), studies) {
                    languages, teachers, teaching, lessons, (topics, exams, lessonTopics) ->
                transform(Snapshot(languages, teachers, teaching, lessons, topics, exams, lessonTopics))
            },
        )
    }.flowOn(io)

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }
}
