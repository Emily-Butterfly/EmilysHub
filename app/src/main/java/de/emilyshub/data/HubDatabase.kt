package de.emilyshub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.emilyshub.places.Place
import de.emilyshub.places.PlaceDao
import de.emilyshub.places.PlaceWifi
import de.emilyshub.routines.ChecklistItem
import de.emilyshub.routines.ChecklistTick
import de.emilyshub.routines.Occurrence
import de.emilyshub.routines.Routine
import de.emilyshub.routines.RoutineDao
import de.emilyshub.routines.RoutinePlace
import de.emilyshub.routines.RoutineTag
import de.emilyshub.tags.Tag
import de.emilyshub.tags.TagDao
import de.emilyshub.todos.Todo
import de.emilyshub.todos.TodoChecklistItem
import de.emilyshub.todos.TodoDao
import de.emilyshub.todos.TodoPlace
import de.emilyshub.todos.TodoTag
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The app's only database, encrypted with SQLCipher (SPEC.md §9).
 *
 * Every schema change bumps [version] and adds a hand-written migration to [open]. There is
 * deliberately no destructive fallback: a missing migration fails loudly instead of wiping data.
 */
@Database(
    entities = [
        Setting::class, Routine::class, Occurrence::class, ChecklistItem::class, ChecklistTick::class, Tag::class, RoutineTag::class,
        Place::class, PlaceWifi::class, RoutinePlace::class,
        Todo::class, TodoChecklistItem::class, TodoTag::class, TodoPlace::class,
    ],
    version = HubDatabase.VERSION,
)
@TypeConverters(Converters::class)
abstract class HubDatabase : RoomDatabase() {
    abstract fun settings(): SettingsDao

    abstract fun routines(): RoutineDao

    abstract fun tags(): TagDao

    abstract fun places(): PlaceDao

    abstract fun todos(): TodoDao

    companion object {
        const val VERSION = 16
        private const val NAME = "emilyshub.db"
        private const val KEY_BYTES = 32

        /** Opens the database with its 256-bit [key]; a wrong key fails on first access. */
        fun open(context: Context, key: ByteArray, name: String = NAME): HubDatabase {
            require(key.size == KEY_BYTES) { "The database key must be $KEY_BYTES bytes" }
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, HubDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(rawKey(key)))
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                )
                .build()
        }

        /** SQLCipher's raw-key form, x'<64 hex digits>': the key is used as is, without slow passphrase derivation. */
        internal fun rawKey(key: ByteArray): ByteArray =
            "x'${key.toHexString()}'".toByteArray()
    }
}
