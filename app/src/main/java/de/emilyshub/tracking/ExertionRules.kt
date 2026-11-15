package de.emilyshub.tracking

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** A day above the mean of the days before: its [value] and the [mean] it is set against. */
data class SuggestionCheck(val value: Long, val mean: Double)

/** The gap from an exertion to the start of an episode, in whole [hours]; [approximate] if a time was not known. */
data class Gap(val hours: Long, val approximate: Boolean)

/**
 * The pacing threshold (SPEC.md §17), in beats per minute: one's [own] value, as from a cardiopulmonary exercise test,
 * or the rule of thumb, the mean of the morning resting pulses plus [RULE_OF_THUMB], "Faustregel, schwach belegt".
 * [WORKWELL-15] [KELLER-2024]
 */
data class PacingThreshold(val bpm: Int, val own: Boolean) {
    companion object {
        /** What the rule of thumb adds to the mean of the morning pulses. [WORKWELL-15] */
        const val RULE_OF_THUMB = 15

        /** How many days of morning pulses the rule of thumb takes. */
        const val MORNING_DAYS = 7L

        /** The sources of the threshold, for the screen. */
        val SOURCES = listOf(Source.Workwell15, Source.Keller2024)

        /** The source of the hint on medications that lower the pulse. */
        val PULSE_LOWERING_SOURCE = Source.DiazBuschmann2014

        /** The threshold by [own] value, else by the [mornings]' resting pulses; none without either. */
        fun of(own: Int?, mornings: List<Int>): PacingThreshold? = when {
            own != null -> PacingThreshold(own, own = true)
            mornings.isEmpty() -> null
            else -> PacingThreshold(mornings.average().roundToInt() + RULE_OF_THUMB, own = false)
        }

        /** Whether the hint shows that medications lowering the pulse shift the threshold: so without one's [own] value. */
        fun warnsOfPulseLowering(own: Int?, anyLowersPulse: Boolean): Boolean = own == null && anyLowersPulse
    }
}

/**
 * The rules of exertions and episodes (SPEC.md §17, "Anstrengungen, PEM & Crash"), pure logic, their working values in
 * one place: these are the app's own, no guideline's.
 */
object ExertionRules {
    /** The evening's load from which it counts as an exertion of the day. */
    const val EVENING_FROM = 2

    /** How far above the mean a day must lie to be suggested: more than 150 percent (own working value). */
    const val FACTOR_PERCENT = 150

    /** The days before a day its mean is taken over (own working value). */
    const val MEAN_DAYS = 14L

    /** How many of those days must have data for a suggestion (own working value). */
    const val MIN_DAYS = 7

    /** The strength from which the following evenings ask after an exertion. */
    val FOLLOW_UP_STRENGTH = 2..3

    /** How many evenings after it ask. */
    const val FOLLOW_UP_DAYS = 3L

    /** How many days before an episode's start its triggers are chosen from. */
    const val TRIGGER_DAYS = 3L

    /** The scale of strength. */
    val STRENGTH = 0..3

    /** The scale of an episode's severity. */
    val SEVERITY = 1..3

    /** The time an exertion or an episode is taken to be at when it is not known. */
    val UNKNOWN_TIME: LocalTime = LocalTime.NOON

    /** The exertions of [entry]'s evening: each load of [EVENING_FROM] or more, of its kind and strength. */
    fun fromEvening(entry: EveningEntry): Map<ExertionKind, Int> = listOfNotNull(
        entry.loadPhysical?.let { ExertionKind.Physical to it },
        entry.loadCognitive?.let { ExertionKind.Cognitive to it },
        entry.loadEmotional?.let { ExertionKind.Emotional to it },
    ).filter { (_, level) -> level >= EVENING_FROM }.toMap()

    /**
     * Whether [day] in [byDay] lies above [FACTOR_PERCENT] of the mean of the [MEAN_DAYS] before it, counting only the
     * days with data and only with [MIN_DAYS] of them: its value and that mean, or none.
     */
    fun exceeds(byDay: Map<LocalDate, Long>, day: LocalDate): SuggestionCheck? {
        val value = byDay[day] ?: return null
        val before = (1..MEAN_DAYS).mapNotNull { byDay[day.minusDays(it)] }
        if (before.size < MIN_DAYS) return null
        val mean = before.average()
        return if (value * 100 > mean * FACTOR_PERCENT) SuggestionCheck(value, mean) else null
    }

    /** The exertions of [exertions] the evening of [day] asks after: of strength 2–3, on one of the three days before. */
    fun followUps(day: LocalDate, exertions: List<Exertion>): List<Exertion> = exertions.filter {
        it.strength in FOLLOW_UP_STRENGTH && it.date.isBefore(day) && !it.date.isBefore(day.minusDays(FOLLOW_UP_DAYS))
    }

    /**
     * The exertions of [exertions] to choose as triggers of [episode]: from the day three days before its start up to
     * its start, none later on the day it began. A time not known counts as [UNKNOWN_TIME]; with the start's not known,
     * all of that day count.
     */
    fun candidates(episode: Episode, exertions: List<Exertion>): List<Exertion> = exertions.filter {
        val inDays = !it.date.isAfter(episode.startDate) && !it.date.isBefore(episode.startDate.minusDays(TRIGGER_DAYS))
        val afterStart = episode.startTime != null && it.date == episode.startDate && (it.time ?: UNKNOWN_TIME).isAfter(episode.startTime)
        inDays && !afterStart
    }

    /** How many hours after [exertion] [episode] began, a time not known taken as [UNKNOWN_TIME]. */
    fun hoursAfter(exertion: Exertion, episode: Episode): Gap {
        val from = exertion.date.atTime(exertion.time ?: UNKNOWN_TIME)
        val to = episode.startDate.atTime(episode.startTime ?: UNKNOWN_TIME)
        return Gap(Duration.between(from, to).toHours(), exertion.time == null || episode.startTime == null)
    }
}
