package de.emilyshub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The app's only database, encrypted with SQLCipher (SPEC.md §9).
 *
 * Every schema change bumps [version] and adds a hand-written migration to [open]. There is
 * deliberately no destructive fallback: a missing migration fails loudly instead of wiping data.
 */
@Database(entities = [Setting::class], version = 1)
abstract class HubDatabase : RoomDatabase() {
    abstract fun settings(): SettingsDao

    companion object {
        private const val NAME = "emilyshub.db"
        private const val KEY_BYTES = 32

        /** Opens the database with its 256-bit [key]; a wrong key fails on first access. */
        fun open(context: Context, key: ByteArray, name: String = NAME): HubDatabase {
            require(key.size == KEY_BYTES) { "The database key must be $KEY_BYTES bytes" }
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, HubDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(rawKey(key)))
                .build()
        }

        /** SQLCipher's raw-key form, x'<64 hex digits>': the key is used as is, without slow passphrase derivation. */
        private fun rawKey(key: ByteArray): ByteArray =
            "x'${key.toHexString()}'".toByteArray()
    }
}
