package de.emilyshub.doku

import android.content.Context
import androidx.room.Room

/**
 * `doku.db` in a file, so that what is in it outlives locking, but with Android's own SQLite, as SQLCipher's needs its
 * native library: the key is checked by [DokuKeys] before, and here takes no part.
 */
fun testDokuDatabase(context: Context): DokuDatabase =
    Room.databaseBuilder(context, DokuDatabase::class.java, DokuDatabase.NAME)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
