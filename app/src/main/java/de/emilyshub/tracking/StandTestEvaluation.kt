package de.emilyshub.tracking

import java.time.Duration
import java.time.Instant

/** A heart rate of Garmin's curve, at a moment. */
data class Beat(val at: Instant, val bpm: Int)

/**
 * A point of a Stehtest as evaluated: its [pulse], own or, if [filled], from Garmin's curve; its [rise] over lying; the
 * blood pressure as measured and its drops below lying, none where it rose.
 */
data class StandPointResult(
    val minute: Int,
    val pulse: Int?,
    val filled: Boolean,
    val rise: Int?,
    val systolic: Int?,
    val diastolic: Int?,
    val systolicDrop: Int?,
    val diastolicDrop: Int?,
)

/**
 * The evaluation of a Stehtest (SPEC.md §17): the lying pulse, own or [lyingFilled] from the curve; each point up to the
 * abort; the largest rise over lying and the minutes rising by [StandTestRules.RISE] or more; for leaning, the mean of
 * the last three minutes and its rise; the largest drop of the blood pressure within [StandTestRules.EARLY_MINUTES] and
 * after, systolic and diastolic, each none without a pressure lying and there; the minutes systolic below
 * [StandTestRules.SYSTOLIC_LOW]. [complete] if all its points were reached.
 */
data class StandTestResult(
    val lyingPulse: Int?,
    val lyingFilled: Boolean,
    val points: List<StandPointResult>,
    val maxRise: Int?,
    val risesAtLeast30: List<Int>,
    val lastThreeMean: Double?,
    val lastThreeRise: Double?,
    val systolicDropEarly: Int?,
    val diastolicDropEarly: Int?,
    val systolicDropLate: Int?,
    val diastolicDropLate: Int?,
    val systolicBelow90: List<Int>,
    val complete: Boolean,
)

/** The thresholds of a Stehtest, in one place with their sources (SPEC.md §17, "Quellen der Schwellen"). */
object StandTestRules {
    /** A rise of the pulse over lying, per minute, that counts. [CMAJ-POTS-2022] [NIH-POTS-2021] [DGN-SYNK-2020] */
    const val RISE = 30

    /** Up to which minute standing a drop of the blood pressure counts as early, later ones as delayed. [ESC-SYNK-2018] */
    const val EARLY_MINUTES = 3

    /** A systolic pressure below this, in mmHg, counts. [ESC-SYNK-2018] */
    const val SYSTOLIC_LOW = 90

    /** How many of the last minutes the mean of the lean test is taken over. [LEE-2020] */
    const val LAST_MINUTES = 3

    /** How long to lie before standing up, in minutes, the least and the most. [CMAJ-POTS-2022] [LEE-2020] */
    val LYING_MINUTES = 5..10

    /** How far from a point a heart rate of the curve may lie to fill it: half a minute either way, own choice. */
    val FILL_WINDOW: Duration = Duration.ofSeconds(30)

    /** The sources of these thresholds, for the screen. */
    val SOURCES = listOf(Source.CmajPots2022, Source.Lee2020, Source.DgnSyncope2020, Source.EscSyncope2018, Source.NihPots2021)
}

/**
 * Evaluates a Stehtest as SPEC.md §17 says, pure logic. Its own points are the main values; a pulse missing at a point
 * reached is filled from Garmin's [curve], the heart rate nearest to its minute within [StandTestRules.FILL_WINDOW],
 * and marked; the lying one from the last heart rate of the minute before standing up. During a Stehtest the curve
 * is at rest, so tier A ([GarminTiers.STAND_TEST]).
 */
object StandTestEvaluation {

