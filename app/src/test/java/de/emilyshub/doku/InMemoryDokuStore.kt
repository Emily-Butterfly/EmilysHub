package de.emilyshub.doku

import android.content.Context
import androidx.room.Room
import de.emilyshub.island.IslandFileStore
import de.emilyshub.island.IslandOwnFiles
import java.io.File

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

    /** Files under a key of the tests', as the island's own are. */
    private val files = IslandOwnFiles(File(context.cacheDir, "doku-files")).store(ByteArray(32))

    override suspend fun <T> withFiles(block: suspend (DokuDatabase, IslandFileStore) -> T): T? = if (locked) null else block(database, files)

    override suspend fun <T> changingWithFiles(block: suspend (DokuDatabase, IslandFileStore) -> T): T? = withFiles(block)

    fun close() = database.close()
}
