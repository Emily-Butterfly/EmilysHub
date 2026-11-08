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

/** 3 → 4: the tests, with their values, and the measurements at appointments (#61, #62). */
val DOKU_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `doku_test` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`has_sum` INTEGER NOT NULL, `sum_direction` TEXT NOT NULL, `archived_on` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `doku_test_value` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `test_id` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `min` INTEGER, `max` INTEGER, " +
                "`decimals` INTEGER NOT NULL, `direction` TEXT NOT NULL, `retired` INTEGER NOT NULL, " +
                "FOREIGN KEY(`test_id`) REFERENCES `doku_test`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_doku_test_value_test_id` ON `doku_test_value` (`test_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`appointment_id` INTEGER NOT NULL, `test_id` INTEGER NOT NULL, `remark` TEXT NOT NULL, " +
                "FOREIGN KEY(`appointment_id`) REFERENCES `appointment`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`test_id`) REFERENCES `doku_test`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_appointment_id` ON `measurement` (`appointment_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_test_id` ON `measurement` (`test_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurement_value` (`measurement_id` INTEGER NOT NULL, `value_id` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, PRIMARY KEY(`measurement_id`, `value_id`), " +
                "FOREIGN KEY(`measurement_id`) REFERENCES `measurement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`value_id`) REFERENCES `doku_test_value`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_measurement_value_value_id` ON `measurement_value` (`value_id`)")
    }
}
