package de.emilyshub.languages

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * How regularly a language's cards were practised (SPEC.md §15; #150), from the log of its answers: in the [month] of
 * a day, of whose days [daysOfMonth] have come so far, in its [year] and in all ([total]).
 */
data class Practised(val month: Tally, val daysOfMonth: Int, val year: Tally, val total: Tally) {
    /**
     * Some answers: on how many [days] there were any, how many there were ([answers]), and the share of them [known],
     * all but "Nochmal", without the very first answer of each way of a card, as it was not learnt then; none while
     * nothing is left to count it by.
     */
    data class Tally(val days: Int, val answers: Int, val known: Double?)

    companion object {
        /** What [reviews] come to as of [today], each by its day on the phone's calendar ([zone]). */
        fun of(reviews: List<CardReview>, today: LocalDate, zone: ZoneId): Practised {
            val firsts = reviews.groupBy { PromptKey(it.cardId, it.reverse) }
                .mapNotNull { (_, ofWay) -> ofWay.minWithOrNull(compareBy<CardReview> { it.at }.thenBy { it.id }) }
                .toSet()
            val dated = reviews.map { it to it.at.atZone(zone).toLocalDate() }
            fun tally(within: (LocalDate) -> Boolean): Tally {
                val those = dated.filter { (_, day) -> within(day) }
                val counted = those.map { it.first }.filter { it !in firsts }
                return Tally(
                    days = those.map { it.second }.distinct().size,
                    answers = those.size,
                    known = counted.takeIf { it.isNotEmpty() }?.let { all -> all.count { it.grade != Rating.Again.grade }.toDouble() / all.size },
                )
            }
            val month = YearMonth.from(today)
            return Practised(
                month = tally { YearMonth.from(it) == month },
                daysOfMonth = today.dayOfMonth,
                year = tally { it.year == today.year },
                total = tally { true },
            )
        }
    }
}

/**
 * What comes next for a language's cards (SPEC.md §15; #150): how many ways of its cards are due [tomorrow], and in the
 * next seven days from tomorrow on ([nextWeek]); new ones not, as they are due on no day.
 */
data class DuePreview(val tomorrow: Int, val nextWeek: Int) {
    companion object {
        /** Of [cards], the ways they are asked, as of [today]. */
        fun of(cards: List<Card>, today: LocalDate): DuePreview {
            val dues = cards.flatMap { it.prompts }.mapNotNull { it.due }
            val tomorrow = today.plusDays(1)
            return DuePreview(
                tomorrow = dues.count { it == tomorrow },
                nextWeek = dues.count { !it.isBefore(tomorrow) && !it.isAfter(today.plusDays(PREVIEW_DAYS)) },
            )
        }

        /** How many days "nächste 7 Tage" looks ahead. */
        private const val PREVIEW_DAYS = 7L
    }
}
