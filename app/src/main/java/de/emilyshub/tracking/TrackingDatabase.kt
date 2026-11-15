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
import androidx.room.migration.Migration
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
 * ([Tracking]). The slices after #123 fill it.
 *
 * As the app's, every schema change bumps [VERSION] and adds a hand-written migration to [MIGRATIONS]; there is
 * deliberately no destructive fallback.
 */
@Database(entities = [TrackingSetting::class], version = TrackingDatabase.VERSION)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun settings(): TrackingSettingsDao

    companion object {
        const val VERSION = 1
        const val NAME = "tracking.db"

        val MIGRATIONS: List<Migration> = emptyList()

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
