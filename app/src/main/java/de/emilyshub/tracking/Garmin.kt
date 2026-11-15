package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import java.time.Instant

/** What a value read from Health Connect is (SPEC.md §17, "Garmin über Health Connect"). */
enum class SampleKind(val stored: String) {
    /** A heart rate, per minute, at a moment. */
    HeartRate("heart_rate"),

    /** The resting heart rate the writing app worked out, per minute: Garmin's, apart from the morning's own. */
    RestingHeartRate("resting_heart_rate"),

    /** Steps counted from its start to its end. */
    Steps("steps"),

    /** A night's sleep, its length in minutes. */
    Sleep("sleep"),

    /** A stage of a night's sleep, by Health Connect's number of it: Garmin's own reckoning. */
    SleepStage("sleep_stage"),
}

/** What measured a value, as the writing app tells Health Connect; unknown when it does not. */
enum class DeviceKind(val stored: String) {
    /** A watch, measuring at the wrist. */
    Watch("watch"),

    /** A chest strap, measuring electrically. */
    ChestStrap("chest_strap"),

    /** Anything else, or not told. */
    Unknown("unknown"),
}

/**
 * A value read from Health Connect, kept as a copy in Tracking (SPEC.md §17): of [kind], from [start] to [end] (the same
 * moment for a heart rate), its [value], the app it came from ([sourceApp]) and what measured it. [key] is Health
 * Connect's id of its record, with the place within it for a record of several, so that reading again adds without
 * doubling. The moments are kept in milliseconds, so that the database compares them as numbers.
 */
@Entity(tableName = "garmin_sample", indices = [Index("kind", "start_ms")])
data class GarminSample(
    @PrimaryKey val key: String,
    val kind: SampleKind,
    @ColumnInfo(name = "start_ms") val startMillis: Long,
    @ColumnInfo(name = "end_ms") val endMillis: Long,
    val value: Long,
    @ColumnInfo(name = "source_app") val sourceApp: String,
    val device: DeviceKind,
    val model: String? = null,
) {
    /** When it begins, or the moment of a heart rate. */
    val start: Instant get() = Instant.ofEpochMilli(startMillis)

    /** When it ends, the same as [start] for a heart rate. */
    val end: Instant get() = Instant.ofEpochMilli(endMillis)

    /** The id of the record in Health Connect it came from. */
    val recordId: String get() = key.substringBefore(PLACE)

    companion object {
        /** Between a record's id and the place of a value within it, in [key]. */
        const val PLACE = '#'

        /** A value of [kind] from [start] to [end]. */
        fun of(key: String, kind: SampleKind, start: Instant, end: Instant, value: Long, sourceApp: String, device: DeviceKind, model: String? = null) =
            GarminSample(key, kind, start.toEpochMilli(), end.toEpochMilli(), value, sourceApp, device, model)
    }
}

/** Where values of one kind came from: an app and what measured them there, and how many. */
data class SampleSource(
    val kind: SampleKind,
    @ColumnInfo(name = "source_app") val sourceApp: String,
    val device: DeviceKind,
    @ColumnInfo(name = "sample_count") val count: Int,
)

/** The values read from Health Connect. */
@Dao
interface GarminDao {
    /** Writes [samples], each in place of one of its key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(samples: List<GarminSample>)

    /** Deletes the values of record [id], and those within it ([prefix]: its id and [GarminSample.PLACE], then %). */
    @Query("DELETE FROM garmin_sample WHERE `key` = :id OR `key` LIKE :prefix")
    suspend fun deleteRecord(id: String, prefix: String)

    /**
     * Writes [samples] in place of what their records held before, so that a record written anew with fewer values,
     * such as heart rates, leaves none of its old ones behind.
     */
    @Transaction
    suspend fun put(samples: List<GarminSample>) {
        samples.map { it.recordId }.distinct().forEach { deleteRecord(it, "$it${GarminSample.PLACE}%") }
        insert(samples)
    }

    /** Where the steps came in up to: the end of the last span of them. */
    @Query("SELECT MAX(end_ms) FROM garmin_sample WHERE kind = 'steps'")
    suspend fun stepsUntilMillis(): Long?

    /** The values of [kind] that overlap [fromMillis] to [toMillis], by their start. */
    @Query("SELECT * FROM garmin_sample WHERE kind = :kind AND start_ms < :toMillis AND end_ms > :fromMillis ORDER BY start_ms")
    suspend fun overlapping(kind: SampleKind, fromMillis: Long, toMillis: Long): List<GarminSample>

    /** Where the values came from, by kind, app and what measured them. */
    @Query(
        "SELECT kind, source_app, device, COUNT(*) AS sample_count FROM garmin_sample " +
            "GROUP BY kind, source_app, device ORDER BY kind, sample_count DESC",
    )
    suspend fun sources(): List<SampleSource>

    /** The last value of [kind] from [sourceApp] measured by [device]. */
    @Query(
        "SELECT * FROM garmin_sample WHERE kind = :kind AND source_app = :sourceApp AND device = :device " +
            "ORDER BY start_ms DESC LIMIT 1",
    )
    suspend fun last(kind: SampleKind, sourceApp: String, device: DeviceKind): GarminSample?

    /** How many values there are. */
    @Query("SELECT COUNT(*) FROM garmin_sample")
    suspend fun count(): Int
}
