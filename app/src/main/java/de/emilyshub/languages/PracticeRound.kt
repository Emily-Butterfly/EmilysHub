package de.emilyshub.languages

import java.io.Serializable
import java.time.LocalDate

/**
 * The Runde (CONTEXT.md; SPEC.md §15): one practice of a language's Tagespensum, or of all the cards of a topic or exam
 * ([selection]; #107, #148), from opening it to leaving it. A plain value the screen keeps through a turn and the lock:
 * the [round] of "Nochmal" and of those asked, the [last] answer with the round before it, to take back one step while
 * it is open (#145), and each answer and each taken back a [step], which covers the card anew. A card on its way (an
 * answer being kept, or one being taken back) stays shown, and nothing else can be done meanwhile ([idle]); it is not
 * kept, as it is under way only while the screen is.
 */
data class PracticeRound(
    val selection: CardSelection? = null,
    val round: Round = Round(),
    val last: LastAnswer? = null,
    val step: Int = 0,
    @Transient val onItsWay: OnItsWay? = null,
) : Serializable {

    /** Whether nothing is on its way: a card can be answered, taken back or edited. */
    val idle: Boolean get() = onItsWay == null

    /** Whether the last answer can be taken back now. */
    val canTakeBack: Boolean get() = last != null && idle

    /**
     * What the round asks as it stands on [today]: the language's Tagespensum ([workload]) as it is; or, of a selection,
     * its [selected] cards, each once, again only after "Nochmal" (#107).
     */
    fun cards(workload: Workload?, selected: List<Prompt>?, today: LocalDate): Workload? =
        if (selection == null) workload else selected?.let { Workload.ofSelection(it, round, today) }

    /** The card shown of [cards], the way it is asked: one on its way, else the round's next; none once all are done. */
    fun shown(cards: Workload): Prompt? = onItsWay?.prompt ?: round.current(cards)

    /** Which card of how many the one shown is ("7 von 23"). */
    fun position(cards: Workload): Pair<Int, Int> = round.position(cards)

    /** [prompt] is being answered: shown until the answer is kept ([answered]). */
    fun answering(prompt: Prompt): PracticeRound = copy(onItsWay = OnItsWay(prompt, takenBack = false))

    /**
     * [prompt] answered [rating], kept by [flashcards]: of a selection, its new cards do not count against those of the
     * day (#107). Then the next card is shown, and this answer is the one to take back.
     */
    suspend fun answered(flashcards: Flashcards, prompt: Prompt, rating: Rating): PracticeRound {
        val answered = flashcards.answer(prompt.id, rating, countsAsNew = selection == null, reverse = prompt.reverse)
        return copy(
            round = round.answered(prompt, rating),
            step = step + 1,
            last = answered?.let { LastAnswer(it, round) },
            onItsWay = null,
        )
    }

    /**
     * The last answer being taken back: the round at once as it was before it, so that it stays so if the screen is turned
     * meanwhile, and its card shown again, covered, until [takenBack] and the cards have it again ([settledBy]).
     */
    fun takingBack(): PracticeRound {
        val taking = last ?: return this
        return copy(round = taking.round, last = null, step = step + 1, onItsWay = OnItsWay(taking.answered.before, takenBack = true, answered = taking.answered))
    }

    /** The answer taken back by [flashcards]: its card planned as before, its entry gone (#145). */
    suspend fun takenBack(flashcards: Flashcards): PracticeRound {
        val taking = onItsWay?.answered ?: return this
        flashcards.takeBack(taking)
        return copy(onItsWay = onItsWay.copy(answered = null))
    }

    /**
     * The round as [cards] now stand: a card taken back is the round's own again once they have it again, or no more
     * once it is gone, edited away say.
     */
    fun settledBy(cards: Workload): PracticeRound {
        val way = onItsWay ?: return this
        if (!way.takenBack || way.answered != null) return this
        val key = way.prompt.key
        val back = round.current(cards)?.key == key || (cards.remaining + cards.doneToday).none { it.key == key }
        return if (back) copy(onItsWay = null) else this
    }

    /** A card on its way: being answered, or taken back, with the answer still to take back while [answered] is not none. */
    data class OnItsWay(val prompt: Prompt, val takenBack: Boolean, val answered: Answered? = null)
}

/** The [answered] last in a Runde, and the [round] as it stood before it, to take back (#145). */
data class LastAnswer(val answered: Answered, val round: Round) : Serializable
