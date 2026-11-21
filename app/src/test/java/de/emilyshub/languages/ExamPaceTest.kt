package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The pace the next exam asks for (SPEC.md §15, #151): its open topics per week left, rounded up, the weeks being the
 * days to it divided by 7 and at least 1; in its last week all its open topics; nothing without open topics, on its
 * day, after it or once it is taken.
 */
class ExamPaceTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun status(days: Long, open: Int, taken: Boolean = false) = ExamStatus(
        exam = Exam(id = 1, languageId = 1, name = "A2", date = today.plusDays(days), taken = taken),
        days = days,
        open = open,
        topics = open + 3,
    )

    @Test
    fun `eight days and more ask for topics per week, the weeks left counted whole, at least one`() {
        assertEquals(ExamPace.PerWeek(12), status(days = 8, open = 12).pace)
        assertEquals(ExamPace.PerWeek(6), status(days = 14, open = 12).pace)
        assertEquals(ExamPace.PerWeek(12), status(days = 13, open = 12).pace)
    }

    @Test
    fun `the topics per week are rounded up`() {
        assertEquals(ExamPace.PerWeek(2), status(days = 43, open = 12).pace)
        assertEquals(ExamPace.PerWeek(1), status(days = 43, open = 1).pace)
        assertEquals(ExamPace.PerWeek(3), status(days = 21, open = 7).pace)
    }

    @Test
    fun `in its last week all its open topics are left until it`() {
        assertEquals(ExamPace.Total(12), status(days = 7, open = 12).pace)
        assertEquals(ExamPace.Total(12), status(days = 1, open = 12).pace)
        assertEquals(ExamPace.Total(1), status(days = 1, open = 1).pace)
    }

    @Test
    fun `nothing without open topics, on its day, after it, or once taken`() {
        assertNull(status(days = 43, open = 0).pace)
        assertNull(status(days = 0, open = 12).pace)
        assertNull(status(days = -3, open = 12).pace)
        assertNull(status(days = 43, open = 12, taken = true).pace)
    }
}
