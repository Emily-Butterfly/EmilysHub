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

/** 2 → 3: the appointments (#60). */
val DOKU_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `appointment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patient_id` INTEGER NOT NULL, " +
                "`date` TEXT NOT NULL, `time` TEXT, `text` TEXT NOT NULL, " +
                "FOREIGN KEY(`patient_id`) REFERENCES `patient`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_patient_id` ON `appointment` (`patient_id`)")
    }
}
