package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDate

/**
 * The morning's entry of [date] (SPEC.md §17, "Morgens"): what was not filled in is null, "nicht erfasst", never 0. The
 * resting pulse lying down, the blood pressure, how long and how well the night was slept ([sleepQuality] 0 = sehr gut
 * to 3), whether one woke up rested, and whether the pulse was [measuredDifferently] than asked, after coffee say.
 * [recordedOn] is the day it was first recorded, by which it counts as made up ([backfilled]).
 */
@Entity(tableName = "morning_entry")
data class MorningEntry(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "resting_pulse") val restingPulse: Int? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    @ColumnInfo(name = "sleep_minutes") val sleepMinutes: Int? = null,
    @ColumnInfo(name = "sleep_quality") val sleepQuality: Int? = null,
    val rested: Boolean? = null,
    @ColumnInfo(name = "measured_differently") val measuredDifferently: Boolean = false,
    @ColumnInfo(name = "recorded_on") val recordedOn: LocalDate,
) {
    /** Whether it was recorded later than the day after: shown nowhere, only counted in the evaluation. */
    val backfilled: Boolean get() = EntryDay.isBackfilled(date, recordedOn)
}

/**
 * The evening's entry of [date] (SPEC.md §17, "Abends"), its symptoms apart ([SymptomValue]) and its intakes as far as
 * they went otherwise than planned ([IntakeDeviation]): null is "nicht erfasst", never 0. The load of the day, physical,
 * cognitive, emotional and social, 0–3; whether it was a working day and one with heat or long standing; how much was drunk, in
 * tenths of a litre; how many meals, coffees and drinks; and a note in Markdown.
 */
@Entity(tableName = "evening_entry")
data class EveningEntry(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "load_physical") val loadPhysical: Int? = null,
    @ColumnInfo(name = "load_cognitive") val loadCognitive: Int? = null,
    @ColumnInfo(name = "load_emotional") val loadEmotional: Int? = null,
    @ColumnInfo(name = "load_social") val loadSocial: Int? = null,
    val workday: Boolean? = null,
    @ColumnInfo(name = "heat_or_standing") val heatOrStanding: Boolean? = null,
    @ColumnInfo(name = "fluid_decilitres") val fluidDecilitres: Int? = null,
    val meals: Int? = null,
    val caffeine: Int? = null,
    val alcohol: Int? = null,
    val note: String = "",
    @ColumnInfo(name = "recorded_on") val recordedOn: LocalDate,
) {
    /** Whether it was recorded later than the day after: shown nowhere, only counted in the evaluation. */
    val backfilled: Boolean get() = EntryDay.isBackfilled(date, recordedOn)
}

/**
 * A symptom asked for in the evening (SPEC.md §17, "Symptome"), at [position] in the list; [hidden] ones are no longer
 * asked, but keep their values and show in the entries that have them.
 */
@Entity(tableName = "symptom")
data class Symptom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val hidden: Boolean = false,
)

