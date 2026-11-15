package de.emilyshub.tracking

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Whether the day's morning and evening entries are made, for the start page (SPEC.md §17). */
data class DayStatus(val day: LocalDate, val morningDone: Boolean, val eveningDone: Boolean)

/** A morning [entry] to show, [stored] if it was saved before, else new and empty. */
data class MorningSheet(val entry: MorningEntry, val stored: Boolean)

/** The two entries of a day. */
enum class EntryKind { Morning, Evening }

/**
 * What the evening entry of [entry]'s day shows (SPEC.md §17, "Abends"): the [symptoms] to ask, those not hidden and
 * the hidden ones that have a value here, with their [levels] by symptom; all [medications], to ask those taken on the
 * day it is for ([takenOn]), which can change in the entry, with how their intakes went otherwise than planned
 * ([deviations]), none of them "wie geplant"; the exertions of strength 2–3 of the three days before to ask after
 * ([followUps]) with what was answered ([followUpAnswers]). [stored] if it was saved before.
 */
data class EveningSheet(
    val entry: EveningEntry,
    val levels: Map<Long, Int>,
    val symptoms: List<Symptom>,
    val medications: List<Medication>,
    val deviations: Map<Long, IntakeDeviation>,
    val stored: Boolean,
    val followUps: List<Exertion> = emptyList(),
    val followUpAnswers: Map<Long, FollowUpAnswer> = emptyMap(),
) {
    /** The medications taken on [day], to ask how their intakes went. */
    fun takenOn(day: LocalDate): List<Medication> = medications.filter { it.isActiveOn(day) }
}

/** The ranges a value of an entry must lie in to be saved; outside, it is not saved. */
object EntryLimits {
    /** The resting pulse, per minute, as the ticket gives it. */
    val PULSE = 30..220

    /** The blood pressure, in mmHg; a typing error lies outside, never a value one measures. */
    val SYSTOLIC = 50..300

    /** As [SYSTOLIC]. */
    val DIASTOLIC = 30..200

    /** The scale of symptoms, load and sleep's quality. */
    val LEVEL = 0..3

    /** A night's sleep, in minutes. */
    val SLEEP_MINUTES = 0..24 * 60

    /** What was drunk, in tenths of a litre. */
    val FLUID_DECILITRES = 0..150

    /** Meals, coffees and drinks. */
    val COUNT = 0..50
}

/**
 * The morning and evening entries of Tracking (SPEC.md §17, "Tageseinträge"), read and written only while it is open:
 * one of each per day, saved as they stand, also unfinished, what is left empty null, never 0. A new one is for the
 * day [EntryDay.suggested] says, which the entry can change, back [EntryDay.BACKFILL_DAYS] days from that day at most;
 * one first recorded later than the day after counts as made up, silently. "Recorded on" counts by the same day, so
 * that an evening entry made after midnight, before 04:00, is not made up.
 */
class TrackingEntries(private val tracking: TrackingStore, private val symptoms: TrackingSymptoms, private val clock: Clock) {

    /** The day it is, by the calendar. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The day a new entry is for: until 04:00 the day before. */
    fun suggestedDay(): LocalDate = EntryDay.suggested(ZonedDateTime.now(clock))

    /** The earliest day an entry can be made for: seven before the one a new entry is for. */
    fun earliestDay(): LocalDate = EntryDay.earliest(suggestedDay())

    /** Whether the entries of [day] are made; none once locked. */
    suspend fun status(day: LocalDate): DayStatus? = tracking.withDatabase { database ->
        DayStatus(day, database.entries().morning(day) != null, database.entries().evening(day) != null)
    }

    /** The days within reach that have an entry of [kind], for the date to change to only one without. */
    suspend fun takenDays(kind: EntryKind): Set<LocalDate>? = tracking.withDatabase { database ->
        when (kind) {
            EntryKind.Morning -> database.entries().morningDays(earliestDay(), today())
            EntryKind.Evening -> database.entries().eveningDays(earliestDay(), today())
        }.toSet()
    }

    /** The morning entry of [day] to show, a new and empty one if there is none yet; none once locked. */
    suspend fun morning(day: LocalDate): MorningSheet? = tracking.withDatabase { database ->
        val stored = database.entries().morning(day)
        MorningSheet(stored ?: MorningEntry(date = day, recordedOn = suggestedDay()), stored != null)
    }

    /** The evening entry of [day] to show, a new and empty one if there is none yet; none once locked. */
    suspend fun evening(day: LocalDate): EveningSheet? = tracking.withDatabase { database -> sheetOf(database, day) }

    /** The evening entry of [day] as [evening] shows it, from [database]. */
    internal suspend fun sheetOf(database: TrackingDatabase, day: LocalDate): EveningSheet {
        val stored = database.entries().evening(day)
        val levels = database.entries().values(day).associate { it.symptomId to it.level }
        return EveningSheet(
            entry = stored ?: EveningEntry(date = day, recordedOn = suggestedDay()),
            levels = levels,
            symptoms = symptoms.listed(database).filter { !it.hidden || it.id in levels },
            medications = database.medications().all(),
            deviations = database.entries().deviations(day).associateBy { it.medicationId },
            stored = stored != null,
            followUps = followUpsOf(database, day),
            followUpAnswers = database.entries().followUps(day).associate { it.exertionId to it.answer },
        )
    }

    /** Saves [entry], in place of the one of [replacing] if it moved there from that day; see [writeMorning]. */
    suspend fun saveMorning(entry: MorningEntry, replacing: LocalDate?): Boolean =
        tracking.changing { writeMorning(it, entry, replacing) } ?: false

