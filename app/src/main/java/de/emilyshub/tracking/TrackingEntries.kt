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
 * the hidden ones that have a value here, with their [levels] by symptom; the [medications] taken that day, with how
 * their intakes went otherwise than planned ([deviations]), none of them "wie geplant". [stored] if it was saved before.
 */
data class EveningSheet(
    val entry: EveningEntry,
    val levels: Map<Long, Int>,
    val symptoms: List<Symptom>,
    val medications: List<Medication>,
    val deviations: Map<Long, IntakeDeviation>,
    val stored: Boolean,
)

/** The ranges a value of an entry must lie in to be saved; outside, it is not saved. */
object EntryLimits {
    val PULSE = 30..220
    val SYSTOLIC = 50..300
    val DIASTOLIC = 30..200
    val LEVEL = 0..3
    val SLEEP_MINUTES = 0..24 * 60
    val FLUID_DECILITRES = 0..150
    val COUNT = 0..50
}

/**
 * The morning and evening entries of Tracking (SPEC.md §17, "Tageseinträge"), read and written only while it is open:
 * one of each per day, saved as they stand, also unfinished, what is left empty null, never 0. A new one is for the
 * day [EntryDay.suggested] says, which the entry can change, back [EntryDay.BACKFILL_DAYS] days at most; one first
 * recorded later than the day after counts as made up, silently.
 */
class TrackingEntries(private val tracking: TrackingStore, private val symptoms: TrackingSymptoms, private val clock: Clock) {

    /** The day it is, by the calendar. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The day a new entry is for: until 04:00 the day before. */
    fun suggestedDay(): LocalDate = EntryDay.suggested(ZonedDateTime.now(clock))

    /** The earliest day an entry can be made for. */
    fun earliestDay(): LocalDate = EntryDay.earliest(today())

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
        MorningSheet(stored ?: MorningEntry(date = day, recordedOn = today()), stored != null)
    }

    /** The evening entry of [day] to show, a new and empty one if there is none yet; none once locked. */
    suspend fun evening(day: LocalDate): EveningSheet? = tracking.withDatabase { database -> sheetOf(database, day) }

    /** The evening entry of [day] as [evening] shows it, from [database]. */
    internal suspend fun sheetOf(database: TrackingDatabase, day: LocalDate): EveningSheet {
        val stored = database.entries().evening(day)
        val levels = database.entries().values(day).associate { it.symptomId to it.level }
        return EveningSheet(
            entry = stored ?: EveningEntry(date = day, recordedOn = today()),
            levels = levels,
            symptoms = symptoms.listed(database).filter { !it.hidden || it.id in levels },
            medications = database.medications().all().filter { it.isActiveOn(day) },
            deviations = database.entries().deviations(day).associateBy { it.medicationId },
            stored = stored != null,
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
        val recordedOn = replacing?.let { entries.morning(it)?.recordedOn } ?: today()
        entries.putMorning(entry.withinLimits().copy(recordedOn = recordedOn), replacing)
        return true
    }

    /** Saves the evening entry, as [writeEvening] does. */
    suspend fun saveEvening(entry: EveningEntry, levels: Map<Long, Int>, deviations: Collection<IntakeDeviation>, replacing: LocalDate?): Boolean =
        tracking.changing { writeEvening(it, entry, levels, deviations, replacing) } ?: false

    /**
     * Writes [entry] into [database] with the [levels] of its symptoms and how its intakes went otherwise than planned
     * ([deviations]), in place of the one of [replacing] if it moved there from that day, keeping the day it was first
     * recorded; values out of their [EntryLimits] and intakes of medications not taken that day are left out. False,
     * writing nothing, if its day is out of reach or has an evening entry already.
     */
    suspend fun writeEvening(
        database: TrackingDatabase,
        entry: EveningEntry,
        levels: Map<Long, Int>,
        deviations: Collection<IntakeDeviation>,
        replacing: LocalDate?,
    ): Boolean {
        val entries = database.entries()
        val day = entry.date
        if (!canMoveTo(day, replacing) || (day != replacing && entries.evening(day) != null)) return false
        val recordedOn = replacing?.let { entries.evening(it)?.recordedOn } ?: today()
        val known = database.symptoms().all().map { it.id }.toSet()
        val taken = database.medications().all().filter { it.isActiveOn(day) }.map { it.id }.toSet()
        entries.putEvening(
            entry = entry.withinLimits().copy(recordedOn = recordedOn),
            values = levels.filter { (id, level) -> id in known && level in EntryLimits.LEVEL }.map { (id, level) -> SymptomValue(day, id, level) },
            deviations = deviations.filter { it.medicationId in taken }.map {
                it.copy(date = day, time = it.time.takeIf { _ -> it.deviation == Deviation.OtherTime })
            },
            replacing = replacing,
        )
        return true
    }

    /** Whether an entry can be at [day], having been at [replacing]: within reach, or where it was. */
    private fun canMoveTo(day: LocalDate, replacing: LocalDate?) = day == replacing || EntryDay.canRecord(day, today())

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
