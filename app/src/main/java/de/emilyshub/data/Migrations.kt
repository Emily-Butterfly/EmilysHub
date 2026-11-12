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

/** Version 17: the wishlist (#19), wishes with their tags. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wishes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `period` TEXT NOT NULL, `status` TEXT NOT NULL, `done_on` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wish_tags` (`wish_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`wish_id`, `tag_id`), " +
                "FOREIGN KEY(`wish_id`) REFERENCES `wishes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wish_tags_tag_id` ON `wish_tags` (`tag_id`)")
    }
}

/**
 * Version 18: goals (#20). A wish can be part of another, to any depth, and have a progress set by hand; a to-do can be
 * part of a goal. Every wish is at the top and every to-do part of none, as before.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `wishes` ADD COLUMN `parent_id` INTEGER " +
                "REFERENCES `wishes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE",
        )
        db.execSQL("ALTER TABLE `wishes` ADD COLUMN `manual_progress` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishes_parent_id` ON `wishes` (`parent_id`)")
        db.execSQL(
            "ALTER TABLE `todos` ADD COLUMN `goal_id` INTEGER " +
                "REFERENCES `wishes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_goal_id` ON `todos` (`goal_id`)")
    }
}

/** Version 19: to-dos that come again (#30). None does yet, and none came next for another. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `recurrence` TEXT")
        db.execSQL(
            "ALTER TABLE `todos` ADD COLUMN `next_id` INTEGER " +
                "REFERENCES `todos`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_next_id` ON `todos` (`next_id`)")
    }
}

/** Version 20: routines under goals (#21), with what they aim at. Every routine is part of none and aims at nothing yet. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `routines` ADD COLUMN `goal_id` INTEGER " +
                "REFERENCES `wishes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `target` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routines_goal_id` ON `routines` (`goal_id`)")
    }
}

/** Version 21: notes (#22), in "Allgemein" or in tabs of their own. There are none yet. */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_tabs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, `tab_id` INTEGER, `changed_at` TEXT NOT NULL, " +
                "FOREIGN KEY(`tab_id`) REFERENCES `note_tabs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_tab_id` ON `notes` (`tab_id`)")
    }
}

/**
 * Version 22: folders of notes (#23), in a tab or in each other, and the order of what lies in them, or at the top of a
 * tab, "Allgemein" too. There are none yet; the notes are at the top of their tab, the one changed last first.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`tab_id` INTEGER, `parent_id` INTEGER, `position` INTEGER NOT NULL, `sort` TEXT NOT NULL, " +
                "`changed_at` TEXT NOT NULL, " +
                "FOREIGN KEY(`tab_id`) REFERENCES `note_tabs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`parent_id`) REFERENCES `note_folders`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_folders_tab_id` ON `note_folders` (`tab_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_folders_parent_id` ON `note_folders` (`parent_id`)")
        db.execSQL("ALTER TABLE `note_tabs` ADD COLUMN `sort` TEXT NOT NULL DEFAULT 'changed'")
        db.execSQL("CREATE TABLE IF NOT EXISTS `note_general_tab` (`id` INTEGER NOT NULL, `sort` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "ALTER TABLE `notes` ADD COLUMN `folder_id` INTEGER " +
                "REFERENCES `note_folders`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION",
        )
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folder_id` ON `notes` (`folder_id`)")
    }
}

/**
 * Version 23: notes and folders pinned (#24), notes on Heute, and notes linked to goals, to-dos and routines. None is
 * pinned or linked yet.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `note_folders` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `on_today` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_goals` (`note_id` INTEGER NOT NULL, `goal_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`note_id`, `goal_id`), " +
                "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`goal_id`) REFERENCES `wishes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_goals_goal_id` ON `note_goals` (`goal_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_todos` (`note_id` INTEGER NOT NULL, `todo_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`note_id`, `todo_id`), " +
                "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`todo_id`) REFERENCES `todos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_todos_todo_id` ON `note_todos` (`todo_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_routines` (`note_id` INTEGER NOT NULL, `routine_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`note_id`, `routine_id`), " +
                "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_routines_routine_id` ON `note_routines` (`routine_id`)")
    }
}

/** Version 24: notes have the shared tags too (SPEC.md §8). None has one yet. */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_tags` (`note_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`note_id`, `tag_id`), " +
                "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tags_tag_id` ON `note_tags` (`tag_id`)")
    }
}

