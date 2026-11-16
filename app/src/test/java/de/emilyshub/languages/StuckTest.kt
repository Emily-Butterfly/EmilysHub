package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** When a card "hakt" (SPEC.md §15; #149): of the last 8 answers of one of its ways, its very first left out, 4 "Nochmal". */
class StuckTest {

    @Test
    fun `a way hakt from 4 Nochmal among its last 8 answers, not from 3`() {
        assertFalse(Stuck.way(answers(Rating.Good, Rating.Again, Rating.Again, Rating.Again, Rating.Good, Rating.Good, Rating.Good, Rating.Good, Rating.Good)))
        assertTrue(Stuck.way(answers(Rating.Good, Rating.Again, Rating.Again, Rating.Again, Rating.Again, Rating.Good, Rating.Good, Rating.Good, Rating.Good)))
    }

    @Test
    fun `its very first answer does not count, as a new card is rarely known at once`() {
        assertFalse(Stuck.way(answers(Rating.Again, Rating.Again, Rating.Again, Rating.Again)))
        assertTrue(Stuck.way(answers(Rating.Again, Rating.Again, Rating.Again, Rating.Again, Rating.Again)))
    }

    @Test
    fun `known again, its Nochmal slip out of the last 8 and the mark goes by itself`() {
        val stuck = listOf(Rating.Good) + List(4) { Rating.Again }
        assertTrue(Stuck.way(answers(*(stuck + List(4) { Rating.Good }).toTypedArray())))
        assertFalse(Stuck.way(answers(*(stuck + List(5) { Rating.Good }).toTypedArray())))
    }

    @Test
    fun `each way counts for itself, and a card hakt if one of its ways does`() {
        val front = answers(Rating.Good, Rating.Again, Rating.Again, cardId = 1)
        val back = answers(Rating.Good, Rating.Again, Rating.Again, cardId = 1, reverse = true)
        assertEquals("two each way are not four", emptySet<Long>(), Stuck.cards(front + back))

        val backStuck = answers(Rating.Good, Rating.Again, Rating.Again, Rating.Again, Rating.Again, cardId = 1, reverse = true)
        val other = answers(Rating.Good, Rating.Good, cardId = 2)
        assertEquals(setOf(1L), Stuck.cards(front + backStuck + other))
    }

    @Test
    fun `answers are taken in the order given, whatever order the log comes in`() {
        val stuck = answers(Rating.Good, Rating.Again, Rating.Again, Rating.Again, Rating.Again)
        assertTrue(Stuck.way(stuck.reversed()))
        // The first is the one answered first, wherever it stands.
        assertFalse(Stuck.way(answers(Rating.Again, Rating.Again, Rating.Again, Rating.Again, Rating.Good).reversed()))
    }

    private fun answers(vararg ratings: Rating, cardId: Long = 1, reverse: Boolean = false): List<CardReview> =
        ratings.mapIndexed { i, rating ->
            val id = i + 1L + if (reverse) 100 else 0
            CardReview(id = id, cardId = cardId, at = Instant.parse("2026-11-01T09:00:00Z").plusSeconds(86_400L * i), grade = rating.grade, reverse = reverse)
        }
}
