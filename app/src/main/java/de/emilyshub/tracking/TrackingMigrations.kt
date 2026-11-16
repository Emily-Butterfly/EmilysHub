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

/** 5 → 6: the exertions, the suggestions decided, the follow-up answers and the episodes with their triggers (#129). */
val TRACKING_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exertion` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                "`time` TEXT, `kind` TEXT NOT NULL, `duration_minutes` INTEGER, `strength` INTEGER NOT NULL, " +
                "`origin` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exertion_date` ON `exertion` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `suggestion_decision` (`date` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`confirmed` INTEGER NOT NULL, PRIMARY KEY(`date`, `source`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `follow_up` (`date` TEXT NOT NULL, `exertion_id` INTEGER NOT NULL, " +
                "`answer` TEXT NOT NULL, PRIMARY KEY(`date`, `exertion_id`), " +
                "FOREIGN KEY(`date`) REFERENCES `evening_entry`(`date`) ON UPDATE CASCADE ON DELETE CASCADE , " +
                "FOREIGN KEY(`exertion_id`) REFERENCES `exertion`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_follow_up_exertion_id` ON `follow_up` (`exertion_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `episode` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`start_date` TEXT NOT NULL, `start_time` TEXT, `end_date` TEXT, `severity` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `episode_trigger` (`episode_id` INTEGER NOT NULL, `exertion_id` INTEGER NOT NULL, " +
                "`certainty` TEXT NOT NULL, PRIMARY KEY(`episode_id`, `exertion_id`), " +
                "FOREIGN KEY(`episode_id`) REFERENCES `episode`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`exertion_id`) REFERENCES `exertion`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_trigger_exertion_id` ON `episode_trigger` (`exertion_id`)")
    }
}

/** 6 → 7: the social load of the evening. */
val TRACKING_MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `evening_entry` ADD COLUMN `load_social` INTEGER")
    }
}

/** 7 → 8: the photos of the events (#133). */
val TRACKING_MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`event_id` INTEGER NOT NULL, `taken_at` TEXT NOT NULL, `jpeg` BLOB NOT NULL, " +
                "FOREIGN KEY(`event_id`) REFERENCES `tracking_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_photo_event_id` ON `event_photo` (`event_id`)")
    }
}

/**
 * 8 → 9: one's own pacing threshold with the day it holds from, so that each day keeps the threshold of its time. One
 * set before, in the settings, holds from the first day on, as it did.
 */
val TRACKING_MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `pacing_threshold` (`from_date` TEXT NOT NULL, `bpm` INTEGER, PRIMARY KEY(`from_date`))")
        db.execSQL(
            "INSERT INTO `pacing_threshold` (`from_date`, `bpm`) SELECT '1970-01-01', CAST(value AS INTEGER) " +
                "FROM `tracking_setting` WHERE `key` = 'pacing.own_threshold' AND value != ''",
        )
        db.execSQL("DELETE FROM `tracking_setting` WHERE `key` = 'pacing.own_threshold'")
    }
}

/**
 * 9 → 10: the photos of the events lie in files of their own (ADR 0004). Each row gets the name of its file by chance,
 * and its JPEG waits in [EventPhotos.TO_MOVE] until Tracking is open and has the key for the files
 * ([EventPhotos.moveOutOfDatabase]).
 */
val TRACKING_MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_photo_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`event_id` INTEGER NOT NULL, `taken_at` TEXT NOT NULL, `file` TEXT NOT NULL, " +
                "FOREIGN KEY(`event_id`) REFERENCES `tracking_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("INSERT INTO `event_photo_new` (`id`, `event_id`, `taken_at`, `file`) SELECT `id`, `event_id`, `taken_at`, lower(hex(randomblob(16))) FROM `event_photo`")
        db.execSQL(
            "CREATE TABLE `${EventPhotos.TO_MOVE}` AS SELECT n.`file` AS `file`, o.`jpeg` AS `jpeg` " +
                "FROM `event_photo_new` n JOIN `event_photo` o ON o.`id` = n.`id`",
        )
        db.execSQL("DROP TABLE `event_photo`")
        db.execSQL("ALTER TABLE `event_photo_new` RENAME TO `event_photo`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_photo_event_id` ON `event_photo` (`event_id`)")
    }
}

/** 10 → 11: the monthly questionnaires FUNCAP55 with their answers (#140). */
val TRACKING_MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `funcap_form` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`started_on` TEXT NOT NULL, `completed_on` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `funcap_answer` (`form_id` INTEGER NOT NULL, `item` INTEGER NOT NULL, " +
                "`value` INTEGER NOT NULL, PRIMARY KEY(`form_id`, `item`), " +
                "FOREIGN KEY(`form_id`) REFERENCES `funcap_form`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