/**
 * Version 25: the characters that print nothing and only get in the way, which saving leaves out since, are taken out of
 * what was saved before (#26): zero-width spaces, word joiners, byte order marks and soft hyphens, so that the search
 * finds it as it reads. Names of tags stay, as two can only differ in such characters if a backup brought them in.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val texts = mapOf(
            "notes" to listOf("title", "text"),
            "todos" to listOf("title", "description"),
            "routines" to listOf("name", "description"),
            "wishes" to listOf("title", "description", "period"),
            "note_tabs" to listOf("name"),
            "note_folders" to listOf("name"),
            "places" to listOf("name"),
            "checklist_items" to listOf("text"),
            "todo_checklist_items" to listOf("text"),
        )
        for ((table, columns) in texts) {
            val cleaned = columns.joinToString { column ->
                "`$column` = " + STRAY_CHARACTERS.fold("`$column`") { text, code -> "replace($text, char($code), '')" }
            }
            db.execSQL("UPDATE `$table` SET $cleaned")
        }
    }
}

/** The code points of what [MIGRATION_24_25] takes out, as InputText leaves them out on saving. */
private val STRAY_CHARACTERS = listOf(0x200B, 0x2060, 0xFEFF, 0x00AD)

/**
 * Version 26: a series that comes again can end (#34): a to-do's after a number of to-dos or on a date, counting which
 * of its series it is; a routine's on a date. None ends yet; a series under way counts on from its first to-do.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `ends_after` INTEGER")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `ends_on` TEXT")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `series_number` INTEGER NOT NULL DEFAULT 1")
        // A series already under way counts on from its first to-do, along the links from each one to the next.
        db.execSQL(
            """
            WITH RECURSIVE series(id, number) AS (
                SELECT id, 1 FROM todos WHERE id NOT IN (SELECT next_id FROM todos WHERE next_id IS NOT NULL)
                UNION ALL
                SELECT todos.next_id, series.number + 1 FROM series JOIN todos ON todos.id = series.id
                WHERE todos.next_id IS NOT NULL
            )
            UPDATE todos SET series_number = (SELECT number FROM series WHERE series.id = todos.id)
            WHERE id IN (SELECT id FROM series)
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `ends_on` TEXT")
    }
}

/** Version 27: a to-do can show on Heute some days before it is due (#35). None does yet: each only on its day. */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `shows_days_before` INTEGER")
    }
}

