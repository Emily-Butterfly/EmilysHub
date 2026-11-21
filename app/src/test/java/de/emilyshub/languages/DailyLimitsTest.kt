package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The limits that share out a backlog over several days (SPEC.md §15; #156): a language's workload up to its limit, the
 * longest due first, no new cards while more is due than it takes; the overall limit shared by backlog, rounded by the
 * largest remainder; the smaller of a language's own limit and its share.
 */
class DailyLimitsTest {

    private val today = LocalDate.of(2026, 11, 21)

    /** A card [id] due [daysAgo] days ago, asked before. */
    private fun due(id: Long, daysAgo: Long = 1, languageId: Long = 1) = Card(
        id = id, languageId = languageId, front = "f$id", back = "b$id",
        stability = 3.0, difficulty = 5.0, firstReview = today.minusDays(30), lastReview = today.minusDays(10),
        due = today.minusDays(daysAgo), interval = 9,
    )

    private fun new(id: Long, languageId: Long = 1) = Card(id = id, languageId = languageId, front = "f$id", back = "b$id")

    /** A card [id] asked today, as the day's workload asked it, due before it was if [wasDue]. */
    private fun askedToday(id: Long, wasDue: Boolean = true) = due(id).copy(lastReview = today, due = today.plusDays(5)).let {
        if (wasDue) it else it.copy(firstReview = today)
    }

    @Test
    fun `with a limit the workload takes the longest due first up to it, and tells how many wait`() {
        val cards = listOf(due(1, daysAgo = 1), due(2, daysAgo = 5), due(3, daysAgo = 3), new(4))

        val workload = Workload.of(cards, newPerDay = 10, today, limit = 2)

        assertEquals(listOf(2L, 3L), workload.remaining.map { it.id })
        assertEquals(1, workload.waiting)
    }

    @Test
    fun `while more is due than the limit takes there are no new cards, without that new ones as far as the limit goes`() {
        val backlog = listOf(due(1), due(2), due(3), new(4))
        assertTrue(Workload.of(backlog, newPerDay = 10, today, limit = 2).remaining.none { it.new })

        val room = listOf(due(1), new(2), new(3), new(4))
        val workload = Workload.of(room, newPerDay = 10, today, limit = 3)
        assertEquals(listOf(1L, 2L, 3L), workload.remaining.map { it.id })
        assertEquals(0, workload.waiting)
        // Not more new ones than the day takes, either.
        assertEquals(listOf(1L, 2L), Workload.of(room, newPerDay = 1, today, limit = 3).remaining.map { it.id })
    }

    @Test
    fun `what the day's workload asked counts against the limit, once each, and the limit reached it is done`() {
        val cards = listOf(askedToday(1), askedToday(2, wasDue = false), due(3), due(4))
        val asked = AskedToday(inWorkload = setOf(PromptKey(1, false), PromptKey(2, false)), wasDue = setOf(PromptKey(1, false)))

        val workload = Workload.of(cards, newPerDay = 10, today, limit = 3, asked = asked)
        assertEquals(listOf(3L), workload.remaining.map { it.id })
        assertEquals(1, workload.waiting)

        val reached = Workload.of(cards, newPerDay = 10, today, limit = 2, asked = asked)
        assertTrue(reached.done)
        assertEquals(2, reached.waiting)
    }

    @Test
    fun `what a topic practised asked does not count against the limit`() {
        val cards = listOf(askedToday(1), due(2), due(3))
        // Asked today, but not by the day's workload.
        val asked = AskedToday(inWorkload = emptySet(), wasDue = setOf(PromptKey(1, false)))

        assertEquals(listOf(2L, 3L), Workload.of(cards, newPerDay = 10, today, limit = 2, asked = asked).remaining.map { it.id })
    }

    @Test
    fun `without a limit the workload is as before`() {
        val cards = listOf(due(1), due(2), new(3))
        val workload = Workload.of(cards, newPerDay = 10, today)

        assertEquals(listOf(1L, 2L, 3L), workload.remaining.map { it.id })
        assertEquals(0, workload.waiting)
    }

    @Test
    fun `asked both ways, the back does not come the day the front is due`() {
        val both = due(1).copy(bothWays = true, reverseStability = 3.0, reverseDifficulty = 5.0, reverseDue = today.minusDays(2), reverseInterval = 9)

        val workload = Workload.of(listOf(both, due(2)), newPerDay = 10, today, limit = 5)

        assertEquals(listOf(PromptKey(1, true), PromptKey(2, false)), workload.remaining.map { it.key })
    }

    @Test
    fun `the backlog counts what is due and what was due and asked today, so it stays the same over the day`() {
        val before = listOf(due(1), due(2), due(3), new(4))
        assertEquals(3, DailyLimits.backlog(before, AskedToday(), today))

        val after = listOf(askedToday(1), due(2), due(3), new(4))
        val asked = AskedToday(inWorkload = setOf(PromptKey(1, false)), wasDue = setOf(PromptKey(1, false)))
        assertEquals(3, DailyLimits.backlog(after, asked, today))
    }

    @Test
    fun `the overall limit is shared by backlog, the largest remainder first, by name if alike`() {
        // 100 × 1/3 each: 33 each and one left, to the first by name.
        assertEquals(
            mapOf(1L to 34, 2L to 33, 3L to 33),
            DailyLimits.shares(100, listOf(Backlog(1, "Arabisch", 10), Backlog(2, "Italienisch", 10), Backlog(3, "Türkisch", 10))),
        )
        // 50 × 30/40 = 37.5, 50 × 10/40 = 12.5: alike, so by name.
        assertEquals(mapOf(2L to 38, 1L to 12), DailyLimits.shares(50, listOf(Backlog(2, "Arabisch", 30), Backlog(1, "Italienisch", 10))))
        // 20 × 3/7 = 8.57, 20 × 4/7 = 11.43: the larger remainder gets the one left.
        assertEquals(mapOf(1L to 9, 2L to 11), DailyLimits.shares(20, listOf(Backlog(1, "Italienisch", 3), Backlog(2, "Arabisch", 4))))
    }

    @Test
    fun `a language without a backlog gets no share, and with none anywhere none does`() {
        assertEquals(mapOf(1L to 20), DailyLimits.shares(20, listOf(Backlog(1, "Arabisch", 5), Backlog(2, "Italienisch", 0))))
        assertEquals(emptyMap<Long, Int>(), DailyLimits.shares(20, listOf(Backlog(1, "Arabisch", 0))))
    }

    @Test
    fun `a language's limit on a day is the smaller of its own and its share`() {
        assertEquals(30, DailyLimits.limitOf(own = 30, share = 40))
        assertEquals(25, DailyLimits.limitOf(own = 30, share = 25))
        assertEquals(30, DailyLimits.limitOf(own = 30, share = null))
        assertEquals(25, DailyLimits.limitOf(own = null, share = 25))
        assertNull(DailyLimits.limitOf(own = null, share = null))
    }
}
