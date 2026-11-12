package de.emilyshub.languages

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.NameProblem
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sprachen (SPEC.md §15): languages and teachers made, archived and kept while lessons hang on them; lessons checked,
 * loaded and saved; and what they come to by language and teacher for the month, the year and in all.
 */
@RunWith(AndroidJUnit4::class)
class LanguagesTest {

    private val database = inMemoryDatabase()

    /** Thursday, 12 November 2026, 10:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val languages = Languages({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a lesson as typed is checked strictly, each problem at its field`() {
        val empty = LessonDraft(date = null, minutes = "", price = "12,345", note = "x".repeat(LessonDraft.NOTE_MAX + 1))
        assertEquals(
            setOf(
                LessonProblem.DateMissing, LessonProblem.MinutesInvalid, LessonProblem.LanguageMissing, LessonProblem.TeacherMissing,
                LessonProblem.PriceInvalid, LessonProblem.NoteTooLong,
            ),
            empty.problems,
        )
        for (minutes in listOf("0", "-5", "1441", "45.5", "abc")) {
            assertTrue(minutes, LessonProblem.MinutesInvalid in LessonDraft(date(), minutes = minutes).problems)
        }
        val fine = LessonDraft(date(), minutes = " 45 ", languageId = 1, teacherId = 1, price = "12,50", note = "x".repeat(LessonDraft.NOTE_MAX))
        assertEquals(emptySet<LessonProblem>(), fine.problems)
        assertEquals(Lesson(date = date(), minutes = 45, languageId = 1, teacherId = 1, priceCents = 1250, note = fine.note), fine.toLesson())
        assertNull(LessonDraft(date(), minutes = "45", languageId = 1, teacherId = 1).toLesson().priceCents)
    }

    @Test
    fun `a teacher needs a name and a language, and a language a name no other reads the same as`() = runBlocking {
        assertEquals(
            setOf(TeacherProblem.NameMissing, TeacherProblem.LanguagesMissing),
            (languages.saveTeacher(TeacherEditing.New, TeacherDraft(" ")) as Saving.Refused).problems,
        )
        val arabic = language("Arabisch")

        assertEquals(Saving.Refused(setOf(NameProblem.Taken)), languages.createLanguage(" arabisch "))
        assertEquals(Saving.Refused(setOf(NameProblem.Missing)), languages.createLanguage(""))
        languages.setLanguageArchived(arabic, archived = true)
        assertEquals("archived, it still takes its name", Saving.Refused(setOf(NameProblem.Taken)), languages.createLanguage("Arabisch"))
        assertEquals(Saving.Saved(arabic), languages.renameLanguage(arabic, "Arabisch (MSA)"))
        assertEquals(listOf("Arabisch (MSA)"), languages.languages().first().map { it.language.name })
    }

    @Test
    fun `the start page counts the month, the year and all, by each lesson's date, and each language apart`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        // One teacher of both: each language counts for itself.
        val amal = teacher("Amal", arabic, italian)
        lesson(arabic, amal, "2026-11-01", 60, price = "15")
        lesson(arabic, amal, "2026-10-31", 30)
        lesson(italian, amal, "2026-11-12", 45, price = "10")
        lesson(arabic, amal, "2025-12-31", 90, price = "20")

        val overview = languages.overview().first()

        assertEquals(Tally(minutes = 105, count = 2, costCents = 2500), overview.all.month)
        assertEquals(Tally(minutes = 135, count = 3, costCents = 2500), overview.all.year)
        assertEquals(Tally(minutes = 225, count = 4, costCents = 4500), overview.all.total)
        assertEquals(listOf("Arabisch", "Italienisch"), overview.cards.map { it.language.name })
        val arabicCard = overview.cards.first().periods
        assertEquals(Tally(60, 1, 1500), arabicCard.month)
        assertEquals(Tally(90, 2, 1500), arabicCard.year)
        assertEquals(Tally(180, 3, 3500), arabicCard.total)
        assertEquals("no price, no cost", Tally(30, 1, null), Tally.of(listOf(Lesson(date = date(), minutes = 30, languageId = 1, teacherId = 1))))
    }

    @Test
    fun `a language's page has its lessons, the newest first, and what each teacher's come to`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val amal = teacher("Amal", arabic, italian)
        val bilal = teacher("Bilal", arabic)
        lesson(arabic, bilal, "2026-11-03", 60, price = "12")
        lesson(arabic, amal, "2026-11-10", 45)
        lesson(italian, amal, "2026-11-11", 60, price = "30")

        val page = checkNotNull(languages.page(arabic).first())

        assertEquals(listOf("2026-11-10" to "Amal", "2026-11-03" to "Bilal"), page.lessons.map { it.lesson.date.toString() to it.teacher.name })
        assertEquals(listOf("Amal", "Bilal"), page.byTeacher.map { it.teacher.name })
        assertEquals(Tally(45, 1, null), page.byTeacher[0].periods.total)
        assertEquals(Tally(60, 1, 1200), page.byTeacher[1].periods.month)
    }

    @Test
    fun `archived, a language and a teacher are offered no more, and their lessons stay and count`() = runBlocking {
        val arabic = language("Arabisch")
        val amal = teacher("Amal", arabic)
        lesson(arabic, amal, "2026-11-01", 60)

        languages.setLanguageArchived(arabic, archived = true)
        languages.setTeacherArchived(amal, archived = true)

        val choices = languages.choices().first()
        assertEquals(emptyList<Language>(), choices.languages)
        assertEquals(emptyList<TeacherItem>(), choices.teachers)
        assertEquals(emptyList<LanguageCard>(), languages.overview().first().cards)
        assertEquals(60L, languages.overview().first().all.total.minutes)
        assertEquals(
            setOf(LessonProblem.LanguageNotOffered, LessonProblem.TeacherNotOffered),
            (languages.save(LessonEditing.New, draft(arabic, amal)) as Saving.Refused).problems,
        )

        languages.setLanguageArchived(arabic, archived = false)
        languages.setTeacherArchived(amal, archived = false)
        assertEquals(listOf("Amal"), languages.choices().first().teachers.map { it.teacher.name })
    }

    @Test
    fun `neither a language nor a teacher with lessons can be deleted, without them they can`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val amal = teacher("Amal", arabic, italian)
        val bilal = teacher("Bilal", italian)
        lesson(arabic, amal, "2026-11-01", 60)

        assertFalse(languages.deleteLanguage(arabic))
        assertFalse(languages.deleteTeacher(amal))
        assertTrue(languages.deleteTeacher(bilal))
        assertTrue(languages.deleteLanguage(italian))

        assertEquals(listOf("Arabisch"), languages.languages().first().map { it.language.name })
        assertEquals(listOf(listOf("Arabisch")), languages.teachers().first().map { items -> items.languages.map { it.name } })
        assertTrue(languages.teachers().first().single().hasLessons)
    }

    @Test
    fun `a language taken from a teacher leaves their old lessons as they are, which can still be saved so`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val amal = teacher("Amal", arabic, italian)
        val old = lesson(arabic, amal, "2026-11-01", 60)

        languages.saveTeacher(checkNotNull(languages.teacherEditing(amal)), TeacherDraft("Amal", languageIds = setOf(italian)))

        assertEquals(listOf("Amal"), checkNotNull(languages.page(arabic).first()).lessons.map { it.teacher.name })
        assertFalse(languages.choices().first().teachers.single().teaches(arabic))
        val editing = checkNotNull(languages.editing(old))
        assertEquals(Saving.Saved(old), languages.save(editing, checkNotNull(editing.original).copy(minutes = "75")))
        assertEquals(
            "a new one of it is refused",
            setOf(LessonProblem.TeacherNotOffered),
            (languages.save(LessonEditing.New, draft(arabic, amal)) as Saving.Refused).problems,
        )
    }

    @Test
    fun `another teacher given the language is offered for it`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val amal = teacher("Amal", italian)

        languages.teach(amal, arabic)

        assertTrue(languages.choices().first().teachers.single().teaches(arabic))
        assertTrue(languages.save(LessonEditing.New, draft(arabic, amal)) is Saving.Saved)
    }

    @Test
    fun `a lesson opens as saved, saves its changes, is refused for its problems, and is deleted`() = runBlocking {
        val arabic = language("Arabisch")
        val amal = teacher("Amal", arabic)
        assertEquals(LessonDraft(date = date(), languageId = arabic), languages.newLesson(arabic))
        val id = lesson(arabic, amal, "2026-11-01", 60, price = "12,5", note = "Verben")

        val editing = checkNotNull(languages.editing(id))
        val opened = checkNotNull(editing.original)
        assertEquals(LessonDraft(date("2026-11-01"), "60", arabic, amal, price = "12,50", note = "Verben"), opened)

        assertEquals(setOf(LessonProblem.MinutesInvalid), (languages.save(editing, opened.copy(minutes = "")) as Saving.Refused).problems)
        assertEquals(60, checkNotNull(languages.editing(id)).original?.minutes?.toInt())

        languages.save(editing, opened.copy(minutes = "90", price = ""))
        assertEquals(LessonDraft(date("2026-11-01"), "90", arabic, amal, note = "Verben"), languages.editing(id)?.original)

        languages.delete(id)
        assertNull(languages.editing(id))
        assertEquals("one deleted meanwhile stays deleted", Saving.Saved(id), languages.save(editing, opened))
        assertNull(languages.editing(id))
    }

    private fun date(text: String = "2026-11-12") = LocalDate.parse(text)

    private fun draft(languageId: Long, teacherId: Long) = LessonDraft(date(), minutes = "60", languageId = languageId, teacherId = teacherId)

    private suspend fun language(name: String): Long = (languages.createLanguage(name) as Saving.Saved).id

    private suspend fun teacher(name: String, vararg languageIds: Long): Long =
        (languages.saveTeacher(TeacherEditing.New, TeacherDraft(name, languageIds = languageIds.toSet())) as Saving.Saved).id

    private suspend fun lesson(languageId: Long, teacherId: Long, date: String, minutes: Int, price: String = "", note: String = ""): Long {
        val draft = LessonDraft(date(date), minutes.toString(), languageId, teacherId, price, note)
        return (languages.save(LessonEditing.New, draft) as Saving.Saved).id
    }
}
