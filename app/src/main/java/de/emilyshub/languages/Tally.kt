package de.emilyshub.languages

import java.time.LocalDate
import java.time.YearMonth

/**
 * What some lessons come to (SPEC.md §15): their [minutes], how many there were ([count]), and what those with a price
 * cost ([costCents]); none if none had one.
 */
data class Tally(val minutes: Long = 0, val count: Int = 0, val costCents: Long? = null) {
    /** With [lesson] counted too. */
    operator fun plus(lesson: Lesson) = Tally(
        minutes = minutes + lesson.minutes,
        count = count + 1,
        costCents = lesson.priceCents?.let { (costCents ?: 0) + it } ?: costCents,
    )

    companion object {
        /** What [lessons] come to. */
        fun of(lessons: Iterable<Lesson>): Tally = lessons.fold(Tally(), Tally::plus)
    }
}

/** What lessons come to in the month of a day, its year, and in all ([total]). */
data class Periods(val month: Tally, val year: Tally, val total: Tally) {
    companion object {
        /** What [lessons] come to in the month and the year of [today], each by its date, and in all. */
        fun of(lessons: List<Lesson>, today: LocalDate): Periods {
            val month = YearMonth.from(today)
            return Periods(
                month = Tally.of(lessons.filter { YearMonth.from(it.date) == month }),
                year = Tally.of(lessons.filter { it.date.year == today.year }),
                total = Tally.of(lessons),
            )
        }
    }
}
