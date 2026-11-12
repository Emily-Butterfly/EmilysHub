package de.emilyshub.wishes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.languages.ExamDraft
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.languages.Languages
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.testAttachmentStore
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exams of Sprachen at goals (SPEC.md §6, §15): a part like a to-do, its progress the share of its topics done, done
 * once taken; settling its goal when taken and taken back, when made under one done, and when either side is deleted.
 */
@RunWith(AndroidJUnit4::class)
class ExamGoalsTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val languages = Languages(
        { database }, clock, LessonFiles(testAttachmentStore(), FakeLessonDocuments(), Dispatchers.Unconfined), Dispatchers.Unconfined,
    )

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an exam at a goal shows there with its topics, its progress the share of them done, nothing without any`() = runBlocking {
        val goal = wishes.create(WishDraft("Arabisch A2 erreichen"))
        val arabic = language("Arabisch")
        val a2 = exam(arabic, "A2", goal)

        assertEquals(0.0, part(goal).progress, 0.0)
        val topics = List(4) { topic(arabic, "Thema $it", a2) }
        languages.setTopicDone(topics[0], done = true)

        val shown = part(goal)
        assertEquals("Arabisch A2", shown.title)
        assertEquals(1 to 4, shown.ticked to shown.topics)
        assertEquals(0.25, shown.progress, 0.0)
        assertEquals(25, checkNotNull(wishes.goal(goal).first()).percent)
    }

    @Test
    fun `ticking, opening, moving and assigning topics changes the goal's progress`() = runBlocking {
        val goal = wishes.create(WishDraft("A2"))
        val arabic = language("Arabisch")
        val a2 = exam(arabic, "A2", goal)
        val other = exam(arabic, "B1", goal = null)
        val verbs = topic(arabic, "Verben", a2)
        val numbers = topic(arabic, "Zahlen", null)

        languages.setTopicDone(verbs, done = true)
        assertEquals(1.0, part(goal).progress, 0.0)
        languages.moveTopics(listOf(numbers), a2)
        assertEquals(0.5, part(goal).progress, 0.0)
        languages.setTopicDone(verbs, done = false)
        assertEquals(0.0, part(goal).progress, 0.0)
        languages.moveTopics(listOf(numbers), other)
        assertEquals(1 to 0, part(goal).topics to part(goal).ticked)
    }

    @Test
    fun `taken, it finishes a goal whose other parts are done, and taken back opens it again`() = runBlocking {
        val goal = wishes.create(WishDraft("A2"))
        val todo = todos.create(TodoDraft("Anmelden", goalId = goal))
        val a2 = exam(language("Arabisch"), "A2", goal)
        todos.markDone(todo)
        assertEquals(WishStatus.Open, wishes.get(goal)?.status)

        languages.setExamTaken(a2, taken = true, result = "bestanden")
        assertEquals(WishStatus.Done to today, wishes.get(goal)?.let { it.status to it.doneOn })
        assertEquals("taken: all of it", 1.0, part(goal).progress, 0.0)

        languages.setExamTaken(a2, taken = false)
        assertEquals(WishStatus.InProgress, wishes.get(goal)?.status)
    }

    @Test
    fun `a new exam under a goal done opens it again, as does one moved there, and one moved away may finish it`() = runBlocking {
        val goal = wishes.create(WishDraft("A2", status = WishStatus.Done, doneOn = today))
        val arabic = language("Arabisch")

        val a2 = exam(arabic, "A2", goal)
        assertEquals(WishStatus.InProgress, wishes.get(goal)?.status)

        val todo = todos.create(TodoDraft("Anmelden", goalId = goal))
        todos.markDone(todo)
        languages.saveExam(a2, ExamDraft("A2", LocalDate.parse("2026-12-25"), goalId = null))
        assertEquals("its last part open gone, it is done", WishStatus.Done, wishes.get(goal)?.status)
        languages.saveExam(a2, ExamDraft("A2", LocalDate.parse("2026-12-25"), goalId = goal))
        assertEquals(WishStatus.InProgress, wishes.get(goal)?.status)
    }

    @Test
    fun `an exam deleted goes from its goal, and a goal deleted leaves its exam without one`() = runBlocking {
        val goal = wishes.create(WishDraft("A2"))
        val todo = todos.create(TodoDraft("Anmelden", goalId = goal))
        todos.markDone(todo)
        val arabic = language("Arabisch")
        val a2 = exam(arabic, "A2", goal)

        languages.deleteExam(a2)
        assertTrue(checkNotNull(wishes.goal(goal).first()).exams.isEmpty())
        assertEquals("its last part open gone", WishStatus.Done, wishes.get(goal)?.status)

        val other = wishes.create(WishDraft("B1"))
        val b1 = exam(arabic, "B1", other)
        wishes.delete(other)
        assertNull(languages.examDraft(b1)?.goalId)
    }

    @Test
    fun `deleting a goal with its parts moving up takes the exam up, and a progress set by hand comes first`() = runBlocking {
        val top = wishes.create(WishDraft("Arabisch"))
        val goal = wishes.create(WishDraft("A2", parentId = top))
        val a2 = exam(language("Arabisch"), "A2", goal)

        wishes.deleteMovingPartsUp(goal)
        assertEquals(top, languages.examDraft(a2)?.goalId)

        val byHand = wishes.create(WishDraft("B1", manualProgress = 70))
        exam(language("Italienisch"), "B1", byHand)
        assertEquals(70, checkNotNull(wishes.goal(byHand).first()).percent)
    }

    private suspend fun part(goal: Long) = checkNotNull(wishes.goal(goal).first()).exams.single()

    private suspend fun language(name: String) = (languages.createLanguage(name) as Saving.Saved).id

    private suspend fun exam(languageId: Long, name: String, goal: Long?) =
        (languages.createExam(languageId, ExamDraft(name, LocalDate.parse("2026-12-25"), goal)) as Saving.Saved).id

    private suspend fun topic(languageId: Long, name: String, examId: Long?) =
        (languages.createTopic(languageId, name, examId) as Saving.Saved).id
}
