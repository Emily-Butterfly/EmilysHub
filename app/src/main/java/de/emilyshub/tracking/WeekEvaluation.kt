package de.emilyshub.tracking

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A week of Tracking, Monday to Sunday (SPEC.md §17), by its [monday]. */
data class Week(val monday: LocalDate) : java.io.Serializable {
    init {
        require(monday.dayOfWeek == DayOfWeek.MONDAY) { "A week begins on a Monday" }
    }

    /** Its Sunday. */
    val sunday: LocalDate get() = monday.plusDays(6)

    /** Whether [day] lies within it. */
    operator fun contains(day: LocalDate): Boolean = !day.isBefore(monday) && !day.isAfter(sunday)

    /** The week before. */
    fun previous(): Week = Week(monday.minusWeeks(1))

    /** The week after. */
    fun next(): Week = Week(monday.plusWeeks(1))

    companion object {
        /** The week [day] lies in. */
        fun of(day: LocalDate): Week = Week(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
}

/**
 * A figure of the week evaluation: its [value], none without data, shown as "–" and never as 0, and the number of entries
 * it rests on ([basis]).
 */
data class Figure(val value: Double?, val basis: Int) {
    companion object {
        /** No data. */
        val NONE = Figure(null, 0)

        /** How many of [basis] entries counted, none without any. */
        fun count(count: Int, basis: Int): Figure = if (basis == 0) NONE else Figure(count.toDouble(), basis)

        /** The mean of [values], none without any. */
        fun mean(values: Collection<Number>): Figure = if (values.isEmpty()) NONE else Figure(values.map { it.toDouble() }.average(), values.size)
    }
}

/** The mean of a symptom in the week. */
data class SymptomFigure(val symptom: Symptom, val mean: Figure)

/**
 * The figures of a week, [from] Monday [to] Sunday (SPEC.md §17, "Wochenauswertung"), or of the weeks of the doctor's
 * summary, each with the entries it rests on: the morning resting
 * pulse, its mean, least, most and the days above [WeekRules.PULSE_HIGH]; the Stehtests, their largest rise, how many
 * rose by [StandTestRules.RISE] in at least two measurements, how many dropped by [WeekRules.SYSTOLIC_DROP] /
 * [WeekRules.DIASTOLIC_DROP] mmHg within three minutes and after; the events with breathlessness of 2 or more and with a
 * change of the skin, and the mean minutes till it faded; the exertions, the episodes and the mean hours from a trigger
 * to the start; the mean of each symptom; the days with less than [WeekRules.FLUID_LOW_DECILITRES] drunk; the nights with
 * sleep's quality 2 or worse; the evenings with an intake otherwise than planned; Garmin's steps and minutes above the pacing threshold
 * per day; and the share of entries made up later.
 */
data class WeekFigures(
    val from: LocalDate,
    val to: LocalDate,
    val pulseMean: Figure,
    val pulseMin: Figure,
    val pulseMax: Figure,
    val pulseHighDays: Figure,
    val standTests: Figure,
    val standMaxRise: Figure,
    val standRiseTwice: Figure,
    val standDropEarly: Figure,
    val standDropLate: Figure,
    val events: Figure,
    val eventsBreathless: Figure,
    val eventsSkin: Figure,
    val skinFadeMinutes: Figure,
    val exertions: Figure,
    val episodes: Figure,
    val episodeGapHours: Figure,
    val symptoms: List<SymptomFigure>,
    val fluidLowDays: Figure,
    val poorNights: Figure,
    val deviations: Figure,
    val stepsPerDay: Figure,
    val minutesAbovePerDay: Figure,
    val backfilledShare: Figure,
)

/** The thresholds of the week evaluation (SPEC.md §17), in one place. */
object WeekRules {
    /** A morning resting pulse above this, per minute, counts. [HRS-2015] */
    const val PULSE_HIGH = 100

    /** A drop of the systolic pressure by this much, in mmHg, counts. [ESC-SYNK-2018] */
    const val SYSTOLIC_DROP = 20

    /** A drop of the diastolic pressure by this much, in mmHg, counts. [ESC-SYNK-2018] */
    const val DIASTOLIC_DROP = 10

    /** Less drunk than this, in tenths of a litre, counts: 1.5 l, eigener Arbeitswert, no guideline's. */
    const val FLUID_LOW_DECILITRES = 15

    /** Breathlessness and sleep's quality from this on count, on the scale 0–3: eigene Festlegung. */
    const val LEVEL_HIGH = 2

    /** How many measurements of a Stehtest must rise by [StandTestRules.RISE] for it to count. [NIH-POTS-2021] [CMAJ-POTS-2022] */
    const val RISES_NEEDED = 2

    /** Whether [result] dropped within [StandTestRules.EARLY_MINUTES]: by the thresholds above, or systolic below 90. */
    fun droppedEarly(result: StandTestResult): Boolean =
        (result.systolicDropEarly ?: 0) >= SYSTOLIC_DROP || (result.diastolicDropEarly ?: 0) >= DIASTOLIC_DROP ||
            result.systolicBelow90.any { it <= StandTestRules.EARLY_MINUTES }

    /** Whether [result] dropped after [StandTestRules.EARLY_MINUTES] only. */
    fun droppedLate(result: StandTestResult): Boolean = !droppedEarly(result) &&
        ((result.systolicDropLate ?: 0) >= SYSTOLIC_DROP || (result.diastolicDropLate ?: 0) >= DIASTOLIC_DROP ||
            result.systolicBelow90.any { it > StandTestRules.EARLY_MINUTES })

    /** Whether [result] dropped by [SYSTOLIC_DROP] / [DIASTOLIC_DROP] mmHg at any point, early or late, the low systolic aside. [NIH-POTS-2021] */
    fun droppedBy2010(result: StandTestResult): Boolean =
        listOf(result.systolicDropEarly, result.systolicDropLate).any { (it ?: 0) >= SYSTOLIC_DROP } ||
            listOf(result.diastolicDropEarly, result.diastolicDropLate).any { (it ?: 0) >= DIASTOLIC_DROP }

    /** Whether [result] has a pressure at all to drop: lying and standing, early or late. */
    fun hasPressure(result: StandTestResult): Boolean = listOf(
        result.systolicDropEarly,
        result.diastolicDropEarly,
        result.systolicDropLate,
        result.diastolicDropLate,
    ).any { it != null }
}

/** The week evaluation (SPEC.md §17), pure logic: each figure from what was recorded in the week, none without data. */
object WeekEvaluation {

    /** The figures of [week] from [records]. */
    fun of(records: TrackingRecords, week: Week): WeekFigures = of(records, week.monday, week.sunday)

    /** The same figures over the days [from] to [to], as the doctor's summary has them for its weeks. */
    fun of(records: TrackingRecords, from: LocalDate, to: LocalDate): WeekFigures {
        val span = from..to
        val mornings = records.mornings.filter { it.date in span }
        val evenings = records.evenings.filter { it.date in span }
        val pulses = mornings.mapNotNull { it.restingPulse }
        val tests = records.standTests.filter { records.dayOf(it.test.startedAt) in span && it.stood }
        val risen = tests.mapNotNull { it.result.maxRise }
        val pressured = tests.filter { WeekRules.hasPressure(it.result) }
        val events = records.events.filter { records.dayOf(it.at) in span }
        val exertions = records.exertions.filter { it.date in span }
        val episodes = records.episodes.filter { it.startDate in span }
        val gaps = episodes.flatMap { episode ->
            records.triggers.filter { it.episodeId == episode.id }
                .mapNotNull { trigger -> records.exertions.firstOrNull { it.id == trigger.exertionId } }
                .map { ExertionRules.hoursAfter(it, episode).hours }
        }
        val levels = records.symptomValues.filter { it.date in span }
        val fluids = evenings.mapNotNull { it.fluidDecilitres }
        val qualities = mornings.mapNotNull { it.sleepQuality }
        val withIntakes = evenings.filter { evening -> records.medications.any { it.isActiveOn(evening.date) } }
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val steps = days.mapNotNull { records.garmin.steps[it] }
        val above = days.mapNotNull { records.garmin.minutesAbove[it] }
        val entries = mornings.map { it.backfilled } + evenings.map { it.backfilled }
        return WeekFigures(
            from = from,
            to = to,
            pulseMean = Figure.mean(pulses),
            pulseMin = pulses.minOrNull()?.let { Figure(it.toDouble(), pulses.size) } ?: Figure.NONE,
            pulseMax = pulses.maxOrNull()?.let { Figure(it.toDouble(), pulses.size) } ?: Figure.NONE,
            pulseHighDays = Figure.count(pulses.count { it > WeekRules.PULSE_HIGH }, pulses.size),
            standTests = Figure.count(tests.size, tests.size),
            standMaxRise = risen.maxOrNull()?.let { Figure(it.toDouble(), risen.size) } ?: Figure.NONE,
            standRiseTwice = Figure.count(tests.count { it.result.risesAtLeast30.size >= WeekRules.RISES_NEEDED }, risen.size),
            standDropEarly = Figure.count(pressured.count { WeekRules.droppedEarly(it.result) }, pressured.size),
            standDropLate = Figure.count(pressured.count { WeekRules.droppedLate(it.result) }, pressured.size),
            events = Figure.count(events.size, events.size),
            eventsBreathless = events.mapNotNull { it.breathlessness }.let { values -> Figure.count(values.count { it >= WeekRules.LEVEL_HIGH }, values.size) },
            eventsSkin = Figure.count(events.count { it.skinAreas.isNotEmpty() }, events.size),
            skinFadeMinutes = Figure.mean(events.mapNotNull { it.skinFadeMinutes }),
            // Without an evening entry nothing tells whether there were none or none were recorded.
            exertions = Figure.count(exertions.size, maxOf(evenings.size, exertions.size)),
            episodes = Figure.count(episodes.size, maxOf(evenings.size, episodes.size)),
            episodeGapHours = Figure.mean(gaps),
            symptoms = records.symptoms
                .filter { symptom -> !symptom.hidden || levels.any { it.symptomId == symptom.id } }
                .map { symptom -> SymptomFigure(symptom, Figure.mean(levels.filter { it.symptomId == symptom.id }.map { it.level })) },
            fluidLowDays = Figure.count(fluids.count { it < WeekRules.FLUID_LOW_DECILITRES }, fluids.size),
            poorNights = Figure.count(qualities.count { it >= WeekRules.LEVEL_HIGH }, qualities.size),
            deviations = Figure.count(withIntakes.count { evening -> records.deviations.any { it.date == evening.date } }, withIntakes.size),
            stepsPerDay = Figure.mean(steps),
            minutesAbovePerDay = Figure.mean(above),
            backfilledShare = if (entries.isEmpty()) Figure.NONE else Figure(entries.count { it }.toDouble() / entries.size, entries.size),
        )
    }
}
