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

/** Version 5: how often and how far apart a routine reminds again (#8); every routine so far gets the defaults. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `follow_up_count` INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `follow_up_minutes` INTEGER NOT NULL DEFAULT 30")
    }
}

/** Version 6: whether a routine reminds with a full-screen alarm (#9); none does so far. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `full_screen` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 7: how long each occurrence of a routine can be caught up on (#10); every routine so far gets four hours. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `window_hours` INTEGER NOT NULL DEFAULT 4")
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `window_until_midnight` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Version 8: a routine's priority, whether it is in use, and its checklist with what of it is ticked off on each
 * occurrence (#11); every routine so far is active, of normal priority and without a checklist.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `priority` TEXT NOT NULL DEFAULT 'normal'")
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'active'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`routine_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                "FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_routine_id` ON `checklist_items` (`routine_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `checklist_ticks` (`occurrence_id` INTEGER NOT NULL, `item_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`occurrence_id`, `item_id`), " +
                "FOREIGN KEY(`occurrence_id`) REFERENCES `occurrences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`item_id`) REFERENCES `checklist_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_ticks_item_id` ON `checklist_ticks` (`item_id`)")
    }
}

/**
 * Version 9: the tags shared by all areas, and which routines have which (#12); there are none so far. Later areas
 * add a link table of their own.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_tags` (`routine_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`routine_id`, `tag_id`), " +
                "FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_tags_tag_id` ON `routine_tags` (`tag_id`)")
    }
}

/** Version 10: named places and the WLANs at which they are recognised (#13); there are none so far. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `place_wifis` (`place_id` INTEGER NOT NULL, `ssid` TEXT NOT NULL, " +
                "PRIMARY KEY(`place_id`, `ssid`), " +
                "FOREIGN KEY(`place_id`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_place_wifis_ssid` ON `place_wifis` (`ssid`)")
    }
}

/** Version 11: the same WLAN can be at several places (#13), such as "eduroam" at a university and at work. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_place_wifis_ssid`")
    }
}

/**
 * Version 12: routines bound to places, and occurrences that wait for them (#14); so far no routine is bound to one,
 * and nothing waits.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `occurrences` ADD COLUMN `waiting` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routine_places` (`routine_id` INTEGER NOT NULL, `place_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`routine_id`, `place_id`), " +
                "FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`place_id`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_places_place_id` ON `routine_places` (`place_id`)")
    }
}

/** Version 13: a place can have a GPS area (#15); the places there are have none. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `places` ADD COLUMN `area_latitude` REAL")
        db.execSQL("ALTER TABLE `places` ADD COLUMN `area_longitude` REAL")
        db.execSQL("ALTER TABLE `places` ADD COLUMN `area_radius` INTEGER")
    }
}

/** Version 14: the to-dos (#17), with their checklists and tags; none yet. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `due_date` TEXT, `due_time` TEXT, `priority` TEXT NOT NULL, `done_at` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todo_checklist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`todo_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `ticked` INTEGER NOT NULL, " +
                "FOREIGN KEY(`todo_id`) REFERENCES `todos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_checklist_items_todo_id` ON `todo_checklist_items` (`todo_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todo_tags` (`todo_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`todo_id`, `tag_id`), " +
                "FOREIGN KEY(`todo_id`) REFERENCES `todos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_tags_tag_id` ON `todo_tags` (`tag_id`)")
    }
}

/**
 * To-dos remind (SPEC.md §5): their reminder's state, and the places they remind at. An open one with a time still ahead
 * on the phone's clock reminds at it, as it would saved now; one whose time is gone by does not.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `reminds_at_due_time` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `remind_at` TEXT")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `waiting` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE `todos` SET `reminds_at_due_time` = 1 WHERE `due_time` IS NOT NULL AND `done_at` IS NULL " +
                "AND `due_date` || 'T' || `due_time` > strftime('%Y-%m-%dT%H:%M', 'now', 'localtime')",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todo_places` (`todo_id` INTEGER NOT NULL, `place_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`todo_id`, `place_id`), " +
                "FOREIGN KEY(`todo_id`) REFERENCES `todos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`place_id`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_places_place_id` ON `todo_places` (`place_id`)")
    }
}

/** To-dos note when they were seen away from their places, so that being at one after that is arriving (SPEC.md §4). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `away` INTEGER NOT NULL DEFAULT 0")
    }
}
