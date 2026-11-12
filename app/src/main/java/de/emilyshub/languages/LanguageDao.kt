package de.emilyshub.languages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
}
