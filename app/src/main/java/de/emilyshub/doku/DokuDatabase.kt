package de.emilyshub.doku

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
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import de.emilyshub.data.Converters
import de.emilyshub.island.RawKey
import de.emilyshub.island.SECURE_DELETE
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * A setting of the Doku, kept in its own database: nothing of the Doku goes into the app's. Such as how the start page
 * orders the patients, and the appointment template.
 */
@Entity(tableName = "doku_setting")
data class DokuSetting(@PrimaryKey val key: String, val value: String)

/** The Doku's settings, by key. */
@Dao
interface DokuSettingsDao {
    /** The value under [key], if any. */
    @Query("SELECT value FROM doku_setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    /** Sets [setting], in place of the value under its key. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: DokuSetting)
}

/**
 * The Doku's own database, `doku.db` (SPEC.md §14), apart from [de.emilyshub.data.HubDatabase] and encrypted with
 * SQLCipher under a key of its own, which only the Doku password or the fingerprint set free ([Doku]).
 *
 * As the app's, every schema change bumps [VERSION] and adds a hand-written migration to [MIGRATIONS]; there is
 * deliberately no destructive fallback.
 */
@Database(
    entities = [
        DokuSetting::class,
        Patient::class,
        Appointment::class,
        ClinicalTest::class,
        TestValue::class,
        Measurement::class,
        MeasurementValue::class,
        AssessmentEntry::class,
        AssessmentAnswer::class,
    ],
    version = DokuDatabase.VERSION,
)
@TypeConverters(Converters::class, DokuConverters::class)
abstract class DokuDatabase : RoomDatabase() {
    abstract fun settings(): DokuSettingsDao

    abstract fun patients(): PatientDao

    abstract fun appointments(): AppointmentDao

    abstract fun tests(): TestDao

    abstract fun measurements(): MeasurementDao

    abstract fun assessments(): AssessmentDao

    companion object {
        const val VERSION = 5
        const val NAME = "doku.db"

        val MIGRATIONS: List<Migration> = listOf(DOKU_MIGRATION_1_2, DOKU_MIGRATION_2_3, DOKU_MIGRATION_3_4, DOKU_MIGRATION_4_5)

        /** Opens `doku.db`, or a database [name]d otherwise, with its [rawKey]; a wrong key fails on first access. */
        fun open(context: Context, rawKey: RawKey, name: String = NAME): DokuDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, DokuDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(rawKey.bytes))
                .addMigrations(*MIGRATIONS.toTypedArray())
                .addCallback(SECURE_DELETE)
                .build()
        }
    }
}
