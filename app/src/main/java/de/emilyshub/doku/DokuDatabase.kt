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
import androidx.room.migration.Migration
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * A setting of the Doku, kept in its own database: nothing of the Doku goes into the app's. None yet: the table is
 * here because Room needs one, and for the settings the start page and the appointment template bring (#59, #60).
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
 * The Doku's database key in SQLCipher's raw form, x'<64 hex digits>', used as is without a slow passphrase derivation.
 * The bytes stay SQLCipher's while the database is open, so that wiping them once it is closed wipes SQLCipher's copy.
 */
@JvmInline
value class RawKey(val bytes: ByteArray) {
    fun wipe() = bytes.fill(0)
}

/**
 * The Doku's own database, `doku.db` (SPEC.md §14), apart from [de.emilyshub.data.HubDatabase] and encrypted with
 * SQLCipher under a key of its own, which only the Doku password or the fingerprint set free ([Doku]).
 *
 * As the app's, every schema change bumps [VERSION] and adds a hand-written migration to [MIGRATIONS]; there is
 * deliberately no destructive fallback.
 */
@Database(entities = [DokuSetting::class], version = DokuDatabase.VERSION)
abstract class DokuDatabase : RoomDatabase() {
    abstract fun settings(): DokuSettingsDao

    companion object {
        const val VERSION = 1
        const val NAME = "doku.db"

        val MIGRATIONS: List<Migration> = emptyList()

        /** Opens `doku.db`, or a database [name]d otherwise, with its [rawKey]; a wrong key fails on first access. */
        fun open(context: Context, rawKey: RawKey, name: String = NAME): DokuDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, DokuDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(rawKey.bytes))
                .addMigrations(*MIGRATIONS.toTypedArray())
                .build()
        }

        /** The [RawKey] of [key], written straight into bytes, so that no string of it lingers. */
        fun rawKey(key: ByteArray): RawKey {
            require(key.size == DokuKeys.KEY_BYTES) { "The Doku key must be ${DokuKeys.KEY_BYTES} bytes" }
            val raw = ByteArray(3 + key.size * 2)
            raw[0] = 'x'.code.toByte()
            raw[1] = '\''.code.toByte()
            key.forEachIndexed { i, byte ->
                raw[2 + 2 * i] = HEX[(byte.toInt() shr 4) and 0xF].code.toByte()
                raw[3 + 2 * i] = HEX[byte.toInt() and 0xF].code.toByte()
            }
            raw[raw.lastIndex] = '\''.code.toByte()
            return RawKey(raw)
        }

        private const val HEX = "0123456789abcdef"
    }
}
