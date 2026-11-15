package de.emilyshub.tracking

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 1 → 2: the morning and evening entries with their symptoms (#124), and the medications with their timeline (#125). */
val TRACKING_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `morning_entry` (`date` TEXT NOT NULL, `resting_pulse` INTEGER, " +
                "`systolic` INTEGER, `diastolic` INTEGER, `sleep_minutes` INTEGER, `sleep_quality` INTEGER, " +
                "`rested` INTEGER, `measured_differently` INTEGER NOT NULL, `recorded_on` TEXT NOT NULL, " +
                "PRIMARY KEY(`date`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `evening_entry` (`date` TEXT NOT NULL, `load_physical` INTEGER, " +
                "`load_cognitive` INTEGER, `load_emotional` INTEGER, `workday` INTEGER, `heat_or_standing` INTEGER, " +
                "`fluid_decilitres` INTEGER, `meals` INTEGER, `caffeine` INTEGER, `alcohol` INTEGER, " +
                "`note` TEXT NOT NULL, `recorded_on` TEXT NOT NULL, PRIMARY KEY(`date`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `symptom` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `hidden` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `symptom_value` (`date` TEXT NOT NULL, `symptom_id` INTEGER NOT NULL, " +
                "`level` INTEGER NOT NULL, PRIMARY KEY(`date`, `symptom_id`), " +
                "FOREIGN KEY(`date`) REFERENCES `evening_entry`(`date`) ON UPDATE CASCADE ON DELETE CASCADE , " +
                "FOREIGN KEY(`symptom_id`) REFERENCES `symptom`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_symptom_value_symptom_id` ON `symptom_value` (`symptom_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `medication` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `form` TEXT NOT NULL, `times` TEXT NOT NULL, `purpose` TEXT NOT NULL, " +
                "`thrombosis_risk` INTEGER NOT NULL, `lowers_pulse` INTEGER NOT NULL, `side_effects` TEXT NOT NULL, " +
                "`start` TEXT NOT NULL, `end` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `dose_change` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`medication_id` INTEGER NOT NULL, `date` TEXT NOT NULL, `dose_before` TEXT NOT NULL, " +
                "`dose_after` TEXT NOT NULL, " +
                "FOREIGN KEY(`medication_id`) REFERENCES `medication`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dose_change_medication_id` ON `dose_change` (`medication_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `intake_deviation` (`date` TEXT NOT NULL, " +
                "`medication_id` INTEGER NOT NULL, `deviation` TEXT NOT NULL, `time` TEXT, PRIMARY KEY(`date`, " +
                "`medication_id`), " +
                "FOREIGN KEY(`date`) REFERENCES `evening_entry`(`date`) ON UPDATE CASCADE ON DELETE CASCADE , " +
                "FOREIGN KEY(`medication_id`) REFERENCES `medication`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_intake_deviation_medication_id` ON `intake_deviation` (`medication_id`)")
    }
}

/** 2 → 3: the values read from Health Connect (#126). */
val TRACKING_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `garmin_sample` (`key` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`start_ms` INTEGER NOT NULL, `end_ms` INTEGER NOT NULL, `value` INTEGER NOT NULL, " +
                "`source_app` TEXT NOT NULL, `device` TEXT NOT NULL, `model` TEXT, PRIMARY KEY(`key`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_garmin_sample_kind_start_ms` ON `garmin_sample` (`kind`, `start_ms`)")
    }
}

/** 3 → 4: the Stehtests with their points and the intakes before them (#127). */
val TRACKING_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stand_test` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`variant` TEXT NOT NULL, `started_at` TEXT NOT NULL, `standing_at` TEXT, `lying_pulse` INTEGER, " +
                "`lying_systolic` INTEGER, `lying_diastolic` INTEGER, `caffeine` INTEGER, " +
                "`meal_minutes_before` INTEGER, `fluid_decilitres` INTEGER, `abort_cause` TEXT, " +
                "`aborted_minute` INTEGER, `abort_reason` TEXT NOT NULL, `finished` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stand_point` (`test_id` INTEGER NOT NULL, `minute` INTEGER NOT NULL, " +
                "`pulse` INTEGER, `systolic` INTEGER, `diastolic` INTEGER, `dizziness` INTEGER, " +
                "`palpitations` INTEGER, `breathlessness` INTEGER, `legs_discoloured` INTEGER, " +
                "PRIMARY KEY(`test_id`, `minute`), " +
                "FOREIGN KEY(`test_id`) REFERENCES `stand_test`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stand_intake` (`test_id` INTEGER NOT NULL, `medication_id` INTEGER NOT NULL, " +
                "`minutes_before` INTEGER NOT NULL, PRIMARY KEY(`test_id`, `medication_id`), " +
                "FOREIGN KEY(`test_id`) REFERENCES `stand_test`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`medication_id`) REFERENCES `medication`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stand_intake_medication_id` ON `stand_intake` (`medication_id`)")
    }
}

/** 4 → 5: the events with the intakes before them (#128). */
val TRACKING_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tracking_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, `at` TEXT NOT NULL, `water` TEXT, `duration_minutes` INTEGER, `posture` TEXT, " +
                "`pulse_before` INTEGER, `ended_at` TEXT, `pulse_after` INTEGER, `pulse_five` INTEGER, " +
                "`pulse_ten` INTEGER, `spo2` INTEGER, `breathlessness` INTEGER, `dizziness` INTEGER, " +
                "`sat_down` INTEGER, `sat_down_after_minutes` INTEGER, `skin_areas` TEXT NOT NULL, " +
                "`skin_pattern` TEXT, `skin_painful` INTEGER, `skin_fades_lying` INTEGER, " +
                "`skin_fade_minutes` INTEGER, `recovered_minutes` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_intake` (`event_id` INTEGER NOT NULL, `medication_id` INTEGER NOT NULL, " +
                "`minutes_before` INTEGER NOT NULL, PRIMARY KEY(`event_id`, `medication_id`), " +
                "FOREIGN KEY(`event_id`) REFERENCES `tracking_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`medication_id`) REFERENCES `medication`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_intake_medication_id` ON `event_intake` (`medication_id`)")
    }
}
