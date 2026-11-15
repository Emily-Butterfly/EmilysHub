package de.emilyshub.tracking

import java.time.Duration
import java.time.Instant

/**
 * How far a Garmin value can be trusted (SPEC.md §17, "Stufen"): what each tier may do in a hint, the evaluation of
 * #131 deciding by it.
 */
enum class Tier {
    /** Measured and well checked: can set off a hint, as one's own measurements. */
    A,

    /** Derived or partly checked: backs up a hint set off otherwise, sets off none. */
    B,

    /** Garmin's own reckoning or poorly checked: only in the history and the PDF, marked "Garmin-Schätzung". */
    C,
}

/** The [tier] of a kind of value, and the [sources] the choice rests on. */
data class TierRule(val tier: Tier, val sources: List<Source>)

/**
 * Which tier a Garmin value has (SPEC.md §17, "Stufen"), in one place with the sources of each choice; pure logic.
 * A heart rate counts as A at rest, which is no steps counted in the [REST_MARGIN] before and after it, as far as the
 * steps are known there, or from a chest strap by the device type Health Connect gives; otherwise, from the wrist or a
 * device unknown, as B: also where no steps came in yet for its time, as they may only be late. Steps are A; Garmin's
 * resting heart rate and the length of sleep B; the stages of sleep C.
 */
object GarminTiers {
    /** How long before and after a heart rate no steps may be counted for it to be at rest. */
    val REST_MARGIN: Duration = Duration.ofMinutes(1)

    /** The rule for heart rates at rest and from a chest strap. [GARMIN-HF]: accurate at rest and with a chest strap. */
    val HEART_RATE_AT_REST = TierRule(Tier.A, listOf(Source.GarminHeartRate))

    /** The rule for heart rates in motion from the wrist or unknown. [GARMIN-HF]: wrist sensors fall short in motion. */
    val HEART_RATE_IN_MOTION = TierRule(Tier.B, listOf(Source.GarminHeartRate))

    /**
     * The rule for heart rates during a Stehtest, the curve beside its own points: at rest by SPEC.md §17 ("In Ruhe
     * heißt … oder ein Stehtest"). [GARMIN-HF]: accurate at rest.
     */
    val STAND_TEST = HEART_RATE_AT_REST

    /** The rule of each kind; for a heart rate the cautious one, which [heartRate] lifts at rest or from a chest strap. */
    fun ruleOf(kind: SampleKind): TierRule = when (kind) {
        SampleKind.HeartRate -> HEART_RATE_IN_MOTION
        // [GARMIN-SCHRITTE]: steps are counted with acceptable errors.
        SampleKind.Steps -> TierRule(Tier.A, listOf(Source.GarminSteps))
        // [GARMIN-HRV]: Garmin's resting heart rate is its own reckoning over the day; Dial 2025 left Garmin out of its
        // validation for that, no study checks it.
        SampleKind.RestingHeartRate -> TierRule(Tier.B, listOf(Source.GarminHrv))
        // [GARMIN-SCHLAF]: the length of sleep is moderately checked against polysomnography.
        SampleKind.Sleep -> TierRule(Tier.B, listOf(Source.GarminSleep))
        // [GARMIN-SCHLAF]: the stages of sleep agree poorly with polysomnography.
        SampleKind.SleepStage -> TierRule(Tier.C, listOf(Source.GarminSleep))
    }

    /**
     * The tier of [sample], heart rates by [steps], the steps counted around them, and [stepsKnownUntil], how far steps
     * came in at all.
     */
    fun tierOf(sample: GarminSample, steps: List<GarminSample>, stepsKnownUntil: Instant?): Tier =
        if (sample.kind == SampleKind.HeartRate) heartRate(sample, steps, stepsKnownUntil).tier else ruleOf(sample.kind).tier

    /** The rule for heart rate [sample]: from a chest strap, or at rest with its steps known, A; otherwise B. */
    fun heartRate(sample: GarminSample, steps: List<GarminSample>, stepsKnownUntil: Instant?): TierRule {
        val stepsKnown = stepsKnownUntil != null && !stepsKnownUntil.isBefore(sample.start.plus(REST_MARGIN))
        val atRest = stepsKnown && atRest(sample.start, steps)
        return if (sample.device == DeviceKind.ChestStrap || atRest) HEART_RATE_AT_REST else HEART_RATE_IN_MOTION
    }

    /**
     * Whether no steps of [steps] were counted within [REST_MARGIN] before or after [at]: a span of steps that only
     * ends as the minute before begins, or begins as the minute after ends, lies outside.
     */
    fun atRest(at: Instant, steps: List<GarminSample>): Boolean {
        val from = at.minus(REST_MARGIN)
        val to = at.plus(REST_MARGIN)
        return steps.none { it.kind == SampleKind.Steps && it.value > 0 && it.start.isBefore(to) && it.end.isAfter(from) }
    }
}
