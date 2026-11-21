package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** How regularly a language's cards are practised, and what comes next, in its evaluation (SPEC.md §15; #150). */
class PractisedTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(time: String): Instant = LocalDateTime.parse(time).atZone(zone).toInstant()

    private fun review(cardId: Long, time: String, rating: Rating, reverse: Boolean = false) =
        CardReview(cardId = cardId, at = at(time), grade = rating.grade, reverse = reverse)

    @Test
    fun `a day practised is one with an answer by the phone's calendar, across midnight and the turn of the month`() {
        val reviews = listOf(
            review(1, "2026-10-31T23:30", Rating.Good), // October, by the phone's clock, though November in UTC
            review(1, "2026-11-01T00:10", Rating.Good),
            review(2, "2026-11-01T08:00", Rating.Good),
            review(2, "2026-11-16T21:00", Rating.Again),
            review(3, "2025-12-31T12:00", Rating.Good),
        )

        val practised = Practised.of(reviews, today = LocalDate.parse("2026-11-16"), zone)

        assertEquals(Practised.Tally(days = 2, answers = 3, known = 0.5), practised.month)
        assertEquals("of the month's days up to today", 16, practised.daysOfMonth)
        assertEquals("the first answers of each way, in October and 2025, left out", Practised.Tally(days = 3, answers = 4, known = 0.5), practised.year)
        assertEquals(Practised.Tally(days = 4, answers = 5, known = 0.5), practised.total)
    }

    @Test
    fun `known is the share of answers but Nochmal, without the very first answer of each way`() {
        val reviews = listOf(
            review(1, "2026-11-02T08:00", Rating.Again), // first of its front: not counted
            review(1, "2026-11-03T08:00", Rating.Again),
            review(1, "2026-11-04T08:00", Rating.Hard),
            review(1, "2026-11-05T08:00", Rating.Again, reverse = true), // first of its back: not counted
            review(1, "2026-11-06T08:00", Rating.Easy, reverse = true),
            review(2, "2026-11-06T09:00", Rating.Good), // first of its front: not counted
        )

        val month = Practised.of(reviews, today = LocalDate.parse("2026-11-16"), zone).month

        assertEquals(6, month.answers)
        assertEquals(2.0 / 3, checkNotNull(month.known), 1e-9)
    }

    @Test
    fun `a first answer before the month leaves the month's answers all counted`() {
        val reviews = listOf(review(1, "2026-10-20T08:00", Rating.Good), review(1, "2026-11-02T08:00", Rating.Again))

        assertEquals(0.0, checkNotNull(Practised.of(reviews, LocalDate.parse("2026-11-16"), zone).month.known), 1e-9)
    }

    @Test
    fun `with only first answers, or none, there is no share known`() {
        val practised = Practised.of(listOf(review(1, "2026-11-02T08:00", Rating.Good)), LocalDate.parse("2026-11-16"), zone)

        assertNull(practised.month.known)
        assertEquals(Practised.Tally(days = 0, answers = 0, known = null), Practised.of(emptyList(), LocalDate.parse("2026-11-16"), zone).total)
    }

    @Test
    fun `the preview counts the ways due tomorrow and in the next seven days, both ways of a card, no new ones`() {
        val today = LocalDate.parse("2026-11-16")
        val cards = listOf(
            Card(id = 1, languageId = 1, front = "a", back = "a", stability = 1.0, difficulty = 5.0, due = today.plusDays(1)),
            Card(
                id = 2, languageId = 1, front = "b", back = "b", stability = 1.0, difficulty = 5.0, due = today.plusDays(7),
                bothWays = true, reverseStability = 1.0, reverseDifficulty = 5.0, reverseDue = today.plusDays(1),
            ),
            Card(id = 3, languageId = 1, front = "c", back = "c", stability = 1.0, difficulty = 5.0, due = today.plusDays(8)),
            Card(id = 4, languageId = 1, front = "d", back = "d", stability = 1.0, difficulty = 5.0, due = today),
            Card(id = 5, languageId = 1, front = "e", back = "e"),
            // Asked both ways no more: what was planned for its back does not count.
            Card(id = 6, languageId = 1, front = "f", back = "f", reverseStability = 1.0, reverseDifficulty = 5.0, reverseDue = today.plusDays(1)),
        )

        assertEquals(DuePreview(tomorrow = 2, nextWeek = 3), DuePreview.of(cards, today))
    }
}
