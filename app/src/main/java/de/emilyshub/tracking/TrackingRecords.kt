package de.emilyshub.tracking

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A Stehtest as the evaluation sees it: the [test], its own [points] and its [result] with Garmin's curve. */
data class StandRecord(val test: StandTest, val points: List<StandPoint>, val result: StandTestResult) {
    /** The day it was made on, by the wall clock of [zone]. */
    fun dayIn(zone: ZoneId): LocalDate = test.startedAt.atZone(zone).toLocalDate()

    /** Whether it got to standing: only then is there anything to evaluate. */
    val stood: Boolean get() = result.points.isNotEmpty()

    /** Whether dizziness, palpitations or breathlessness were felt standing, at any point. */
    val complaints: Boolean
        get() = points.any { (it.dizziness ?: 0) > 0 || (it.palpitations ?: 0) > 0 || (it.breathlessness ?: 0) > 0 }

    /** Whether any value came from Garmin's curve, tier A. */
    val filledFromGarmin: Boolean get() = result.lyingFilled || result.points.any { it.filled }
}

/**
 * Garmin's values by day, as the week evaluation, the history and the hints take them (SPEC.md §17), each day by the wall
 * clock, so that a day the clocks change on has its 23 or 25 hours: the [steps] (tier A), the minutes of heart rate above
 * the pacing threshold ([minutesAbove], which only ever suggest), Garmin's [restingHeartRate] (tier B, the mean of the
 * day's values), the length of the night's sleep by the day it ended on ([sleepMinutes], tier B) and its deep sleep
 * ([deepSleepMinutes], tier C, Garmin's own reckoning). A day without values has none, never 0.
 */
data class GarminDays(
    val steps: Map<LocalDate, Long> = emptyMap(),
    val minutesAbove: Map<LocalDate, Long> = emptyMap(),
    val restingHeartRate: Map<LocalDate, Double> = emptyMap(),
    val sleepMinutes: Map<LocalDate, Long> = emptyMap(),
    val deepSleepMinutes: Map<LocalDate, Long> = emptyMap(),
) {
    companion object {
        /** Health Connect's number of the stage of deep sleep (SleepSessionRecord.STAGE_TYPE_DEEP). */
        const val DEEP_SLEEP_STAGE = 5L

        private const val MILLIS_PER_MINUTE = 60_000L

        /**
         * The days of [samples] in [zone]: the steps by the day their span began on, the heart rates above
         * [thresholdBpm] as whole minutes, a day with heart rates but none above having 0; the sleep by the day it ended.
         */
        fun of(samples: List<GarminSample>, zone: ZoneId, thresholdBpm: Int?): GarminDays {
            val byKind = samples.groupBy { it.kind }
            fun day(at: Instant) = at.atZone(zone).toLocalDate()
            val heart = byKind[SampleKind.HeartRate].orEmpty()
            return GarminDays(
                steps = byKind[SampleKind.Steps].orEmpty().groupBy { day(it.start) }.mapValues { (_, spans) -> spans.sumOf { it.value } },
                minutesAbove = if (thresholdBpm == null) {
                    emptyMap()
                } else {
                    heart.groupBy { day(it.start) }.mapValues { (_, beats) ->
                        beats.filter { it.value > thresholdBpm }.map { it.startMillis / MILLIS_PER_MINUTE }.distinct().size.toLong()
                    }
                },
                restingHeartRate = byKind[SampleKind.RestingHeartRate].orEmpty().groupBy { day(it.start) }.mapValues { (_, values) -> values.map { it.value }.average() },
                sleepMinutes = byKind[SampleKind.Sleep].orEmpty().groupBy { day(it.end) }.mapValues { (_, nights) -> nights.sumOf { it.value } },
                deepSleepMinutes = byKind[SampleKind.SleepStage].orEmpty()
                    .filter { it.value == DEEP_SLEEP_STAGE }
                    .groupBy { day(it.end) }
                    .mapValues { (_, stages) -> stages.sumOf { Duration.between(it.start, it.end).toMinutes() } },
            )
        }
    }
}

/**
 * All of Tracking that the week evaluation, the history and the hints rest on (SPEC.md §17), read at once while it is
 * open and then evaluated as pure logic: the entries of the mornings and evenings with their symptoms, intakes otherwise
 * than planned and answers about exertions before; the medications with their dose changes; the Stehtests, events,
 * exertions and episodes with their triggers; Garmin's values by day. The moments of Stehtests and events count by the
 * wall clock of [zone].
 */
data class TrackingRecords(
    val zone: ZoneId,
    val mornings: List<MorningEntry> = emptyList(),
    val evenings: List<EveningEntry> = emptyList(),
    val symptoms: List<Symptom> = emptyList(),
    val symptomValues: List<SymptomValue> = emptyList(),
    val deviations: List<IntakeDeviation> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val doseChanges: List<DoseChange> = emptyList(),
    val standTests: List<StandRecord> = emptyList(),
    val events: List<TrackingEvent> = emptyList(),
    val exertions: List<Exertion> = emptyList(),
    val followUps: List<FollowUp> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val triggers: List<EpisodeTrigger> = emptyList(),
    val garmin: GarminDays = GarminDays(),
) {
    /** The day [at] falls on, by the wall clock. */
    fun dayOf(at: Instant): LocalDate = at.atZone(zone).toLocalDate()

    /** The symptom levels of each evening, by day and symptom. */
    val levelsByDay: Map<LocalDate, Map<Long, Int>> by lazy {
        symptomValues.groupBy { it.date }.mapValues { (_, values) -> values.associate { it.symptomId to it.level } }
    }
}
