package de.emilyshub.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import java.time.ZoneId

// Hand-written on purpose: each one runs the SQL Room exports to app/schemas for its target version,
// and HubDatabaseMigrationTest has Room check the result against the app's entities.

/** Version 2: routines and their occurrences (#5). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `description` TEXT NOT NULL, `time` TEXT NOT NULL, `start_date` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `occurrences` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`routine_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_occurrences_routine_id_date_time` " +
                "ON `occurrences` (`routine_id`, `date`, `time`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_occurrences_date` ON `occurrences` (`date`)")
    }
}

/**
 * Version 3: routines with several times and a repetition (#6). Every routine so far is daily at its one
 * time, planned from the start of its first day in the phone's time zone. SQLite cannot change columns in
 * place, so the table is built anew; foreign keys are off during migrations, so dropping the old table
 * keeps the occurrences.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routines_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `description` TEXT NOT NULL, `times` TEXT NOT NULL, `repetition` TEXT NOT NULL, " +
                "`planned_from` TEXT NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `routines_new` (`id`, `name`, `description`, `times`, `repetition`, `planned_from`) " +
                "SELECT `id`, `name`, `description`, `time`, 'daily', `start_date` FROM `routines`",
        )
        val startDates = db.query("SELECT DISTINCT `planned_from` FROM `routines_new`").use { rows ->
            buildList { while (rows.moveToNext()) add(rows.getString(0)) }
        }
        for (date in startDates) {
            val start = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant()
            db.execSQL("UPDATE `routines_new` SET `planned_from` = ? WHERE `planned_from` = ?", arrayOf(start.toString(), date))
        }
        db.execSQL("DROP TABLE `routines`")
        db.execSQL("ALTER TABLE `routines_new` RENAME TO `routines`")
    }
}

/** Version 4: when an occurrence was put off to with "Später" (#7); none so far. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `occurrences` ADD COLUMN `remind_at` TEXT")
    }
}
