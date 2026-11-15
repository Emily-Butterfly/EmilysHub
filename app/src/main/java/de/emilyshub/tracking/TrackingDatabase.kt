package de.emilyshub.tracking

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import de.emilyshub.data.Converters
import de.emilyshub.island.RawKey
import de.emilyshub.island.SECURE_DELETE
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** A setting of Tracking, kept in its own database: nothing of Tracking goes into the app's or the Doku's. */
@Entity(tableName = "tracking_setting")
data class TrackingSetting(@PrimaryKey val key: String, val value: String)

/** Tracking's settings, by key. */
@Dao
interface TrackingSettingsDao {
    /** The value under [key], if any. */
    @Query("SELECT value FROM tracking_setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    /** Sets [setting], in place of the value under its key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: TrackingSetting)
}

/**
 * Tracking's own database, `tracking.db` (SPEC.md §17), apart from [de.emilyshub.data.HubDatabase] and from the Doku's,
 * encrypted with SQLCipher under a key of its own, which only the Tracking password or its fingerprint set free
 * ([Tracking]): the day's entries and their symptoms (#124), the medications (#125), the values read from Health
 * Connect (#126), the Stehtests (#127); the base data in its settings.
 *
 * As the app's, every schema change bumps [VERSION] and adds a hand-written migration to [MIGRATIONS]; there is
 * deliberately no destructive fallback.
 */
@Database(
    entities = [
        TrackingSetting::class,
        MorningEntry::class,
        EveningEntry::class,
        Symptom::class,
        SymptomValue::class,
        Medication::class,
        DoseChange::class,
        IntakeDeviation::class,
        GarminSample::class,
        StandTest::class,
        StandPoint::class,
        StandIntake::class,
    ],
    version = TrackingDatabase.VERSION,
)
@TypeConverters(Converters::class, TrackingConverters::class)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun settings(): TrackingSettingsDao

    abstract fun entries(): EntryDao

    abstract fun symptoms(): SymptomDao

    abstract fun medications(): MedicationDao

    abstract fun garmin(): GarminDao

    abstract fun standTests(): StandTestDao

    companion object {
        const val VERSION = 4
        const val NAME = "tracking.db"

        val MIGRATIONS: List<Migration> = listOf(TRACKING_MIGRATION_1_2, TRACKING_MIGRATION_2_3, TRACKING_MIGRATION_3_4)

        /** Opens `tracking.db`, or a database [name]d otherwise, with its [rawKey]; a wrong key fails on first access. */
        fun open(context: Context, rawKey: RawKey, name: String = NAME): TrackingDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, TrackingDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(rawKey.bytes))
                .addMigrations(*MIGRATIONS.toTypedArray())
                .addCallback(SECURE_DELETE)
                .build()
        }
    }
}

/** What Room keeps of Tracking's own types. */
class TrackingConverters {
    @TypeConverter
    fun fromDeviation(deviation: Deviation): String = deviation.stored

    @TypeConverter
    fun toDeviation(text: String): Deviation = Deviation.entries.first { it.stored == text }

    @TypeConverter
    fun fromSampleKind(kind: SampleKind): String = kind.stored

    @TypeConverter
    fun toSampleKind(text: String): SampleKind = SampleKind.entries.first { it.stored == text }

    @TypeConverter
    fun fromDeviceKind(device: DeviceKind): String = device.stored

    @TypeConverter
    fun toDeviceKind(text: String): DeviceKind = DeviceKind.entries.first { it.stored == text }

    @TypeConverter
    fun fromStandVariant(variant: StandVariant): String = variant.stored

    @TypeConverter
    fun toStandVariant(text: String): StandVariant = StandVariant.entries.first { it.stored == text }

    @TypeConverter
    fun fromAbortCause(cause: AbortCause?): String? = cause?.stored

    @TypeConverter
    fun toAbortCause(text: String?): AbortCause? = text?.let { AbortCause.entries.first { cause -> cause.stored == it } }
}
