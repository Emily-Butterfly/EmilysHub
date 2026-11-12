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
import kotlinx.coroutines.withContext
import java.time.Clock
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

/** What a lesson's editor offers: the languages not archived, and the teachers not archived with what they teach. */
data class LessonChoices(val languages: List<Language>, val teachers: List<TeacherItem>)

/** A language on the start page, with what its lessons come to. */
data class LanguageCard(val language: Language, val periods: Periods)

/** The start page of Sprachen (SPEC.md §15): what all lessons come to, and a card for each language not archived. */
data class LanguagesOverview(val all: Periods, val cards: List<LanguageCard>)

/** A lesson on its language's page, with its teacher. */
data class LessonRow(val lesson: Lesson, val teacher: Teacher)

/** What a teacher's lessons of one language come to. */
data class TeacherTally(val teacher: Teacher, val periods: Periods)

/**
 * A language's page (SPEC.md §15): its [lessons], the newest first, and what they come to for each teacher who held
 * them ([byTeacher]), by name.
 */
data class LanguagePage(val language: Language, val lessons: List<LessonRow>, val byTeacher: List<TeacherTally>)

/**
 * Sprachen (SPEC.md §15): the languages, the teachers with the languages they teach, and the lessons held, each of one
 * language with one teacher; what they come to for the month, the year and in all. Languages and teachers are archived
 * rather than deleted while lessons hang on them. [database] is only called on [io]; writes run to the end once started.
 */
class Languages(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().languages()

    /** Today, by which the month and year are counted, and on which a new lesson is held. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The start page, as it changes. */
    fun overview(): Flow<LanguagesOverview> = observe { languages, _, _, lessons ->
        val today = today()
        LanguagesOverview(
            all = Periods.of(lessons, today),
            cards = languages.filterNot { it.archived }.sortedWith(compareBy(InputText.byName) { it.name }).map { language ->
                LanguageCard(language, Periods.of(lessons.filter { it.languageId == language.id }, today))
            },
        )
    }

    /** The page of the language [languageId], as it changes; none once it is gone. */
    fun page(languageId: Long): Flow<LanguagePage?> = observe { languages, teachers, _, lessons ->
        val language = languages.find { it.id == languageId } ?: return@observe null
        val today = today()
        val byId = teachers.associateBy { it.id }
        // The tables are read one by one: a teacher just made may not be among them yet, for a moment.
        val own = lessons.filter { it.languageId == languageId && it.teacherId in byId }
        LanguagePage(
            language = language,
            lessons = own.sortedWith(compareByDescending<Lesson> { it.date }.thenByDescending { it.id })
                .map { LessonRow(it, byId.getValue(it.teacherId)) },
            byTeacher = own.groupBy { it.teacherId }
                .map { (teacherId, held) -> TeacherTally(byId.getValue(teacherId), Periods.of(held, today)) }
                .sortedWith(compareBy(InputText.byName) { it.teacher.name }),
        )
    }

    /** All languages, by name, those archived after the others. */
    fun languages(): Flow<List<LanguageItem>> = observe { languages, _, teaching, lessons ->
        val kept = lessons.map { it.languageId }.toSet() + onlyLanguages(teaching)
        languages.sortedWith(compareBy<Language> { it.archived }.thenBy(InputText.byName) { it.name })
            .map { LanguageItem(it, deletable = it.id !in kept) }
    }

    /** All teachers, by name, those archived after the others. */
    fun teachers(): Flow<List<TeacherItem>> = observe { languages, teachers, teaching, lessons ->
        teacherItems(languages, teachers, teaching, lessons)
            .sortedWith(compareBy<TeacherItem> { it.teacher.archived }.thenBy(InputText.byName) { it.teacher.name })
    }

    /** What a lesson's editor offers, as it changes. */
    fun choices(): Flow<LessonChoices> = observe { languages, teachers, teaching, lessons ->
        LessonChoices(
            languages = languages.filterNot { it.archived }.sortedWith(compareBy(InputText.byName) { it.name }),
            teachers = teacherItems(languages, teachers, teaching, lessons).filterNot { it.teacher.archived }
                .sortedWith(compareBy(InputText.byName) { it.teacher.name }),
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
     * Deletes the language [id], and who teaches it no more; refused, with nothing deleted, while it has lessons or is the
     * only language of a teacher.
     */
    suspend fun deleteLanguage(id: Long): Boolean = write {
        database().withTransaction {
            if (dao.languageHasLessons(id) || id in onlyLanguages(dao.teaching())) return@withTransaction false
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

    /** The lesson [id] as its editor opens it; none once it is deleted. */
    suspend fun editing(id: Long): LessonEditing? = withContext(io) {
        dao.lesson(id)?.let { LessonEditing(id, LessonDraft.of(it)) }
    }

    /**
     * Saves [draft] as [editing] has it: a new lesson, or the changes to the one opened; refused, with nothing saved, for
     * its problems, and for a language or teacher not offered, unless it is the one it was saved with: so an old lesson
     * keeps an archived language, or a teacher who teaches it no more. One deleted meanwhile stays deleted.
     */
    suspend fun save(editing: LessonEditing, draft: LessonDraft): Saving<LessonProblem> = write {
        database().withTransaction {
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
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            if (id == null) {
                Saving.Saved(dao.insertLesson(draft.toLesson()))
            } else {
                dao.updateLesson(draft.toLesson(id))
                Saving.Saved(id)
            }
        }
    }

    /** Deletes the lesson [id]. The screen asks first. */
    suspend fun delete(id: Long) = write { dao.deleteLesson(id) }

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

    /** All four tables, as they change, made into what [transform] makes of them, on [io]. */
    private fun <T> observe(transform: (List<Language>, List<Teacher>, List<TeacherLanguage>, List<Lesson>) -> T): Flow<T> = flow {
        emitAll(combine(dao.observeLanguages(), dao.observeTeachers(), dao.observeTeaching(), dao.observeLessons(), transform))
    }.flowOn(io)

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }
}
