package de.emilyshub.doku

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 1 → 2: the patients (#59). */
val DOKU_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `patient` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`birth_year` INTEGER, `diagnosis` TEXT NOT NULL, `anamnesis` TEXT NOT NULL, `goals` TEXT NOT NULL, " +
                "`other` TEXT NOT NULL, `created_at` TEXT NOT NULL, `closed_on` TEXT)",
        )
    }
}
