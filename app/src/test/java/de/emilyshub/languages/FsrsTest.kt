package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Test

/** FSRS-6 as `docs/research/spaced-repetition.md` §4 and §5 have it, against all its test vectors (§6). */
class FsrsTest {

    /** One answer: [days] since the one before (none for a new card), what it was, and what it gives. */
    private data class Step(val days: Long?, val rating: Rating, val stability: Double, val difficulty: Double, val interval: Int)

    private fun assertSteps(vararg steps: Step) {
        var memory: Memory? = null
        steps.forEachIndexed { i, step ->
            val scheduled = Fsrs.review(memory, step.days ?: 0, step.rating)
            assertEquals("S of answer ${i + 1}", step.stability, scheduled.memory.stability, TOLERANCE)
            assertEquals("D of answer ${i + 1}", step.difficulty, scheduled.memory.difficulty, TOLERANCE)
            assertEquals("interval of answer ${i + 1}", step.interval, scheduled.interval)
            memory = scheduled.memory
        }
    }

    @Test
    fun `the first answer of a new card`() {
        assertSteps(Step(null, Rating.Again, 0.2120, 6.4133, 1))
        assertSteps(Step(null, Rating.Hard, 1.2931, 5.1122, 1))
        assertSteps(Step(null, Rating.Good, 2.3065, 2.1181, 2))
        assertSteps(Step(null, Rating.Easy, 8.2956, 1.0000, 8))
    }

    @Test
    fun `A - six times Gut, each on the day due`() = assertSteps(
        Step(null, Rating.Good, 2.3065, 2.1181, 2),
        Step(2, Rating.Good, 10.9643, 2.1112, 11),
        Step(11, Rating.Good, 46.2802, 2.1043, 46),
        Step(46, Rating.Good, 162.8622, 2.0975, 163),
        Step(163, Rating.Good, 497.4472, 2.0906, 497),
        Step(497, Rating.Good, 1345.5288, 2.0837, 1346),
    )

    @Test
    fun `B - forgotten and known again in the same round`() = assertSteps(
        Step(null, Rating.Good, 2.3065, 2.1181, 2),
        Step(2, Rating.Good, 10.9643, 2.1112, 11),
        Step(11, Rating.Again, 1.5383, 7.3922, 2),
        Step(0, Rating.Good, 1.5712, 7.3801, 2),
        Step(2, Rating.Good, 4.9318, 7.3679, 5),
        Step(5, Rating.Good, 12.4025, 7.3558, 12),
    )

    @Test
    fun `C - Schwer, Gut, Leicht, Leicht, each on the day due`() = assertSteps(
        Step(null, Rating.Hard, 1.2931, 5.1122, 1),
        Step(1, Rating.Good, 4.5460, 5.1023, 5),
        Step(5, Rating.Easy, 27.0306, 3.4509, 27),
        Step(27, Rating.Easy, 145.8163, 1.2478, 146),
    )

    @Test
    fun `D - late, then early`() = assertSteps(
        Step(null, Rating.Good, 2.3065, 2.1181, 2),
        Step(10, Rating.Good, 25.1087, 2.1112, 25),
        Step(1, Rating.Good, 29.0943, 2.1043, 29),
    )

    @Test
    fun `E - new, twice Nochmal and Gut on the same day, Gut the next`() = assertSteps(
        Step(null, Rating.Again, 0.2120, 6.4133, 1),
        Step(0, Rating.Again, 0.0834, 8.8063, 1),
        Step(0, Rating.Good, 0.1031, 8.7927, 1),
        Step(1, Rating.Good, 0.6953, 8.7792, 1),
    )

    @Test
    fun `the reference's memory state after Nochmal and five times Gut`() {
        var memory: Memory? = null
        listOf(Rating.Again to 0L, Rating.Good to 0L, Rating.Good to 1L, Rating.Good to 3L, Rating.Good to 8L, Rating.Good to 21L)
            .forEach { (rating, days) -> memory = Fsrs.review(memory, days, rating).memory }
        assertEquals(53.62691, checkNotNull(memory).stability, TOLERANCE)
        assertEquals(6.3574867, checkNotNull(memory).difficulty, TOLERANCE)
    }

    @Test
    fun `the interval is at least a day and at most 36 500`() {
        assertEquals(1, Fsrs.interval(0.001))
        assertEquals(36_500, Fsrs.interval(1_000_000.0))
    }

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
