package de.emilyshub.tracking

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The weeks the doctor's summary reaches back (SPEC.md §17, "Arzt-Zusammenfassung"): from 1 to as many as are tracked,
 * [DEFAULT] unless fewer are; they end today, each seven days long.
 */
object SummaryWeeks {
    /** The weeks shown unless chosen otherwise. */
    const val DEFAULT = 4

    /** How many weeks are tracked from [first], the first day with anything, to [today], begun ones counting; 0 without. */
    fun tracked(first: LocalDate?, today: LocalDate): Int {
        if (first == null) return 0
        if (first.isAfter(today)) return 1
        val days = ChronoUnit.DAYS.between(first, today) + 1
        return ((days + 6) / 7).toInt()
    }

    /** The weeks shown first, of [tracked] ones. */
    fun default(tracked: Int): Int = within(DEFAULT, tracked)

    /** [weeks] within 1 and [tracked]. */
    fun within(weeks: Int, tracked: Int): Int = weeks.coerceIn(1, maxOf(1, tracked))

    /** The first and last day of the [weeks] ending [today]. */
    fun days(weeks: Int, today: LocalDate): Pair<LocalDate, LocalDate> = today.minusDays(weeks * 7L - 1) to today
}

/** What Garmin values the summary has, each with the [tier] it always has. */
enum class GarminValue(val tier: Tier) {
    /** Steps a day. */
    Steps(Tier.A),

    /** Garmin's own resting pulse, the mean of each day's. */
    RestingHeartRate(Tier.B),

    /** The length of the night's sleep, in minutes. */
    Sleep(Tier.B),

    /** The deep sleep of the night, Garmin's own reckoning, in minutes: "Garmin-Schätzung". */
    DeepSleep(Tier.C),
}

/** A Garmin value of the summary: its [mean] a day over the weeks, and its [tier]. */
data class GarminFigure(val value: GarminValue, val mean: Figure) {
    val tier: Tier get() = value.tier
}

/** A suspected trigger of an episode: the [exertion], how [certainty] it was, and how many hours before the start. */
data class SummaryTrigger(val exertion: Exertion, val certainty: Certainty, val hoursAfter: Long)

/** An episode of the summary with its suspected triggers. */
data class SummaryEpisode(val episode: Episode, val triggers: List<SummaryTrigger>)

/** A medication of the summary with the changes of its dose, the first first: start, changes, end make its timeline. */
data class SummaryMedication(val medication: Medication, val changes: List<DoseChange>)

/** A photo of event [eventId], taken at [takenAt], for the PDF only when chosen. Not a data class: its bytes compare by identity. */
class SummaryPhoto(val eventId: Long, val takenAt: Instant, val jpeg: ByteArray)

/**
 * The doctor's summary (SPEC.md §17, "Arzt-Zusammenfassung") of the days [from] to [to], filled in from what was
 * recorded: the [strongest] symptoms by their mean, the [figures] of the week evaluation over all those days, the
 * [standTests], [events], [exertions] and [episodes] with their triggers, the [medications] taken then with their
 * timeline, Garmin's values with their tier, the current [hints] with their questions, and the [photos] of the events if
 * they are to go along. The questions of one's own are kept apart, as typed.
 */
data class DoctorSummary(
    val from: LocalDate,
    val to: LocalDate,
    val strongest: List<SymptomFigure>,
    val figures: WeekFigures,
    val standTests: List<StandRecord>,
    val events: List<TrackingEvent>,
    val exertions: List<Exertion>,
    val episodes: List<SummaryEpisode>,
    val medications: List<SummaryMedication>,
    val garmin: List<GarminFigure>,
    val hints: List<Hint>,
    val photos: List<SummaryPhoto>,
) {
    companion object {
        /** How many of the strongest symptoms the summary names. */
        const val STRONGEST = 5

        /** The summary of [from] to [to] from [records], with the current [hints] and the [photos] chosen to go along. */
        fun of(records: TrackingRecords, from: LocalDate, to: LocalDate, hints: List<Hint>, photos: List<SummaryPhoto> = emptyList()): DoctorSummary {
            val span = from..to
            val figures = WeekEvaluation.of(records, from, to)
            val exertions = records.exertions.associateBy { it.id }
            val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
            fun <N : Number> mean(byDay: Map<LocalDate, N>) = Figure.mean(days.mapNotNull { byDay[it] })
            val garmin = listOf(
                GarminFigure(GarminValue.Steps, mean(records.garmin.steps)),
                GarminFigure(GarminValue.RestingHeartRate, mean(records.garmin.restingHeartRate)),
                GarminFigure(GarminValue.Sleep, mean(records.garmin.sleepMinutes)),
                GarminFigure(GarminValue.DeepSleep, mean(records.garmin.deepSleepMinutes)),
            ).filter { it.mean.value != null }
            return DoctorSummary(
                from = from,
                to = to,
                strongest = figures.symptoms
                    .filter { (it.mean.value ?: 0.0) > 0.0 }
                    .sortedWith(compareByDescending<SymptomFigure> { it.mean.value }.thenBy { it.symptom.position })
                    .take(STRONGEST),
                figures = figures,
                standTests = records.standTests.filter { records.dayOf(it.test.startedAt) in span }.sortedBy { it.test.startedAt },
                events = records.events.filter { records.dayOf(it.at) in span }.sortedWith(compareBy({ it.at }, { it.id })),
                exertions = records.exertions.filter { it.date in span }
                    .sortedWith(compareBy<Exertion>({ it.date }, { it.time ?: ExertionRules.UNKNOWN_TIME }, { it.id })),
                episodes = records.episodes
                    .filter { !it.startDate.isAfter(to) && (it.endDate ?: to) >= from }
                    .sortedWith(compareBy({ it.startDate }, { it.id }))
                    .map { episode ->
                        SummaryEpisode(
                            episode,
                            records.triggers.filter { it.episodeId == episode.id }.mapNotNull { trigger ->
                                exertions[trigger.exertionId]?.let { SummaryTrigger(it, trigger.certainty, ExertionRules.hoursAfter(it, episode).hours) }
                            },
                        )
                    },
                medications = records.medications
                    .filter { !it.start.isAfter(to) && (it.end == null || !it.end.isBefore(from)) }
                    .sortedWith(compareBy({ it.start }, { it.name }))
                    .map { medication -> SummaryMedication(medication, records.doseChanges.filter { it.medicationId == medication.id }.sortedBy { it.date }) },
                garmin = garmin,
                hints = hints,
                photos = photos,
            )
        }
    }
}