    fun evaluate(test: StandTest, points: List<StandPoint>, curve: List<Beat>): StandTestResult {
        val standingAt = test.standingAt
        val byMinute = points.associateBy { it.minute }
        val ownLying = test.lyingPulse
        val filledLying = if (ownLying == null && standingAt != null) lyingFromCurve(standingAt, curve) else null
        val lying = ownLying ?: filledLying
        val reached = when {
            standingAt == null -> emptyList()
            test.abortCause != null -> test.variant.minutes.filter { minute -> test.abortedMinute != null && minute <= test.abortedMinute }
            else -> test.variant.minutes
        }
        val results = reached.map { minute ->
            val point = byMinute[minute]
            val fromCurve = if (point?.pulse == null) nearest(standingAt!!.plus(Duration.ofMinutes(minute.toLong())), curve) else null
            val pulse = point?.pulse ?: fromCurve
            StandPointResult(
                minute = minute,
                pulse = pulse,
                filled = point?.pulse == null && fromCurve != null,
                rise = if (pulse != null && lying != null) pulse - lying else null,
                systolic = point?.systolic,
                diastolic = point?.diastolic,
                systolicDrop = drop(test.lyingSystolic, point?.systolic),
                diastolicDrop = drop(test.lyingDiastolic, point?.diastolic),
            )
        }
        val early = results.filter { it.minute <= StandTestRules.EARLY_MINUTES }
        val late = results.filter { it.minute > StandTestRules.EARLY_MINUTES }
        val lastThree = lastThreeMean(test.variant, results)
        return StandTestResult(
            lyingPulse = lying,
            lyingFilled = filledLying != null,
            points = results,
            maxRise = results.mapNotNull { it.rise }.maxOrNull(),
            risesAtLeast30 = results.filter { (it.rise ?: Int.MIN_VALUE) >= StandTestRules.RISE }.map { it.minute },
            lastThreeMean = lastThree,
            lastThreeRise = if (lastThree != null && lying != null) lastThree - lying else null,
            systolicDropEarly = early.mapNotNull { it.systolicDrop }.maxOrNull(),
            diastolicDropEarly = early.mapNotNull { it.diastolicDrop }.maxOrNull(),
            systolicDropLate = late.mapNotNull { it.systolicDrop }.maxOrNull(),
            diastolicDropLate = late.mapNotNull { it.diastolicDrop }.maxOrNull(),
            systolicBelow90 = results.filter { (it.systolic ?: Int.MAX_VALUE) < StandTestRules.SYSTOLIC_LOW }.map { it.minute },
            complete = standingAt != null && reached == test.variant.minutes && test.abortCause == null,
        )
    }

    /** The mean pulse of the last [StandTestRules.LAST_MINUTES] of a lean test, only if all of them have one. */
    private fun lastThreeMean(variant: StandVariant, results: List<StandPointResult>): Double? {
        if (variant != StandVariant.Leaning) return null
        val last = variant.minutes.takeLast(StandTestRules.LAST_MINUTES)
        val pulses = last.map { minute -> results.firstOrNull { it.minute == minute }?.pulse ?: return null }
        return pulses.average()
    }

    /** How far [value] lies below [lying], none if either is missing; a rise is no drop. */
    private fun drop(lying: Int?, value: Int?): Int? = if (lying == null || value == null) null else (lying - value).coerceAtLeast(0)

    /** The heart rate of [curve] nearest to [at], within [StandTestRules.FILL_WINDOW]. */
    private fun nearest(at: Instant, curve: List<Beat>): Int? = curve
        .map { it to Duration.between(at, it.at).abs() }
        .filter { (_, off) -> off <= StandTestRules.FILL_WINDOW }
        .minByOrNull { (_, off) -> off }
        ?.first?.bpm

    /** The last heart rate of [curve] in the minute before [standingAt]. */
    private fun lyingFromCurve(standingAt: Instant, curve: List<Beat>): Int? = curve
        .filter { !it.at.isAfter(standingAt) && !it.at.isBefore(standingAt.minus(Duration.ofMinutes(1))) }
        .maxByOrNull { it.at }
        ?.bpm
}
