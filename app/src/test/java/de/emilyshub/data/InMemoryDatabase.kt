package de.emilyshub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/**
 * The app's database schema without SQLCipher, which needs its native library: in memory, on
 * Robolectric's SQLite. Queries run right on the calling thread, so tests and Compose see every
 * change at once.
 */
fun inMemoryDatabase(): HubDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HubDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
