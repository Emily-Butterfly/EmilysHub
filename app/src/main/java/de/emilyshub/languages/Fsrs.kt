package de.emilyshub.languages

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** The four answers to a card (SPEC.md §15, Vokabeltrainer), by their [grade] in FSRS. */
enum class Rating(val grade: Int) {
    /** "Nochmal": not known. */
    Again(1),

    /** "Schwer": known, but with effort. */
    Hard(2),

    /** "Gut". */
    Good(3),

    /** "Leicht". */
    Easy(4),
}

/** What FSRS knows of a card: its [stability] in days and its [difficulty], from 1 to 10. A new card has none. */
data class Memory(val stability: Double, val difficulty: Double)

/** A card answered: its [memory] now, and the [interval] in days until it is due again. */
data class Scheduled(val memory: Memory, val interval: Int)

/**
 * FSRS-6, the Free Spaced Repetition Scheduler of the project Open Spaced Repetition, worked out anew from its
 * published formulas (the wiki "The Algorithm" of open-spaced-repetition/awesome-fsrs and `fsrs/scheduler.py` of
 * py-fsrs 6.3.2), with its 21 default parameters from there, as `docs/research/spaced-repetition.md` §4 has them. No code
 * of theirs is taken over.
 *
 * As EmilysHub plans (§5 there): a retention wanted of 90 %, at most 36 500 days, in whole days of the calendar, without
 * learning steps in minutes and without fuzz, so that each answer gives one interval, the same each time.
 */
object Fsrs {
    /** The 21 default parameters of FSRS-6, w0 to w20. */
    private val W = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )

    /** The retention wanted. */
    private const val RETENTION = 0.9

    /** The longest interval, in days. */
    const val MAX_INTERVAL = 36_500

    private const val MIN_STABILITY = 0.001
    private val DECAY = -W[20]
    private val FACTOR = 0.9.pow(1 / DECAY) - 1

    /**
     * The card answered [rating] [days] after it was last, as §4.4 there has it: a new one ([memory] none) starts, one
     * answered the same day again ([days] 0) grows by the formula for that day, any other by how likely it was still known.
     */
    fun review(memory: Memory?, days: Long, rating: Rating): Scheduled {
        val next = when {
            memory == null -> Memory(initialStability(rating), initialDifficulty(rating).coerceIn(1.0, 10.0))
            days < 1 -> Memory(sameDayStability(memory.stability, rating), nextDifficulty(memory.difficulty, rating))
            else -> {
                val r = retrievability(days.toDouble(), memory.stability)
                val stability = if (rating == Rating.Again) {
                    forgetStability(memory.difficulty, memory.stability, r)
                } else {
                    recallStability(memory.difficulty, memory.stability, r, rating)
                }
                Memory(stability, nextDifficulty(memory.difficulty, rating))
            }
        }
        val memoryNow = next.copy(stability = max(next.stability, MIN_STABILITY))
        return Scheduled(memoryNow, interval(memoryNow.stability))
    }

    /** The interval for [stability] at the retention wanted: at least a day, at most [MAX_INTERVAL]. */
    fun interval(stability: Double): Int {
        val days = (stability / FACTOR * (RETENTION.pow(1 / DECAY) - 1)).roundToLong()
        return days.coerceIn(1, MAX_INTERVAL.toLong()).toInt()
    }

    /** How likely the card is still known [days] after it was last. */
    private fun retrievability(days: Double, stability: Double) = (1 + FACTOR * days / stability).pow(DECAY)

    private fun initialStability(rating: Rating) = max(W[rating.grade - 1], MIN_STABILITY)

    /** Not yet kept within 1 and 10, as the difficulty's mean reversion wants it. */
    private fun initialDifficulty(rating: Rating) = W[4] - exp(W[5] * (rating.grade - 1)) + 1

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -W[6] * (rating.grade - 3)
        val damped = difficulty + (10 - difficulty) * delta / 9
        return (W[7] * initialDifficulty(Rating.Easy) + (1 - W[7]) * damped).coerceIn(1.0, 10.0)
    }

    private fun recallStability(difficulty: Double, stability: Double, r: Double, rating: Rating): Double {
        val hard = if (rating == Rating.Hard) W[15] else 1.0
        val easy = if (rating == Rating.Easy) W[16] else 1.0
        return stability * (1 + exp(W[8]) * (11 - difficulty) * stability.pow(-W[9]) * (exp((1 - r) * W[10]) - 1) * hard * easy)
    }

    private fun forgetStability(difficulty: Double, stability: Double, r: Double): Double {
        val long = W[11] * difficulty.pow(-W[12]) * ((stability + 1).pow(W[13]) - 1) * exp((1 - r) * W[14])
        return min(long, stability / exp(W[17] * W[18]))
    }

    private fun sameDayStability(stability: Double, rating: Rating): Double {
        val growth = exp(W[17] * (rating.grade - 3 + W[18])) * stability.pow(-W[19])
        return stability * if (rating.grade >= 2) max(growth, 1.0) else growth
    }
}
