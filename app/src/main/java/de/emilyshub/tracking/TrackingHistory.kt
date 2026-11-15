package de.emilyshub.tracking

import java.time.LocalDate

/** What a line of the history shows. */
sealed interface SeriesKind {
    /** A symptom of the evening, 0–3. */
    data class OfSymptom(val symptom: Symptom) : SeriesKind

    /** The morning's own resting pulse. */
    data object MorningPulse : SeriesKind

    /** Garmin's steps per day, tier A. */
    data object Steps : SeriesKind

    /** Garmin's resting heart rate, tier B, beside the morning's own. */
    data object GarminRestingPulse : SeriesKind

    /** The length of the night's sleep by Garmin, tier B. */
    data object GarminSleep : SeriesKind

    /** The deep sleep of the night by Garmin's reckoning, tier C: "Garmin-Schätzung". */
    data object GarminDeepSleep : SeriesKind
}

/**
 * A line of the history: of [kind], a value for each day from the first to the last of the range, none where there is
 * none, which leaves a gap; [tier] for Garmin's values, C to be marked "Garmin-Schätzung"; [scale] its fixed range, or
 * none to scale by the values.
 */
data class Series(val kind: SeriesKind, val tier: Tier?, val points: List<Pair<LocalDate, Double?>>, val scale: IntRange? = null) {
    /** Whether it has any value at all. */
    val hasValues: Boolean get() = points.any { it.second != null }

    /** Whether it is to be marked "Garmin-Schätzung". */
    val estimate: Boolean get() = tier == Tier.C
}

/** What a medication's mark in the history is. */
enum class MarkerKind { Start, DoseChange, End }

/** A change of medication [name] on [date], drawn as an upright line: its start, a dose changed [from] → [to], its end. */
data class Marker(val date: LocalDate, val name: String, val kind: MarkerKind, val from: String = "", val to: String = "")

/** An episode of [kind] from [from] to [to], cut to the range, drawn as a band; [running] if it has no end yet. */
data class Band(val from: LocalDate, val to: LocalDate, val kind: EpisodeKind, val running: Boolean)

/** How far the history reaches: 4 or 12 weeks (SPEC.md §17). */
enum class HistoryRange(val weeks: Int) { Four(4), Twelve(12) }

/** The history over a range, [from] to [to]: its [series], the medications' [markers] and the episodes' [bands]. */
data class History(val from: LocalDate, val to: LocalDate, val series: List<Series>, val markers: List<Marker>, val bands: List<Band>)

/** The history (SPEC.md §17, "Verlauf"), pure logic. */
object TrackingHistory {

    /** The first day of [range] ending [today]: the Monday [HistoryRange.weeks] weeks back, counting this one. */
    fun firstDay(range: HistoryRange, today: LocalDate): LocalDate = Week.of(today).monday.minusWeeks(range.weeks - 1L)

    /**
     * The history of [records] over [range] up to [today]: a line for each symptom shown or with values in the range,
     * for the morning's resting pulse, and for each of Garmin's values that has any in the range; the starts, dose
     * changes and ends of the medications as marks; the episodes as bands.
     */
    fun of(records: TrackingRecords, range: HistoryRange, today: LocalDate): History {
        val from = firstDay(range, today)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val inRange = { day: LocalDate -> !day.isBefore(from) && !day.isAfter(today) }
        fun <T : Number> line(kind: SeriesKind, tier: Tier?, byDay: Map<LocalDate, T>, scale: IntRange? = null) =
            Series(kind, tier, days.map { it to byDay[it]?.toDouble() }, scale)

        val symptoms = records.symptoms.mapNotNull { symptom ->
            val byDay = records.symptomValues.filter { it.symptomId == symptom.id }.associate { it.date to it.level }
            line(SeriesKind.OfSymptom(symptom), null, byDay, EntryLimits.LEVEL).takeIf { !symptom.hidden || it.hasValues }
        }
        val pulse = line(SeriesKind.MorningPulse, null, records.mornings.mapNotNull { m -> m.restingPulse?.let { m.date to it } }.toMap())
        val garmin = listOf(
            line(SeriesKind.Steps, GarminTiers.ruleOf(SampleKind.Steps).tier, records.garmin.steps),
            line(SeriesKind.GarminRestingPulse, GarminTiers.ruleOf(SampleKind.RestingHeartRate).tier, records.garmin.restingHeartRate),
            line(SeriesKind.GarminSleep, GarminTiers.ruleOf(SampleKind.Sleep).tier, records.garmin.sleepMinutes),
            line(SeriesKind.GarminDeepSleep, GarminTiers.ruleOf(SampleKind.SleepStage).tier, records.garmin.deepSleepMinutes),
        ).filter { it.hasValues }

        val names = records.medications.associate { it.id to it.name }
        val markers = (
            records.medications.flatMap { medication ->
                listOfNotNull(
                    Marker(medication.start, medication.name, MarkerKind.Start),
                    medication.end?.let { Marker(it, medication.name, MarkerKind.End) },
                )
            } + records.doseChanges.mapNotNull { change ->
                names[change.medicationId]?.let { Marker(change.date, it, MarkerKind.DoseChange, change.from, change.to) }
            }
            ).filter { inRange(it.date) }.sortedBy { it.date }

        val bands = records.episodes.mapNotNull { episode ->
            val end = episode.endDate ?: today
            if (end.isBefore(from) || episode.startDate.isAfter(today)) return@mapNotNull null
            Band(maxOf(episode.startDate, from), minOf(end, today), episode.kind, episode.running)
        }.sortedBy { it.from }

        return History(from, today, symptoms + pulse + garmin, markers, bands)
    }
}
