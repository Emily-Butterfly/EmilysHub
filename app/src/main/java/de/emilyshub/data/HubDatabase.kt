package de.emilyshub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteOpenHelper
import de.emilyshub.backup.BackupDao
import de.emilyshub.ledger.Booking
import de.emilyshub.ledger.LedgerCategory
import de.emilyshub.ledger.LedgerDao
import de.emilyshub.notes.GeneralTab
import de.emilyshub.notes.Note
import de.emilyshub.notes.NoteDao
import de.emilyshub.notes.NoteFolder
import de.emilyshub.notes.NoteGoal
import de.emilyshub.notes.NoteRoutine
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.NoteTag
import de.emilyshub.notes.NoteTodo
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
import de.emilyshub.search.SearchDao
import de.emilyshub.tags.Tag
import de.emilyshub.tags.TagDao
import de.emilyshub.todos.Todo
import de.emilyshub.todos.TodoChecklistItem
import de.emilyshub.todos.TodoDao
import de.emilyshub.todos.TodoPlace
import de.emilyshub.todos.TodoTag
import de.emilyshub.wishes.Wish
import de.emilyshub.wishes.WishDao
import de.emilyshub.wishes.WishTag
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The app's only database, encrypted with SQLCipher (SPEC.md §9).
 *
 * Every schema change bumps [VERSION] and adds a hand-written migration to [MIGRATIONS]. There is
 * deliberately no destructive fallback: a missing migration fails loudly instead of wiping data.
 */
@Database(
    entities = [
        Setting::class, Routine::class, Occurrence::class, ChecklistItem::class, ChecklistTick::class, Tag::class, RoutineTag::class,
        Place::class, PlaceWifi::class, RoutinePlace::class,
        Todo::class, TodoChecklistItem::class, TodoTag::class, TodoPlace::class,
        Wish::class, WishTag::class,
        NoteTab::class, Note::class, NoteFolder::class, GeneralTab::class,
        NoteGoal::class, NoteTodo::class, NoteRoutine::class, NoteTag::class,
        LedgerCategory::class, Booking::class,
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

    abstract fun wishes(): WishDao

    abstract fun notes(): NoteDao

    abstract fun ledger(): LedgerDao

    abstract fun search(): SearchDao

    abstract fun backup(): BackupDao

    companion object {
        const val VERSION = 28
        private const val NAME = "emilyshub.db"
        private const val KEY_BYTES = 32

        /** Opens the database with its 256-bit [key]; a wrong key fails on first access. */
        fun open(context: Context, key: ByteArray, name: String = NAME): HubDatabase =
            Room.databaseBuilder(context, HubDatabase::class.java, name)
                .openHelperFactory(openHelpers(key))
                .addMigrations(*MIGRATIONS.toTypedArray())
                .build()

        /** SQLCipher's, for databases with the 256-bit [key]: the app's database, and those its backups are migrated in. */
        fun openHelpers(key: ByteArray): SupportSQLiteOpenHelper.Factory {
            require(key.size == KEY_BYTES) { "The database key must be $KEY_BYTES bytes" }
            System.loadLibrary("sqlcipher")
            return SupportOpenHelperFactory(rawKey(key))
        }

        /** From each version to the next; also for backups of older versions (SPEC.md §10). */
        val MIGRATIONS: List<Migration> = listOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
            MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
        )

        /** SQLCipher's raw-key form, x'<64 hex digits>': the key is used as is, without slow passphrase derivation. */
        internal fun rawKey(key: ByteArray): ByteArray =
            "x'${key.toHexString()}'".toByteArray()
    }
}