/** How strong symptom [symptomId] was in the evening entry of [date], 0–3; one not recorded has no row. */
@Entity(
    tableName = "symptom_value",
    primaryKeys = ["date", "symptom_id"],
    foreignKeys = [
        ForeignKey(entity = EveningEntry::class, parentColumns = ["date"], childColumns = ["date"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = Symptom::class, parentColumns = ["id"], childColumns = ["symptom_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("symptom_id")],
)
data class SymptomValue(val date: LocalDate, @ColumnInfo(name = "symptom_id") val symptomId: Long, val level: Int)

/** The morning and evening entries and what belongs to them. */
@Dao
interface EntryDao {
    /** The morning entry of [date], if any. */
    @Query("SELECT * FROM morning_entry WHERE date = :date")
    suspend fun morning(date: LocalDate): MorningEntry?

    /** The evening entry of [date], if any. */
    @Query("SELECT * FROM evening_entry WHERE date = :date")
    suspend fun evening(date: LocalDate): EveningEntry?

    /** The days from [from] to [to] that have a morning entry. */
    @Query("SELECT date FROM morning_entry WHERE date BETWEEN :from AND :to")
    suspend fun morningDays(from: LocalDate, to: LocalDate): List<LocalDate>

    /** The resting pulses of the morning entries from [from] to [to] that have one. */
    @Query("SELECT resting_pulse FROM morning_entry WHERE date BETWEEN :from AND :to AND resting_pulse IS NOT NULL")
    suspend fun restingPulses(from: LocalDate, to: LocalDate): List<Int>

    /** The morning entries from [from] to [to]. */
    @Query("SELECT * FROM morning_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun morningsBetween(from: LocalDate, to: LocalDate): List<MorningEntry>

    /** The evening entries from [from] to [to]. */
    @Query("SELECT * FROM evening_entry WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun eveningsBetween(from: LocalDate, to: LocalDate): List<EveningEntry>

    /** The symptoms' values of the evenings from [from] to [to]. */
    @Query("SELECT * FROM symptom_value WHERE date BETWEEN :from AND :to")
    suspend fun valuesBetween(from: LocalDate, to: LocalDate): List<SymptomValue>

    /** The intakes otherwise than planned from [from] to [to]. */
    @Query("SELECT * FROM intake_deviation WHERE date BETWEEN :from AND :to")
    suspend fun deviationsBetween(from: LocalDate, to: LocalDate): List<IntakeDeviation>

    /** The answers about the exertions before of the evenings from [from] to [to]. */
    @Query("SELECT * FROM follow_up WHERE date BETWEEN :from AND :to")
    suspend fun followUpsBetween(from: LocalDate, to: LocalDate): List<FollowUp>

    /** The days from [from] to [to] that have an evening entry. */
    @Query("SELECT date FROM evening_entry WHERE date BETWEEN :from AND :to")
    suspend fun eveningDays(from: LocalDate, to: LocalDate): List<LocalDate>

    /** Writes [entry], in place of the one of its date. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMorning(entry: MorningEntry)

    /** Deletes the morning entry of [date]. */
    @Query("DELETE FROM morning_entry WHERE date = :date")
    suspend fun deleteMorning(date: LocalDate)

    /** Writes [entry], in place of the one of its date. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvening(entry: EveningEntry)

    /** Deletes the evening entry of [date], with its symptoms and intakes. */
    @Query("DELETE FROM evening_entry WHERE date = :date")
    suspend fun deleteEvening(date: LocalDate)

    /** The symptoms' values in the evening entry of [date]. */
    @Query("SELECT * FROM symptom_value WHERE date = :date")
    suspend fun values(date: LocalDate): List<SymptomValue>

    /** Adds [values]. */
    @Insert
    suspend fun insertValues(values: List<SymptomValue>)

    /** The intakes of [date] that went otherwise than planned. */
    @Query("SELECT * FROM intake_deviation WHERE date = :date")
    suspend fun deviations(date: LocalDate): List<IntakeDeviation>

    /** Adds [deviations]. */
    @Insert
    suspend fun insertDeviations(deviations: List<IntakeDeviation>)

    /**
     * The evening of [entry]'s date as a whole, in place of what was there for it and for [replacing], the date it had
     * before if it moved: its symptoms, its intakes otherwise than planned and its answers about the exertions before.
     */
    @Transaction
    suspend fun putEvening(entry: EveningEntry, values: List<SymptomValue>, deviations: List<IntakeDeviation>, followUps: List<FollowUp>, replacing: LocalDate?) {
        if (replacing != null && replacing != entry.date) deleteEvening(replacing)
        // Its rows go with it by cascade; written anew below.
        deleteEvening(entry.date)
        insertEvening(entry)
        insertValues(values)
        insertDeviations(deviations)
        insertFollowUps(followUps)
    }

    /** Adds [answers]. */
    @Insert
    suspend fun insertFollowUps(answers: List<FollowUp>)

    /** The answers of the evening entry of [date] about the exertions before. */
    @Query("SELECT * FROM follow_up WHERE date = :date")
    suspend fun followUps(date: LocalDate): List<FollowUp>

    /** The morning of [entry]'s date, in place of [replacing], the date it had before if it moved. */
    @Transaction
    suspend fun putMorning(entry: MorningEntry, replacing: LocalDate?) {
        if (replacing != null && replacing != entry.date) deleteMorning(replacing)
        insertMorning(entry)
    }
}

/** The symptoms, in their order. */
@Dao
interface SymptomDao {
    /** All symptoms, in their order. */
    @Query("SELECT * FROM symptom ORDER BY position, id")
    suspend fun all(): List<Symptom>

    /** Adds [symptoms]: their ids. */
    @Insert
    suspend fun insert(symptoms: List<Symptom>): List<Long>

    /** Renames symptom [id] to [name]. */
    @Query("UPDATE symptom SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** Hides symptom [id], or shows it again. */
    @Query("UPDATE symptom SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** Puts symptom [id] at [position] in the list. */
    @Query("UPDATE symptom SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)
}