/**
 * Version 28: the household book (#44), its categories and bookings. None is there yet: the suggested categories come
 * with the first look at it.
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `is_other` INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bookings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amount_cents` INTEGER NOT NULL, `date` TEXT NOT NULL, `category_id` INTEGER NOT NULL, `description` TEXT NOT NULL, " +
                "FOREIGN KEY(`category_id`) REFERENCES `ledger_categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookings_category_id` ON `bookings` (`category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookings_date` ON `bookings` (`date`)")
    }
}

/**
 * Version 29: fixed bookings of the household book (SPEC.md §13, #45), and with each booking the one it was entered
 * from, if any. None is there yet.
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_bookings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amount_cents` INTEGER NOT NULL, `category_id` INTEGER NOT NULL, `description` TEXT NOT NULL, " +
                "`interval` TEXT NOT NULL, `day` INTEGER NOT NULL, `first_month` TEXT NOT NULL, `entered_through` TEXT, " +
                "FOREIGN KEY(`category_id`) REFERENCES `ledger_categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_bookings_category_id` ON `recurring_bookings` (`category_id`)")
        db.execSQL("ALTER TABLE `bookings` ADD COLUMN `recurring_id` INTEGER")
    }
}

/** Version 30: months of the household book closed by hand (SPEC.md §13, #46). None is yet: all are open. */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_closed_months` (`month` TEXT NOT NULL, PRIMARY KEY(`month`))")
    }
}

/**
 * Version 31: the fixed bookings skipped while their month was closed (SPEC.md §13, #46), to be entered by hand once it
 * is open again. None is yet.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_skipped` (`recurring_id` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "PRIMARY KEY(`recurring_id`, `date`), FOREIGN KEY(`recurring_id`) REFERENCES `recurring_bookings`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/**
 * Version 32: budgets of the household book (SPEC.md §13, #47), a monthly one for an expense category, and those a
 * month closed keeps. None is set yet; the months closed before keep none.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `ledger_categories` ADD COLUMN `budget_cents` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_closed_budgets` (`month` TEXT NOT NULL, `category_id` INTEGER NOT NULL, " +
                "`budget_cents` INTEGER NOT NULL, PRIMARY KEY(`month`, `category_id`), " +
                "FOREIGN KEY(`month`) REFERENCES `ledger_closed_months`(`month`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`category_id`) REFERENCES `ledger_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_closed_budgets_category_id` ON `ledger_closed_budgets` (`category_id`)")
    }
}

/** Version 33: a to-do's reminder can come as a full-screen alarm (SPEC.md §5, #36). None does yet. */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `full_screen` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Version 34: a to-do can wake besides when it is due (SPEC.md §5, #37), some days before or at a fixed moment, and
 * remembers whether it does so still. None wakes yet.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `wake_days_before` INTEGER")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `wake_date` TEXT")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `wake_time` TEXT")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `reminds_at_wake_time` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 35: a routine or to-do can show its name on the lock screen (SPEC.md §3, §9, #38). None does yet. */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `name_on_lock_screen` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `name_on_lock_screen` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Version 36: a routine or to-do can have a tone of its own (SPEC.md §3, #39). Each has the default yet. */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `tone` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `tone` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Version 37: routines can be coupled (SPEC.md §2, #41), one of a group done being enough for the day, and an
 * occurrence remembers the one whose being done skipped it. None is coupled yet.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `coupling` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routines_coupling` ON `routines` (`coupling`)")
        db.execSQL("ALTER TABLE `occurrences` ADD COLUMN `covered_by` INTEGER")
    }
}

/**
 * Version 38: a month of the household book can have an opening balance (SPEC.md §13, #50), set by hand or carried over
 * from the month before. None has one yet.
 */
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_opening_balances` (`month` TEXT NOT NULL, `cents` INTEGER NOT NULL, " +
                "`carried` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`month`))",
        )
    }
}

/**
 * Version 39: a fixed booking of the household book can end (SPEC.md §13, #52), after a number of bookings or on a
 * date. None ends yet.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring_bookings` ADD COLUMN `ends_after` INTEGER")
        db.execSQL("ALTER TABLE `recurring_bookings` ADD COLUMN `ends_on` TEXT")
    }
}

