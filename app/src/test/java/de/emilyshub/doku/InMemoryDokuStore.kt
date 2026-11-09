package de.emilyshub.doku

import android.content.Context
import androidx.room.Room

/**
 * The Doku's store for the tests of its modules: its database in memory, with Android's own SQLite, without the vault's
 * key, password or fingerprint; open until [locked], when reads give nothing and writes do nothing, as the Doku's.
 */
class InMemoryDokuStore(context: Context) : DokuStore {
    private val database: DokuDatabase = Room.inMemoryDatabaseBuilder(context, DokuDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()

    /** Whether it is locked, as the Doku would be. */
    var locked = false

    override suspend fun <T> withDatabase(block: suspend (DokuDatabase) -> T): T? = if (locked) null else block(database)

    override suspend fun <T> changing(block: suspend (DokuDatabase) -> T): T? = withDatabase(block)

    fun close() = database.close()
}
