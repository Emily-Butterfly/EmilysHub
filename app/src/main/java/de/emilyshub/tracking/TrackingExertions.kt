package de.emilyshub.tracking

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** A suggested exertion: [date]'s value of [source] above the mean of the days before ([check]). */
data class Suggestion(val date: LocalDate, val source: SuggestionSource, val check: SuggestionCheck)

/**
 * The pacing threshold as its menu page shows it: one's [own] value if set, the [threshold] in force, none without own
 * value or morning pulses, and whether the hint on pulse-lowering medication shows ([warnsOfPulseLowering]).
 */
data class ThresholdView(val own: Int?, val threshold: PacingThreshold?, val warnsOfPulseLowering: Boolean)

/**
 * The exertions of Tracking (SPEC.md §17, "Anstrengungen, PEM & Crash"): by hand, from the evening's load, which they
 * follow ([syncEvening]), or confirmed from a suggestion out of Garmin's steps or minutes above the pacing threshold.
 * Suggestions never set off a hint. Read and written only while Tracking is open.
 */
class TrackingExertions(private val tracking: TrackingStore, private val entries: TrackingEntries, private val clock: Clock) {

    /** All exertions, the latest first; none once locked. */
    suspend fun list(): List<Exertion>? = tracking.withDatabase { it.exertions().all() }

    /** Exertion [id]; none if gone, or once locked. */
    suspend fun get(id: Long): Exertion? = tracking.withDatabase { it.exertions().get(id) }

    /**
     * Saves [exertion] by hand, a new one if its id is 0, and if [triggerOf] is an episode, as one of its triggers,
     * "unklar": its id. None if its strength is off its scale, if it is one of an evening, which follows the evening
     * only, or once locked.
     */
    suspend fun save(exertion: Exertion, triggerOf: Long? = null): Long? = tracking.changing { database -> write(database, exertion, triggerOf) }

    /** Writes [exertion] into [database], as [save] does: for a page that saves as it goes away or Tracking locks. */
    suspend fun write(database: TrackingDatabase, exertion: Exertion, triggerOf: Long? = null): Long? {
        if (exertion.strength !in ExertionRules.STRENGTH) return null
        val cleaned = exertion.copy(durationMinutes = exertion.durationMinutes?.takeIf { it in EventLimits.MINUTES })
        val id = if (cleaned.id == 0L) {
            database.exertions().insert(cleaned.copy(origin = ExertionOrigin.Manual))
        } else {
            val stored = database.exertions().get(cleaned.id)?.takeIf { it.origin != ExertionOrigin.Evening } ?: return null
            database.exertions().update(cleaned.copy(origin = stored.origin))
            cleaned.id
        }
        if (triggerOf != null && database.episodes().get(triggerOf) != null) {
            database.episodes().putTriggers(listOf(EpisodeTrigger(triggerOf, id, Certainty.Unclear)))
        }
        return id
    }

    /** Deletes exertion [id], unless it is one of an evening, which goes only with the evening's load. */
    suspend fun delete(id: Long) {
        tracking.changing { database ->
            database.exertions().get(id)?.takeIf { it.origin != ExertionOrigin.Evening }?.let { database.exertions().delete(it) }
        }
    }

    /**
     * The suggestions not decided yet for the days an entry can be made for: from Garmin's steps, and from the minutes of
     * heart rate above the pacing threshold, each day above 150 percent of the mean of the 14 before ([ExertionRules]).
     * None once locked.
     */
    suspend fun suggestions(): List<Suggestion>? = tracking.withDatabase { database ->
        val from = entries.earliestDay()
        val to = entries.today()
        val decided = database.exertions().decisions(from, to).map { it.date to it.source }.toSet()
        val windowStart = from.minusDays(ExertionRules.MEAN_DAYS)
        val steps = stepsByDay(database, windowStart, to)
        val minutes = minutesAboveByDay(database, windowStart, to, thresholdsBetween(database, windowStart, to))
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.flatMap { day ->
            listOfNotNull(
                ExertionRules.exceeds(steps, day)?.let { Suggestion(day, SuggestionSource.Steps, it) },
                ExertionRules.exceeds(minutes, day)?.let { Suggestion(day, SuggestionSource.HeartRate, it) },
            )
        }.filter { (it.date to it.source) !in decided }.toList()
    }

    /** Confirms the suggestion of [source] for [date] as a physical exertion of [strength]: its id; none off the scale or locked. */
    suspend fun confirm(date: LocalDate, source: SuggestionSource, strength: Int): Long? = tracking.changing { database ->
        if (strength !in ExertionRules.STRENGTH) return@changing null
        database.exertions().decide(SuggestionDecision(date, source, confirmed = true))
        database.exertions().insert(Exertion(date = date, kind = ExertionKind.Physical, strength = strength, origin = source.origin))
    }

