package de.emilyshub.tracking

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** What came before a Stehtest: coffees, minutes since the last meal, what was drunk today in tenths of a litre. */
data class StandBefore(val caffeine: Int? = null, val mealMinutesBefore: Int? = null, val fluidDecilitres: Int? = null)

/** A Stehtest as it runs: its [points] so far and the minute measured next, none once all are. */
data class StandRun(val test: StandTest, val points: List<StandPoint>) {
    val nextMinute: Int? get() = test.variant.nextMinute(points.map { it.minute }.toSet())
}

/** A Stehtest with its evaluation, for the list of all of them. */
data class StandTestSummary(val test: StandTest, val result: StandTestResult)

/**
 * A Stehtest on its result page: the [test] and its [points], the [intakes] of today before it (each medication with the
 * minutes since), Garmin's heart rate for its time as the [curve], tier A ([GarminTiers.STAND_TEST]), and the [result].
 */
data class StandTestReport(
    val test: StandTest,
    val points: List<StandPoint>,
    val intakes: List<Pair<Medication, Int>>,
    val curve: List<Beat>,
    val result: StandTestResult,
)

/**
 * The Stehtests of Tracking (SPEC.md §17, "Stehtest"), stored as they go, so that whatever ends them, what was measured
 * by then is kept: begun only with the whole checklist ticked, stood up after [StandTestRules.LYING_MINUTES] lying,
 * each point as it is measured, finished with the last, or aborted with the minute and a reason. Read and written only
 * while Tracking is open.
 */
class TrackingStandTests(private val tracking: TrackingStore, private val clock: Clock) {

    /** The moment it is, for the timers. */
    fun now(): Instant = clock.instant()

    /** Where the moments are shown. */
    fun zone(): ZoneId = clock.zone

    /**
     * Begins a Stehtest of [variant] lying down now, with what came [before] and the minutes since each of today's
     * intakes entered: its id, or none without all of [ticked], or once locked.
     */
    suspend fun begin(variant: StandVariant, ticked: Set<ChecklistItem>, before: StandBefore): Long? {
        if (!StandChecklist.complete(ticked)) return null
        return tracking.changing { database ->
            val now = ZonedDateTime.now(clock)
            val id = database.standTests().insert(
                StandTest(
                    variant = variant,
                    startedAt = now.toInstant(),
                    caffeine = before.caffeine?.takeIf { it in EntryLimits.COUNT },
                    mealMinutesBefore = before.mealMinutesBefore?.takeIf { it in MEAL_MINUTES },
                    fluidDecilitres = before.fluidDecilitres?.takeIf { it in EntryLimits.FLUID_DECILITRES },
                ),
            )
            database.standTests().insertIntakes(TodaysIntakes.before(database, now).map { (medication, minutes) -> StandIntake(id, medication, minutes) })
            id
        }
    }

    /** Stehtest [id] as it runs; none if gone, or once locked. */
    suspend fun run(id: Long): StandRun? = tracking.withDatabase { database ->
        database.standTests().get(id)?.let { StandRun(it, database.standTests().points(id)) }
    }

    /**
     * Stands up in Stehtest [id] now, with the lying values, each left out out of its range: false if it does not run,
     * stood up already, or has not lain for the least of [StandTestRules.LYING_MINUTES].
     */
    suspend fun standUp(id: Long, pulse: Int?, systolic: Int?, diastolic: Int?): Boolean = tracking.changing { database ->
        val test = database.standTests().get(id)?.takeIf { it.running && it.standingAt == null } ?: return@changing false
        val now = clock.instant()
        if (Duration.between(test.startedAt, now) < Duration.ofMinutes(StandTestRules.LYING_MINUTES.first.toLong())) return@changing false
        database.standTests().update(
            test.copy(
                standingAt = now,
                lyingPulse = pulse.within(EntryLimits.PULSE),
                lyingSystolic = systolic.within(EntryLimits.SYSTOLIC),
                lyingDiastolic = diastolic.within(EntryLimits.DIASTOLIC),
            ),
        )
        true
    } ?: false

    /**
     * Keeps [point] of its test, values out of their range left out, one with nothing in it as skipped; the last point
     * finishes the test. False if the test does not run, has not stood up, or has no point at its minute.
     */
    suspend fun record(point: StandPoint): Boolean = tracking.changing { database -> writePoint(database, point) } ?: false