    /**
     * Writes [entry] into [database], in place of the one of [replacing] if it moved there from that day, keeping the
     * day it was first recorded; values out of their [EntryLimits] are left out. False, writing nothing, if its day is
     * out of reach or has a morning entry already.
     */
    suspend fun writeMorning(database: TrackingDatabase, entry: MorningEntry, replacing: LocalDate?): Boolean {
        val entries = database.entries()
        if (!canMoveTo(entry.date, replacing) || (entry.date != replacing && entries.morning(entry.date) != null)) return false
        val recordedOn = replacing?.let { entries.morning(it)?.recordedOn } ?: suggestedDay()
        entries.putMorning(entry.withinLimits().copy(recordedOn = recordedOn), replacing)
        return true
    }

    /** The exertions the evening of [day] asks after, from [database]. */
    private suspend fun followUpsOf(database: TrackingDatabase, day: LocalDate): List<Exertion> =
        ExertionRules.followUps(day, database.exertions().between(day.minusDays(ExertionRules.FOLLOW_UP_DAYS), day))

    /** Saves the evening entry, as [writeEvening] does. */
    suspend fun saveEvening(
        entry: EveningEntry,
        levels: Map<Long, Int>,
        deviations: Collection<IntakeDeviation>,
        replacing: LocalDate?,
        followUps: Map<Long, FollowUpAnswer>? = null,
    ): Boolean = tracking.changing { writeEvening(it, entry, levels, deviations, replacing, followUps) } ?: false

    /**
     * Writes [entry] into [database] with the [levels] of its symptoms and how its intakes went otherwise than planned
     * ([deviations]), in place of the one of [replacing] if it moved there from that day, keeping the day it was first
     * recorded; values out of their [EntryLimits] and intakes of medications not taken that day are left out. The
     * answers about the exertions before ([followUps]) are those it asks after on its day; none given keeps those it had.
     * Its exertions follow its load ([TrackingExertions.syncEvening]). False, writing nothing, if its day is out of
     * reach or has an evening entry already.
     */
    suspend fun writeEvening(
        database: TrackingDatabase,
        entry: EveningEntry,
        levels: Map<Long, Int>,
        deviations: Collection<IntakeDeviation>,
        replacing: LocalDate?,
        followUps: Map<Long, FollowUpAnswer>? = null,
    ): Boolean {
        val entries = database.entries()
        val day = entry.date
        if (!canMoveTo(day, replacing) || (day != replacing && entries.evening(day) != null)) return false
        val recordedOn = replacing?.let { entries.evening(it)?.recordedOn } ?: suggestedDay()
        val known = database.symptoms().all().map { it.id }.toSet()
        val taken = database.medications().all().filter { it.isActiveOn(day) }.map { it.id }.toSet()
        val answers = followUps ?: replacing?.let { old -> entries.followUps(old).associate { it.exertionId to it.answer } }.orEmpty()
        val asked = followUpsOf(database, day).map { it.id }.toSet()
        entries.putEvening(
            entry = entry.withinLimits().copy(recordedOn = recordedOn),
            values = levels.filter { (id, level) -> id in known && level in EntryLimits.LEVEL }.map { (id, level) -> SymptomValue(day, id, level) },
            deviations = deviations.filter { it.medicationId in taken }.map {
                it.copy(date = day, time = it.time.takeIf { _ -> it.deviation == Deviation.OtherTime })
            },
            followUps = answers.filterKeys { it in asked }.map { (exertion, answer) -> FollowUp(day, exertion, answer) },
            replacing = replacing,
        )
        TrackingExertions.syncEvening(database, entry, replacing)
        return true
    }

    /** Whether an entry can be at [day], having been at [replacing]: within reach, or where it was. */
    private fun canMoveTo(day: LocalDate, replacing: LocalDate?) = day == replacing || (!day.isAfter(today()) && !day.isBefore(earliestDay()))

    private fun MorningEntry.withinLimits() = copy(
        restingPulse = restingPulse.within(EntryLimits.PULSE),
        systolic = systolic.within(EntryLimits.SYSTOLIC),
        diastolic = diastolic.within(EntryLimits.DIASTOLIC),
        sleepMinutes = sleepMinutes.within(EntryLimits.SLEEP_MINUTES),
        sleepQuality = sleepQuality.within(EntryLimits.LEVEL),
    )

    private fun EveningEntry.withinLimits() = copy(
        loadPhysical = loadPhysical.within(EntryLimits.LEVEL),
        loadCognitive = loadCognitive.within(EntryLimits.LEVEL),
        loadEmotional = loadEmotional.within(EntryLimits.LEVEL),
        loadSocial = loadSocial.within(EntryLimits.LEVEL),
        fluidDecilitres = fluidDecilitres.within(EntryLimits.FLUID_DECILITRES),
        meals = meals.within(EntryLimits.COUNT),
        caffeine = caffeine.within(EntryLimits.COUNT),
        alcohol = alcohol.within(EntryLimits.COUNT),
    )

    private fun Int?.within(range: IntRange): Int? = this?.takeIf { it in range }

    companion object {
        /** An intake of [medicationId] on [day] otherwise than planned: at [time], or forgotten without one. */
        fun deviation(day: LocalDate, medicationId: Long, time: LocalTime?): IntakeDeviation =
            IntakeDeviation(day, medicationId, if (time == null) Deviation.Forgotten else Deviation.OtherTime, time)
    }
}
