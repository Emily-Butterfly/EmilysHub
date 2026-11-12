package de.emilyshub.languages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The languages, teachers, who teaches what, the lessons, and the topics and exams with what lessons treated which
 * (SPEC.md §15), as [Languages] reads and writes them.
 */
@Dao
interface LanguageDao {
    @Query("SELECT * FROM languages")
    fun observeLanguages(): Flow<List<Language>>

    @Query("SELECT * FROM languages")
    suspend fun languages(): List<Language>

    @Query("SELECT * FROM languages WHERE id = :id")
    suspend fun language(id: Long): Language?

    @Insert
    suspend fun insertLanguage(language: Language): Long

    @Update
    suspend fun updateLanguage(language: Language)

    @Query("DELETE FROM languages WHERE id = :id")
    suspend fun deleteLanguage(id: Long)

    @Query("SELECT * FROM teachers")
    fun observeTeachers(): Flow<List<Teacher>>

    @Query("SELECT * FROM teachers WHERE id = :id")
    suspend fun teacher(id: Long): Teacher?

    @Insert
    suspend fun insertTeacher(teacher: Teacher): Long

    @Update
    suspend fun updateTeacher(teacher: Teacher)

    @Query("DELETE FROM teachers WHERE id = :id")
    suspend fun deleteTeacher(id: Long)

    @Query("SELECT * FROM teacher_languages")
    fun observeTeaching(): Flow<List<TeacherLanguage>>

    @Query("SELECT * FROM teacher_languages")
    suspend fun teaching(): List<TeacherLanguage>

    @Query("SELECT language_id FROM teacher_languages WHERE teacher_id = :teacherId")
    suspend fun languagesOf(teacherId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTeaching(teaching: List<TeacherLanguage>)

    @Query("DELETE FROM teacher_languages WHERE teacher_id = :teacherId")
    suspend fun deleteTeachingOf(teacherId: Long)

    @Query("SELECT * FROM lessons")
    fun observeLessons(): Flow<List<Lesson>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun lesson(id: Long): Lesson?

    @Insert
    suspend fun insertLesson(lesson: Lesson): Long

    @Update
    suspend fun updateLesson(lesson: Lesson)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteLesson(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM lessons WHERE language_id = :languageId)")
    suspend fun languageHasLessons(languageId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM lessons WHERE teacher_id = :teacherId)")
    suspend fun teacherHasLessons(teacherId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM topics WHERE language_id = :languageId UNION ALL SELECT 1 FROM exams WHERE language_id = :languageId)")
    suspend fun languageHasStudies(languageId: Long): Boolean

    @Query("SELECT * FROM topics")
    fun observeTopics(): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE language_id = :languageId")
    suspend fun topicsOf(languageId: Long): List<Topic>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun topic(id: Long): Topic?

    @Insert
    suspend fun insertTopic(topic: Topic): Long

    @Update
    suspend fun updateTopic(topic: Topic)

    @Update
    suspend fun updateTopics(topics: List<Topic>)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun deleteTopic(id: Long)

    @Query("SELECT * FROM exams")
    fun observeExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun exam(id: Long): Exam?

    @Insert
    suspend fun insertExam(exam: Exam): Long

    @Update
    suspend fun updateExam(exam: Exam)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExam(id: Long)

    @Query("SELECT * FROM lesson_topics")
    fun observeLessonTopics(): Flow<List<LessonTopic>>

    @Query("SELECT topic_id FROM lesson_topics WHERE lesson_id = :lessonId")
    suspend fun topicsOfLesson(lessonId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLessonTopics(lessonTopics: List<LessonTopic>)

    @Query("DELETE FROM lesson_topics WHERE lesson_id = :lessonId")
    suspend fun deleteTopicsOfLesson(lessonId: Long)

    @Query("SELECT * FROM lesson_attachments WHERE lesson_id = :lessonId ORDER BY position")
    suspend fun attachmentsOf(lessonId: Long): List<LessonAttachment>

    @Query("SELECT file FROM lesson_attachments")
    suspend fun attachmentFiles(): List<String>

    @Query("SELECT * FROM lesson_attachments")
    fun observeAttachments(): Flow<List<LessonAttachment>>

    @Insert
    suspend fun insertAttachments(attachments: List<LessonAttachment>)

    @Update
    suspend fun updateAttachments(attachments: List<LessonAttachment>)

    @Query("DELETE FROM lesson_attachments WHERE id IN (:ids)")
    suspend fun deleteAttachments(ids: List<Long>)
}
