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

/** A setting of the Doku, kept in its own database: nothing of the Doku goes into the app's. */
@Entity(tableName = "doku_setting")
data class DokuSetting(@PrimaryKey val key: String, val value: String)

@Dao
interface DokuSettingsDao {
    @Query("SELECT value FROM doku_setting WHERE `key` = :key")
    suspend fun get(key: String): String?

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
@Database(entities = [DokuSetting::class], version = DokuDatabase.VERSION)
abstract class DokuDatabase : RoomDatabase() {
    abstract fun settings(): DokuSettingsDao

    companion object {
        const val VERSION = 1
        const val NAME = "doku.db"

        val MIGRATIONS: List<Migration> = emptyList()

        /**
         * Opens `doku.db` with its key in SQLCipher's raw form ([rawKey]); a wrong key fails on first access. The array
         * stays SQLCipher's while the database is open, so that wiping it once closed wipes SQLCipher's copy too.
         */
        fun open(context: Context, rawKey: ByteArray, name: String = NAME): DokuDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, DokuDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(rawKey))
                .addMigrations(*MIGRATIONS.toTypedArray())
                .build()
        }

        /** SQLCipher's raw-key form, x'<64 hex digits>', written straight into bytes, so that no string of it lingers. */
        fun rawKey(key: ByteArray): ByteArray {
            require(key.size == DokuKeys.KEY_BYTES) { "The Doku key must be ${DokuKeys.KEY_BYTES} bytes" }
            val raw = ByteArray(3 + key.size * 2)
            raw[0] = 'x'.code.toByte()
            raw[1] = '\''.code.toByte()
            key.forEachIndexed { i, byte ->
                raw[2 + 2 * i] = HEX[(byte.toInt() shr 4) and 0xF].code.toByte()
                raw[3 + 2 * i] = HEX[byte.toInt() and 0xF].code.toByte()
            }
            raw[raw.lastIndex] = '\''.code.toByte()
            return raw
        }

        private const val HEX = "0123456789abcdef"
    }
}
