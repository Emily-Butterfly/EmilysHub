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
 * The topics and exams of Sprachen (SPEC.md §15): topics typed in a lesson put on the list, done only by hand, counted
 * by the lessons that treated them; the next exam and how its topics stand; exams taken and taken back; topics moved,
 * one by one and all together; topics and exams deleted; and a lesson that changes its language.
 */
@RunWith(AndroidJUnit4::class)
class TopicsTest {

    private val database = inMemoryDatabase()

    /** Thursday, 12 November 2026, 10:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val languages = Languages({ database }, clock, LessonFiles(testAttachmentStore(), FakeLessonDocuments(), Dispatchers.Unconfined), Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a topic typed in a lesson lands on the list open and without an exam, and one typed again is the same`() = runBlocking {
        val arabic = language("Arabisch")
        val amal = teacher("Amal", arabic)

        val first = lesson(arabic, amal, "2026-11-10", newTopics = listOf("Verben im Präsens", " "))
        assertEquals("a blank one is refused", null, first)
        val id = checkNotNull(lesson(arabic, amal, "2026-11-10", newTopics = listOf("Verben im Präsens", "verben im präsens")))
        lesson(arabic, amal, "2026-11-11", newTopics = listOf(" Verben  im Präsens"))

        val topics = tab(arabic).withoutExam.topics
        assertEquals(listOf("Verben im Präsens"), topics.map { it.topic.name })
        assertFalse(topics.single().topic.done)
        assertNull(topics.single().topic.examId)
        assertEquals(setOf(topics.single().topic.id), languages.editing(id)?.original?.topicIds)
    }

    @Test
    fun `a topic's name is needed and no other of its language reads the same, while another language may have it`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        topic(arabic, "Zahlen")

        assertEquals(Saving.Refused(setOf(NameProblem.Taken)), languages.createTopic(arabic, "zahlen"))
        assertEquals(Saving.Refused(setOf(NameProblem.Missing)), languages.createTopic(arabic, " "))
        assertTrue(languages.createTopic(italian, "Zahlen") is Saving.Saved)
        val colours = topic(arabic, "Farben")
        assertEquals(Saving.Refused(setOf(NameProblem.Taken)), languages.renameTopic(colours, "ZAHLEN"))
        assertEquals(Saving.Saved(colours), languages.renameTopic(colours, "Farben und Formen"))
        assertEquals(listOf("Farben und Formen", "Zahlen"), tab(arabic).withoutExam.topics.map { it.topic.name })
    }

    @Test
    fun `a topic is done and opened again only by hand, and a lesson treating it does not make it done`() = runBlocking {
        val arabic = language("Arabisch")
        val amal = teacher("Amal", arabic)
        val verbs = topic(arabic, "Verben")
        val numbers = topic(arabic, "Zahlen")
        lesson(arabic, amal, "2026-11-10", topicIds = setOf(verbs, numbers))

        assertEquals(listOf(false, false), tab(arabic).withoutExam.topics.map { it.topic.done })
        assertTrue(languages.setTopicDone(verbs, done = true))
        assertEquals("the open before the done", listOf("Zahlen" to false, "Verben" to true), names(tab(arabic).withoutExam))
        lesson(arabic, amal, "2026-11-11", topicIds = setOf(verbs))
        assertTrue(languages.setTopicDone(verbs, done = false))
        assertEquals(listOf("Verben" to false, "Zahlen" to false), names(tab(arabic).withoutExam))
    }

    @Test
    fun `a topic tells in how many lessons it was treated and when last, and lists those lessons`() = runBlocking {
        val arabic = language("Arabisch")
        val amal = teacher("Amal", arabic)
        val bilal = teacher("Bilal", arabic)
        val verbs = topic(arabic, "Verben")
        topic(arabic, "Zahlen")
        lesson(arabic, amal, "2026-10-01", topicIds = setOf(verbs))
        lesson(arabic, bilal, "2026-11-03", topicIds = setOf(verbs))
        lesson(arabic, amal, "2026-10-20", topicIds = setOf(verbs))

        val rows = tab(arabic).withoutExam.topics.associateBy { it.topic.name }
        assertEquals(3, rows.getValue("Verben").lessons)
        assertEquals(LocalDate.parse("2026-11-03"), rows.getValue("Verben").last)
        assertEquals(0, rows.getValue("Zahlen").lessons)
        assertNull(rows.getValue("Zahlen").last)
        assertEquals(
            listOf("2026-11-03" to "Bilal", "2026-10-20" to "Amal", "2026-10-01" to "Amal"),
            languages.lessonsOf(verbs).first().map { it.lesson.date.toString() to it.teacher.name },
        )
    }

    @Test
    fun `the next exam not taken stands on the card and on top of the tab, with the days to it and its open topics`() = runBlocking {
        val arabic = language("Arabisch")
        val later = exam(arabic, "B1", "2027-06-01")
        val next = exam(arabic, "A2", "2026-12-25")
        repeat(3) { topic(arabic, "Thema $it", next).also { id -> if (it == 0) languages.setTopicDone(id, true) } }
        topic(arabic, "Ohne", null)
        topic(arabic, "Später", later)

        val status = checkNotNull(tab(arabic).next)
        assertEquals("A2", status.exam.name)
        assertEquals(43L, status.days)
        assertEquals(2, status.open)
        assertEquals(3, status.topics)
        assertEquals(status, languages.overview().first().cards.single().nextExam)

        clock.now = Instant.parse("2026-12-25T08:00:00Z")
        assertEquals("on the day", 0L, tab(arabic).next?.days)
        clock.now = Instant.parse("2026-12-28T08:00:00Z")
        assertEquals("since 3 days, until it is taken", -3L, tab(arabic).next?.days)

        languages.setExamTaken(next, taken = true)
        assertEquals("B1", tab(arabic).next?.exam?.name)
        languages.setExamTaken(later, taken = true)
        assertNull(tab(arabic).next)
    }

    @Test
    fun `the tab groups the topics by exam, those to come by date, then without, then those taken`() = runBlocking {
        val arabic = language("Arabisch")
        val b1 = exam(arabic, "B1", "2027-06-01")
        val a2 = exam(arabic, "A2", "2026-12-25")
        val a1 = exam(arabic, "A1", "2026-06-01")
        topic(arabic, "Verben", a2)
        topic(arabic, "Zahlen", null)
        languages.setExamTaken(a1, taken = true, result = "  bestanden, 87 %\n")

        val tab = tab(arabic)
        assertEquals(listOf("A2", "B1"), tab.upcoming.map { it.exam?.exam?.name })
        assertEquals(listOf("Zahlen"), tab.withoutExam.topics.map { it.topic.name })
        assertEquals(listOf("A1"), tab.taken.map { it.exam?.exam?.name })
        assertEquals("bestanden, 87 %", tab.taken.single().exam?.exam?.result)
        assertEquals(setOf(a1, a2, b1), tab.exams.keys)
    }

    @Test
    fun `an exam needs a name and a date, and is changed`() = runBlocking {
        val arabic = language("Arabisch")
        assertEquals(
            Saving.Refused(setOf(ExamProblem.NameMissing, ExamProblem.DateMissing)),
            languages.createExam(arabic, ExamDraft(" ", null)),
        )
        val a2 = exam(arabic, "A2", "2026-12-25")
        assertEquals(Saving.Saved(a2), languages.saveExam(a2, ExamDraft("A2 schriftlich", LocalDate.parse("2027-01-15"))))
        assertEquals(ExamDraft("A2 schriftlich", LocalDate.parse("2027-01-15")), languages.examDraft(a2))
    }

    @Test
    fun `a taken exam's topics are only to read, and offered to no lesson, until it is taken back`() = runBlocking {
        val arabic = language("Arabisch")
        val amal = teacher("Amal", arabic)
        val a2 = exam(arabic, "A2", "2026-11-01")
        val verbs = topic(arabic, "Verben", a2)
        val old = checkNotNull(lesson(arabic, amal, "2026-10-30", topicIds = setOf(verbs)))

        assertTrue(languages.setExamTaken(a2, taken = true, result = "bestanden"))

        assertFalse(languages.setTopicDone(verbs, done = true))
        assertFalse(languages.deleteTopic(verbs))
        assertFalse(tab(arabic).taken.single().topics.single().checkable(tab(arabic).exams))
        assertEquals(emptyList<Topic>(), languages.choices().first().topicsOf(arabic))
        assertEquals("typed, it is not made anew", null, lesson(arabic, amal, "2026-11-12", newTopics = listOf("verben")))
        assertEquals(null, lesson(arabic, amal, "2026-11-12", topicIds = setOf(verbs)))
        val editing = checkNotNull(languages.editing(old))
        assertEquals(
            "a lesson saved with it keeps it",
            Saving.Saved(old),
            languages.save(editing, checkNotNull(editing.original).copy(minutes = "90")),
        )
        assertFalse("too long a result", languages.setExamTaken(a2, taken = true, result = "x".repeat(Exam.RESULT_MAX + 1)))

        languages.setExamTaken(a2, taken = false)
        assertEquals("", tab(arabic).upcoming.single().exam?.exam?.result)
        assertTrue(languages.setTopicDone(verbs, done = true))
        assertEquals(listOf("Verben"), languages.choices().first().topicsOf(arabic).map { it.name })
    }

    @Test
    fun `open topics move one by one or all together to another exam or to none, those done of a taken one stay`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val a2 = exam(arabic, "A2", "2026-11-01")
        val b1 = exam(arabic, "B1", "2027-06-01")
        val other = exam(italian, "A1", "2027-01-01")
        val verbs = topic(arabic, "Verben", a2)
        val numbers = topic(arabic, "Zahlen", a2)
        val colours = topic(arabic, "Farben", a2)
        val done = topic(arabic, "Begrüßung", a2).also { languages.setTopicDone(it, true) }

        languages.moveTopics(listOf(verbs), b1)
        assertEquals(b1, topicOf(arabic, verbs).examId)
        languages.moveTopics(listOf(verbs), other)
        assertEquals("not to another language's exam", b1, topicOf(arabic, verbs).examId)

        languages.setExamTaken(a2, taken = true)
        languages.moveTopics(listOf(done), null)
        assertEquals("one done of a taken exam stays", a2, topicOf(arabic, done).examId)
        languages.moveTopics(listOf(numbers), a2)
        assertEquals("not to a taken one", a2, topicOf(arabic, numbers).examId)
        languages.moveOpenTopics(a2, null)
        assertNull(topicOf(arabic, numbers).examId)
        assertNull(topicOf(arabic, colours).examId)
        assertEquals(a2, topicOf(arabic, done).examId)

        languages.moveOpenTopics(b1, null)
        assertNull(topicOf(arabic, verbs).examId)
    }

    @Test
    fun `a topic deleted is lost to its lessons, and an exam deleted leaves its topics without one`() = runBlocking {
        val arabic = language("Arabisch")
        val amal = teacher("Amal", arabic)
        val a2 = exam(arabic, "A2", "2026-12-25")
        val verbs = topic(arabic, "Verben", a2)
        val numbers = topic(arabic, "Zahlen", a2)
        val id = checkNotNull(lesson(arabic, amal, "2026-11-10", topicIds = setOf(verbs, numbers)))

        assertTrue(languages.deleteTopic(verbs))
        assertEquals(setOf(numbers), languages.editing(id)?.original?.topicIds)

        languages.deleteExam(a2)
        assertEquals(listOf("Zahlen"), tab(arabic).withoutExam.topics.map { it.topic.name })
        assertEquals(emptyList<TopicGroup>(), tab(arabic).upcoming)
        assertFalse("a language with topics is not deleted", languages.deleteLanguage(language("Leer").also { topic(it, "x") }))
    }

    @Test
    fun `a lesson that changes its language loses its topics`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val amal = teacher("Amal", arabic, italian)
        val verbs = topic(arabic, "Verben")
        val id = checkNotNull(lesson(arabic, amal, "2026-11-10", topicIds = setOf(verbs)))
        val opened = checkNotNull(languages.editing(id)?.original)

        val moved = opened.withLanguage(italian)
        assertFalse(moved.hasTopics)
        assertEquals(opened, opened.withLanguage(arabic))
        assertEquals(
            "a topic of another language is refused",
            setOf(LessonProblem.TopicNotOffered),
            (languages.save(checkNotNull(languages.editing(id)), opened.copy(languageId = italian)) as Saving.Refused).problems,
        )
        assertEquals(Saving.Saved(id), languages.save(checkNotNull(languages.editing(id)), moved))
        assertEquals(emptySet<Long>(), languages.editing(id)?.original?.topicIds)
        assertEquals(0, tab(arabic).withoutExam.topics.single().lessons)
    }

    private suspend fun tab(languageId: Long) = checkNotNull(languages.page(languageId).first()).topics

    private suspend fun topicOf(languageId: Long, id: Long) =
        tab(languageId).let { it.upcoming + it.withoutExam + it.taken }.flatMap { it.topics }.single { it.topic.id == id }.topic

    private fun names(group: TopicGroup) = group.topics.map { it.topic.name to it.topic.done }

    private suspend fun language(name: String): Long = (languages.createLanguage(name) as Saving.Saved).id

    private suspend fun teacher(name: String, vararg languageIds: Long): Long =
        (languages.saveTeacher(TeacherEditing.New, TeacherDraft(name, languageIds = languageIds.toSet())) as Saving.Saved).id

    private suspend fun topic(languageId: Long, name: String, examId: Long? = null): Long =
        (languages.createTopic(languageId, name, examId) as Saving.Saved).id

    private suspend fun exam(languageId: Long, name: String, date: String): Long =
        (languages.createExam(languageId, ExamDraft(name, LocalDate.parse(date))) as Saving.Saved).id

    /** A lesson of an hour saved with these topics; none if refused. */
    private suspend fun lesson(
        languageId: Long,
        teacherId: Long,
        date: String,
        topicIds: Set<Long> = emptySet(),
        newTopics: List<String> = emptyList(),
    ): Long? {
        val draft = LessonDraft(LocalDate.parse(date), "60", languageId, teacherId, topicIds = topicIds, newTopics = newTopics)
        return (languages.save(LessonEditing.New, draft) as? Saving.Saved)?.id
    }
}