/**
 * Version 40: an expense category of the household book can have a budget of its own in a single month (SPEC.md §13,
 * #53), another amount or none. None has one yet.
 */
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_month_budgets` (`month` TEXT NOT NULL, `category_id` INTEGER NOT NULL, " +
                "`budget_cents` INTEGER, PRIMARY KEY(`month`, `category_id`), " +
                "FOREIGN KEY(`category_id`) REFERENCES `ledger_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_month_budgets_category_id` ON `ledger_month_budgets` (`category_id`)")
    }
}

/** Version 41: notes and folders can be archived (SPEC.md §7, #54). None is yet. */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `archived_at` TEXT")
        db.execSQL("ALTER TABLE `note_folders` ADD COLUMN `archived_at` TEXT")
    }
}

/**
 * Version 42: the household book can have a total budget (SPEC.md §13, #78), as a standard and of a month's own, and a
 * month closed keeps the amount its total budget came to. None has one yet.
 */
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_total_budget` (`id` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`cents` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_month_total_budgets` (`month` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`cents` INTEGER NOT NULL, PRIMARY KEY(`month`))",
        )
        db.execSQL("ALTER TABLE `ledger_closed_months` ADD COLUMN `total_budget_cents` INTEGER")
    }
}

/**
 * Version 43: the household book has Abos (SPEC.md §13, #79), contracts with the last day to cancel them, linked to a
 * fixed booking if wanted. None is there yet.
 */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subscriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`amount_cents` INTEGER, `interval` TEXT, `recurring_id` INTEGER, `note` TEXT NOT NULL, `cancel_by` TEXT, " +
                "`term_end` TEXT, `term_day` INTEGER, `notice_count` INTEGER, `notice_unit` TEXT, `renewal_months` INTEGER, " +
                "`lead_days` INTEGER NOT NULL, `runs_on` INTEGER NOT NULL DEFAULT 0, `ends_on` TEXT, " +
                "FOREIGN KEY(`recurring_id`) REFERENCES `recurring_bookings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subscriptions_recurring_id` ON `subscriptions` (`recurring_id`)")
    }
}

/**
 * Version 44: an Abo keeps from when its reminder counts (#80), so that one made or changed within its lead does not
 * notify for a time gone by. Those there already have none, and all their times count.
 */
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `reminds_from` TEXT")
    }
}

/**
 * Version 45: Sprachen (SPEC.md §15, #81): the languages, the teachers with the languages they teach, and the lessons
 * held, none of whose language or teacher can be deleted while it is there. None is there yet.
 */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `languages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`archived` INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `teachers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `archived` INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `teacher_languages` (`teacher_id` INTEGER NOT NULL, `language_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`teacher_id`, `language_id`), " +
                "FOREIGN KEY(`teacher_id`) REFERENCES `teachers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`language_id`) REFERENCES `languages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_teacher_languages_language_id` ON `teacher_languages` (`language_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lessons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, `language_id` INTEGER NOT NULL, `teacher_id` INTEGER NOT NULL, `price_cents` INTEGER, " +
                "`note` TEXT NOT NULL, " +
                "FOREIGN KEY(`language_id`) REFERENCES `languages`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`teacher_id`) REFERENCES `teachers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_language_id` ON `lessons` (`language_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_teacher_id` ON `lessons` (`teacher_id`)")
    }
}

/**
 * Version 46: the topics and exams of Sprachen (SPEC.md §15, #82): each of a language, a topic for an exam or none, and
 * which lessons treated which topic. None is there yet.
 */
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `language_id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `date` TEXT NOT NULL, `taken` INTEGER NOT NULL DEFAULT 0, `result` TEXT NOT NULL, " +
                "FOREIGN KEY(`language_id`) REFERENCES `languages`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_language_id` ON `exams` (`language_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `topics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `language_id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `exam_id` INTEGER, `done` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`language_id`) REFERENCES `languages`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`exam_id`) REFERENCES `exams`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_topics_language_id` ON `topics` (`language_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_topics_exam_id` ON `topics` (`exam_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lesson_topics` (`lesson_id` INTEGER NOT NULL, `topic_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`lesson_id`, `topic_id`), " +
                "FOREIGN KEY(`lesson_id`) REFERENCES `lessons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`topic_id`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_topics_topic_id` ON `lesson_topics` (`topic_id`)")
    }
}

/**
 * Version 47: the photos and PDFs attached to the lessons of Sprachen (SPEC.md §15, #83), each kept encrypted in a file
 * of its own, gone with its lesson. None is there yet.
 */
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lesson_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`lesson_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `file` TEXT NOT NULL, `bytes` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`lesson_id`) REFERENCES `lessons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_attachments_lesson_id` ON `lesson_attachments` (`lesson_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_attachments_file` ON `lesson_attachments` (`file`)")
    }
}
