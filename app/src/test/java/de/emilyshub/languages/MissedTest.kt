package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The ways not known today (SPEC.md §15; #166): answered "Nochmal" today, open while that is their last answer today. */
class MissedTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-11-25")

    private val book = Card(id = 1, languageId = 1, front = "kitab", back = "Buch", stability = 1.0, difficulty = 5.0, lastReview = today, due = today.plusDays(1), interval = 1)
    private val pen = book.copy(id = 2, front = "qalam", back = "Stift", bothWays = true, reverseStability = 1.0, reverseDifficulty = 5.0, reverseLastReview = today)
    private val door = book.copy(id = 3, front = "bab", back = "Tür")
    private val libro = book.copy(id = 4, languageId = 2, front = "libro")

    @Test
    fun `a way answered Nochmal today is not known, open until known, by language, in the order first not known`() {
        val reviews = listOf(
            review(1, 2, "09:00", Rating.Again, reverse = true),
            review(2, 1, "09:01", Rating.Again),
            review(3, 3, "09:02", Rating.Good),
            review(4, 1, "09:03", Rating.Good),
            review(5, 4, "09:04", Rating.Again),
            // Yesterday's count for nothing.
            review(6, 3, "2026-11-24T20:00", Rating.Again),
        )

        val missed = Missed.of(listOf(book, pen, door, libro), reviews, today, zone)

        assertEquals(setOf(1L, 2L), missed.keys)
        val arabic = missed.getValue(1)
        assertEquals(listOf(PromptKey(2, true), PromptKey(1, false)), arabic.cards.map { it.key })
        assertEquals(setOf(PromptKey(2, true)), arabic.open)
        assertEquals(setOf(PromptKey(4, false)), missed.getValue(2).open)
    }

    @Test
    fun `practised, the open ones are asked once each, again after Nochmal, the others done`() {
        val missed = Missed(listOf(Prompt(book), Prompt(door)), open = setOf(PromptKey(3, false)))

        val start = Workload.ofMissed(missed, Round())
        assertEquals(listOf(PromptKey(3, false)) to listOf(PromptKey(1, false)), start.remaining.map { it.key } to start.doneToday.map { it.key })

        val again = Round().answered(Prompt(door), Rating.Again)
        val after = Workload.ofMissed(missed, again)
        assertEquals(emptyList<Prompt>(), after.remaining)
        assertEquals(Prompt(door), again.current(after))
    }

    private fun review(id: Long, cardId: Long, time: String, rating: Rating, reverse: Boolean = false) = CardReview(
        id = id,
        cardId = cardId,
        at = (if ('T' in time) java.time.LocalDateTime.parse(time) else today.atTime(java.time.LocalTime.parse(time))).atZone(zone).toInstant(),
        grade = rating.grade,
        reverse = reverse,
    )
}