    /** Writes [point] into [database], as [record] does. */
    suspend fun writePoint(database: TrackingDatabase, point: StandPoint): Boolean {
        val dao = database.standTests()
        val test = dao.get(point.testId)?.takeIf { it.running && it.standingAt != null && point.minute in it.variant.minutes } ?: return false
        dao.putPoint(point.withinLimits())
        if (test.variant.nextMinute(dao.points(test.id).map { it.minute }.toSet()) == null) dao.update(test.copy(finished = true))
        return true
    }

    /** Aborts Stehtest [id], as [writeAbort] does. */
    suspend fun abort(id: Long, cause: AbortCause, reason: String, typed: StandPoint? = null): Boolean =
        tracking.changing { database -> writeAbort(database, id, cause, reason, typed) } ?: false

    /**
     * Aborts Stehtest [id] in [database] now, for [cause] and [reason], at the whole minutes since standing up, none if
     * still lying; what was measured stays, and the point [typed] but not saved yet, if any, is kept first. False if it
     * no longer runs.
     */
    suspend fun writeAbort(database: TrackingDatabase, id: Long, cause: AbortCause, reason: String, typed: StandPoint? = null): Boolean {
        typed?.takeIf { it.testId == id }?.let { writePoint(database, it) }
        val test = database.standTests().get(id)?.takeIf { it.running } ?: return false
        database.standTests().update(
            test.copy(
                abortCause = cause,
                abortedMinute = test.standingAt?.let { Duration.between(it, clock.instant()).toMinutes().toInt().coerceAtLeast(0) },
                abortReason = reason.trim().take(REASON_MAX),
            ),
        )
        return true
    }

    /** All Stehtests with their evaluation, the latest first; none once locked. */
    suspend fun list(): List<StandTestSummary>? = tracking.withDatabase { database ->
        database.standTests().all().map { test ->
            StandTestSummary(test, StandTestEvaluation.evaluate(test, database.standTests().points(test.id), curveOf(database, test)))
        }
    }

    /** Stehtest [id] for its result page; none if gone, or once locked. */
    suspend fun report(id: Long): StandTestReport? = tracking.withDatabase { database ->
        val test = database.standTests().get(id) ?: return@withDatabase null
        val points = database.standTests().points(id)
        val medications = database.medications().all().associateBy { it.id }
        val intakes = database.standTests().intakes(id).mapNotNull { intake -> medications[intake.medicationId]?.let { it to intake.minutesBefore } }
        val curve = curveOf(database, test)
        StandTestReport(test, points, intakes.sortedBy { it.second }, curve, StandTestEvaluation.evaluate(test, points, curve))
    }

    /** Garmin's heart rates from lying down up to a minute after the last point, or a minute after the abort. */
    private suspend fun curveOf(database: TrackingDatabase, test: StandTest): List<Beat> {
        val standingMinutes = when {
            test.abortCause == null -> test.variant.minutes.last()
            else -> maxOf(test.abortedMinute ?: 0, database.standTests().points(test.id).maxOfOrNull { it.minute } ?: 0)
        }
        val last = test.standingAt?.plus(Duration.ofMinutes(standingMinutes + 1L))
            ?: test.startedAt.plus(Duration.ofMinutes(StandTestRules.LYING_MINUTES.last.toLong()))
        return database.garmin()
            .overlapping(SampleKind.HeartRate, test.startedAt.toEpochMilli() - 1, last.toEpochMilli())
            .map { Beat(it.start, it.value.toInt()) }
    }

    private fun StandPoint.withinLimits() = copy(
        pulse = pulse.within(EntryLimits.PULSE),
        systolic = systolic.within(EntryLimits.SYSTOLIC),
        diastolic = diastolic.within(EntryLimits.DIASTOLIC),
        dizziness = dizziness.within(EntryLimits.LEVEL),
        palpitations = palpitations.within(EntryLimits.LEVEL),
        breathlessness = breathlessness.within(EntryLimits.LEVEL),
    )

    private fun Int?.within(range: IntRange): Int? = this?.takeIf { it in range }

    companion object {
        /** The minutes since the last meal, up to two days. */
        val MEAL_MINUTES = 0..48 * 60

        /** The longest a reason to abort can be. */
        const val REASON_MAX = 500
    }
}
