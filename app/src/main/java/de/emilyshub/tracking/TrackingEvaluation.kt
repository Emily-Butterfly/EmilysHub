package de.emilyshub.tracking

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The week evaluation, the history, the hints and the doctor's summary of Tracking (SPEC.md §17): what was recorded read
 * at once while it is open ([TrackingRecords]), then evaluated as pure logic ([WeekEvaluation], [TrackingHistory],
 * [Hints], [DoctorSummary]); which hints were marked done is kept in its settings, with the evidence they rested on then,
 * and so are one's own questions for the doctor.
 */
class TrackingEvaluation(
    private val tracking: TrackingStore,
    private val symptoms: TrackingSymptoms,
    private val standTests: TrackingStandTests,
    private val exertions: TrackingExertions,
    private val clock: Clock,
) {
    /** The day it is, by the calendar. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** Where the moments are shown. */
    fun zone(): ZoneId = clock.zone

    /** The figures of [week]; none once locked. */
    suspend fun week(week: Week): WeekFigures? = tracking.withDatabase { database ->
        WeekEvaluation.of(records(database, week.monday, week.sunday), week)
    }

    /** The history over [range] up to today; none once locked. */
    suspend fun history(range: HistoryRange): History? = tracking.withDatabase { database ->
        val today = today()
        TrackingHistory.of(records(database, TrackingHistory.firstDay(range, today), today), range, today)
    }

    /** The hints of today, those marked done left out while their evidence stays as it was; none once locked. */
    suspend fun hints(): HintList? = tracking.withDatabase { database -> hintsOf(database, doneOf(database)) }

    /** The hints marked done, with the evidence they rested on then. */
    private suspend fun doneOf(database: TrackingDatabase): Map<HintRule, Set<String>> = HintRule.entries.mapNotNull { rule ->
        database.settings().get(doneKey(rule))?.let { rule to it.split(EVIDENCE_SEPARATOR).filter(String::isNotEmpty).toSet() }
    }.toMap()

    /**
     * How many weeks are tracked ([SummaryWeeks.tracked]): from the first day with an entry, a Stehtest, an event, an
     * exertion or an episode; none once locked.
     */
    suspend fun weeksTracked(): Int? = tracking.withDatabase { database ->
        val zone = clock.zone
        val firsts = listOfNotNull(
            database.entries().firstMorning(),
            database.entries().firstEvening(),
            database.standTests().first()?.atZone(zone)?.toLocalDate(),
            database.events().first()?.atZone(zone)?.toLocalDate(),
            database.exertions().first(),
            database.episodes().first(),
        )
        SummaryWeeks.tracked(firsts.minOrNull(), today())
    }

    /**
     * The doctor's summary of the [weeks] ending today, with today's hints as the start page shows them and, if
     * [withPhotos], the photos of its events; none once locked.
     */
    suspend fun summary(weeks: Int, withPhotos: Boolean): DoctorSummary? = tracking.withDatabase { database ->
        val (from, to) = SummaryWeeks.days(weeks, today())
        val records = records(database, from, to)
        val hints = hintsOf(database, doneOf(database)).shown
        val summary = DoctorSummary.of(records, from, to, hints)
        if (!withPhotos) return@withDatabase summary
        val photos = database.photos().ofEvents(summary.events.map { it.id }).mapNotNull { photo ->
            database.photos().jpeg(photo.id)?.let { SummaryPhoto(photo.eventId, photo.takenAt, it) }
        }
        summary.copy(photos = photos)
    }

    /** One's own questions for the doctor, as typed (Markdown), "" before any; none once locked. */
    suspend fun questions(): String? = tracking.withDatabase { it.settings().get(QUESTIONS).orEmpty() }

    /** Keeps [text] as one's own questions for the doctor; nothing once locked. */
    suspend fun saveQuestions(text: String) {
        tracking.changing { writeQuestions(it, text) }
    }

    /** Keeps [text] as one's own questions for the doctor, in [database]. */
    suspend fun writeQuestions(database: TrackingDatabase, text: String) {
        database.settings().put(TrackingSetting(QUESTIONS, text))
    }

    /** The hint of [rule] as it stands, done or not, for its page; none if it has none now, or once locked. */
    suspend fun hint(rule: HintRule): Hint? = tracking.withDatabase { database -> hintsOf(database).shown.firstOrNull { it.rule == rule } }

    /** Marks the hint of [rule] done, "erledigt": hidden until something is added to what it rests on. */
    suspend fun markDone(rule: HintRule) {
        tracking.changing { database ->
            val hint = hintsOf(database).shown.firstOrNull { it.rule == rule } ?: return@changing
            database.settings().put(TrackingSetting(doneKey(rule), hint.evidence.sorted().joinToString(EVIDENCE_SEPARATOR)))
        }
    }

    /** The hints of today from [database], over all the days their rules look at, with those [done]. */
    private suspend fun hintsOf(database: TrackingDatabase, done: Map<HintRule, Set<String>> = emptyMap()): HintList {
        val today = today()
        val from = today.minusDays(HintRules.COUNT_WINDOW + HintRules.MEDICATION_BEFORE_DAYS)
        return Hints.evaluate(records(database, from, today), today, done)
    }

    /** The setting that keeps the evidence of the hint of [rule] as it was marked done. */
    private fun doneKey(rule: HintRule) = "hint.done.${rule.name}"

    /**
     * All that was recorded from [from] to [to] in [database]: the Stehtests and events by the day they began on, the
     * episodes overlapping the days, with all medications and their dose changes, and the exertions from
     * [ExertionRules.TRIGGER_DAYS] before, so that an episode has its triggers.
     */
    internal suspend fun records(database: TrackingDatabase, from: LocalDate, to: LocalDate): TrackingRecords {
        val zone = clock.zone
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        val entryDao = database.entries()
        val standDao = database.standTests()
        val stand = standDao.all()
            .filter { !it.startedAt.isBefore(start) && it.startedAt.isBefore(end) }
            .map { test ->
                val points = standDao.points(test.id)
                StandRecord(test, points, StandTestEvaluation.evaluate(test, points, standTests.curveOf(database, test)))
            }
        val episodes = database.episodes().all().filter { !it.startDate.isAfter(to) && (it.endDate ?: to) >= from }
        // Each day's threshold is the one that held on it.
        val thresholds = exertions.thresholdsBetween(database, from, to)
        // The night before the first day ends on it.
        val samples = SampleKind.entries.flatMap { kind ->
            database.garmin().overlapping(kind, start.minusSeconds(NIGHT_SECONDS).toEpochMilli(), end.toEpochMilli())
        }
        return TrackingRecords(
            zone = zone,
            mornings = entryDao.morningsBetween(from, to),
            evenings = entryDao.eveningsBetween(from, to),
            symptoms = symptoms.listed(database),
            symptomValues = entryDao.valuesBetween(from, to),
            deviations = entryDao.deviationsBetween(from, to),
            medications = database.medications().all(),
            doseChanges = database.medications().allDoseChanges(),
            standTests = stand,
            events = database.events().all().filter { !it.at.isBefore(start) && it.at.isBefore(end) },
            exertions = database.exertions().between(from.minusDays(ExertionRules.TRIGGER_DAYS), to),
            followUps = entryDao.followUpsBetween(from, to.plusDays(ExertionRules.FOLLOW_UP_DAYS)),
            episodes = episodes,
            triggers = database.episodes().allTriggers().filter { trigger -> episodes.any { it.id == trigger.episodeId } },
            garmin = GarminDays.of(samples, zone, thresholds),
        )
    }

    private companion object {
        /** The setting that keeps one's own questions for the doctor. */
        const val QUESTIONS = "summary.questions"

        /** Between the keys of the evidence kept for a hint done. */
        const val EVIDENCE_SEPARATOR = "\n"

        /** How long before the first day a night may begin. */
        const val NIGHT_SECONDS = 18 * 60 * 60L
    }
}
