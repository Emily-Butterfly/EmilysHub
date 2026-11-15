package de.emilyshub.tracking

import android.content.Context
import androidx.room.Room
import de.emilyshub.island.SECURE_DELETE

/**
 * `tracking.db` in a file, so that what is in it outlives locking, but with Android's own SQLite, as SQLCipher's needs
 * its native library: the key is checked before, and here takes no part.
 */
fun testTrackingDatabase(context: Context): TrackingDatabase =
    Room.databaseBuilder(context, TrackingDatabase::class.java, TrackingDatabase.NAME)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .addMigrations(*TrackingDatabase.MIGRATIONS.toTypedArray())
        .addCallback(SECURE_DELETE)
        .build()
