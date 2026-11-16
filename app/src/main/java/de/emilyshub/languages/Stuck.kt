package de.emilyshub.languages

/**
 * Cards that "haken" (SPEC.md §15; #149): asked one way, of its last [WINDOW] answers, its very first left out, at
 * least [AGAIN_LIMIT] were "Nochmal"; a card hakt if one of its ways does (#141). Told from the log alone, a sliding window
 * that nothing has to reset: known again, its "Nochmal" slip out of it and the mark goes by itself. Only a mark: such
 * a card is asked as any other.
 */
object Stuck {
    /** How many of a way's last answers count. */
    const val WINDOW = 8

    /** How many "Nochmal" among them make it hakt. */
    const val AGAIN_LIMIT = 4

    /** Whether the way these [answers] are to hakt; all of one card asked one way, in any order. */
    fun way(answers: List<CardReview>): Boolean = answers
        .sortedWith(compareBy(CardReview::at, CardReview::id))
        .drop(1)
        .takeLast(WINDOW)
        .count { it.grade == Rating.Again.grade } >= AGAIN_LIMIT

    /** Of the cards whose answers [reviews] has, the ids of those that hakt one way or the other. */
    fun cards(reviews: List<CardReview>): Set<Long> = reviews
        .groupBy { PromptKey(it.cardId, it.reverse) }
        .filterValues(::way)
        .keys.mapTo(mutableSetOf()) { it.cardId }
}