    /** Dismisses the suggestion of [source] for [date]: it does not come again. */
    suspend fun dismiss(date: LocalDate, source: SuggestionSource) {
        tracking.changing { it.exertions().decide(SuggestionDecision(date, source, confirmed = false)) }
    }

    /** The pacing threshold for its menu page; none once locked. */
    suspend fun threshold(): ThresholdView? = tracking.withDatabase { database -> thresholdOf(database) }

    /** Sets one's own pacing threshold to [bpm], or takes it back for the rule of thumb with none; one off the pulse's range is not kept. */
    suspend fun setOwnThreshold(bpm: Int?) {
        tracking.changing { database -> writeOwnThreshold(database, bpm) }
    }

    /** Writes one's own pacing threshold into [database], as [setOwnThreshold] does. */
    suspend fun writeOwnThreshold(database: TrackingDatabase, bpm: Int?) {
        database.exertions().putOwnThreshold(OwnThreshold(entries.today(), bpm?.takeIf { it in EntryLimits.PULSE }))
    }

    /** The pacing threshold as [threshold] gives it, from [database]. */
    private suspend fun thresholdOf(database: TrackingDatabase): ThresholdView {
        val today = entries.today()
        val owns = database.exertions().ownThresholds()
        val own = PacingThreshold.ownOn(today, owns)
        val lowersPulse = database.medications().all().any { it.lowersPulse && it.isActiveOn(today) }
        val threshold = PacingThreshold.on(today, owns, restingPulses(database, today, today))
        return ThresholdView(own, threshold, PacingThreshold.warnsOfPulseLowering(own, lowersPulse))
    }

    /**
     * The pacing threshold in beats per minute of each day from [from] to [to] that has one: the one that held on that
     * day, by one's own as set then and the mornings up to it, so that a change does not rewrite the days before. For
     * the evaluation too.
     */
    internal suspend fun thresholdsBetween(database: TrackingDatabase, from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        val owns = database.exertions().ownThresholds()
        val pulses = restingPulses(database, from, to)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .mapNotNull { day -> PacingThreshold.on(day, owns, pulses)?.let { day to it.bpm } }
            .toMap()
    }

    /** The morning resting pulses the thresholds from [from] to [to] are taken from, by day. */
    private suspend fun restingPulses(database: TrackingDatabase, from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        database.entries().morningsBetween(from.minusDays(PacingThreshold.MORNING_DAYS - 1), to)
            .mapNotNull { morning -> morning.restingPulse?.let { morning.date to it } }
            .toMap()

    /** The steps of each day from [from] to [to] that has any, each span by the day it began on. */
    private suspend fun stepsByDay(database: TrackingDatabase, from: LocalDate, to: LocalDate): Map<LocalDate, Long> =
        database.garmin().overlapping(SampleKind.Steps, startOf(from), startOf(to.plusDays(1)))
            .groupBy { dayOf(it.start) }
            .mapValues { (_, spans) -> spans.sumOf { it.value } }

    /**
     * The minutes of each day from [from] to [to] with a heart rate, tier A or B as they all are, above that day's
     * [thresholds]; a day with heart rates but none above has 0, one without heart rates or a threshold none.
     */
    private suspend fun minutesAboveByDay(database: TrackingDatabase, from: LocalDate, to: LocalDate, thresholds: Map<LocalDate, Int>): Map<LocalDate, Long> =
        GarminDays.of(database.garmin().overlapping(SampleKind.HeartRate, startOf(from) - 1, startOf(to.plusDays(1))), clock.zone, thresholds).minutesAbove

    private fun startOf(day: LocalDate): Long = day.atStartOfDay(clock.zone).toInstant().toEpochMilli()

    private fun dayOf(at: Instant): LocalDate = ZonedDateTime.ofInstant(at, clock.zone).toLocalDate()

    companion object {

        /**
         * Makes the exertions of [entry]'s evening follow it (SPEC.md §17): each load of 2 or more is one of its kind
         * and strength, changed in place so that an episode keeps it as a trigger, gone once the load is less; moved
         * with the evening from [replacing].
         */
        suspend fun syncEvening(database: TrackingDatabase, entry: EveningEntry, replacing: LocalDate?) {
            val dao = database.exertions()
            if (replacing != null && replacing != entry.date) {
                dao.ofEvening(replacing).forEach { dao.update(it.copy(date = entry.date)) }
            }
            val wanted = ExertionRules.fromEvening(entry)
            val existing = dao.ofEvening(entry.date)
            existing.forEach { exertion ->
                val strength = wanted[exertion.kind]
                if (strength == null) dao.delete(exertion) else if (strength != exertion.strength) dao.update(exertion.copy(strength = strength))
            }
            (wanted.keys - existing.map { it.kind }.toSet()).forEach { kind ->
                dao.insert(Exertion(date = entry.date, kind = kind, strength = wanted.getValue(kind), origin = ExertionOrigin.Evening))
            }
        }
    }
}
