package de.emilyshub.island

import de.emilyshub.backup.BackupException
import de.emilyshub.backup.BackupProblem
import de.emilyshub.security.writeDurably
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

/**
 * The files the island lies in: its [keyFile], its [database] with the files SQLite keeps beside it, and the folder of
 * its [own] files; read for a backup ([snapshot]) and put in place of from one ([replaceWith]), both while the database
 * is closed or nothing writes to it. The own files of a backup being opened are laid aside meanwhile ([putAside]).
 */
internal class IslandFiles(private val keyFile: File, private val database: File, private val schema: Int) {
    private val wal = File("${database.path}-wal")
    private val sharedMemory = File("${database.path}-shm")
    private val journal = File("${database.path}-journal")

    /** The island's own files ([IslandFileStore]). */
    val own = IslandOwnFiles(File("${database.path}-files"))

    /** Where the own files of a backup being opened lie until it is restored. */
    private val aside = File("${database.path}-files.aside")

    /** All that is kept aside while a snapshot is put in place, to come back should that fail. */
    private val kept = listOf(keyFile, database, wal, journal, own.dir)

    /**
     * The database as it lies, with its write-ahead log if there is one, its own files listed, and [keys]; null if the
     * database is larger than [maxBytes].
     */
    fun snapshot(keys: IslandKeys, maxBytes: Int): IslandSnapshot? {
        val log = wal.takeIf { it.length() > 0 }
        if (database.length() + (log?.length() ?: 0) > maxBytes) return null
        val bytes = if (database.exists()) database.readBytes() else ByteArray(0)
        return IslandSnapshot(schema, keys, bytes, log?.readBytes(), own.list())
    }

    /** Lets go of all laid aside. */
    fun clearAside() {
        aside.deleteRecursively()
    }

    /** Lays [bytes] aside as the own file [name] of a backup being opened; null if [name] is none such a file can have. */
    fun putAside(name: String, bytes: ByteArray): IslandFile? {
        if (!IslandOwnFiles.isName(name)) return null
        aside.mkdirs()
        val file = File(aside, name)
        writeDurably(file, bytes)
        return IslandFile(name, bytes.size.toLong(), file)
    }

    /**
     * Puts [snapshot] in place of the files there are, which are kept aside until the [Replacement] is [Replacement.done]
     * or [Replacement.undo]ne. Failing, it leaves the files as they were. Its own files are moved in from where they lie,
     * laid aside as the backup was opened: fails as damaged if they are no longer all there, as another backup was
     * opened since.
     */
    fun replaceWith(snapshot: IslandSnapshot): Replacement {
        if (snapshot.files.any { it.at.length() != it.bytes || !it.at.isFile }) throw BackupException(BackupProblem.Damaged)
        database.parentFile?.mkdirs()
        val staged = mutableListOf<Pair<File, File>>()
        val aside = mutableListOf<File>()
        try {
            listOf(database to snapshot.database, wal to snapshot.wal, keyFile to snapshot.sealedKeys).forEach { (file, bytes) ->
                if (bytes != null) staged += file to restoring(file).also { writeDurably(it, bytes) }
            }
            val ownStaged = restoring(own.dir).also { it.deleteRecursively(); it.mkdirs() }
            staged += own.dir to ownStaged
            snapshot.files.forEach { move(it.at, File(ownStaged, it.name)) }
            kept.filter(File::exists).forEach { file ->
                move(file, old(file))
                aside += file
            }
            // Rebuilt by SQLite from the log; one of the files before would not fit it.
            sharedMemory.delete()
            staged.forEach { (file, from) -> move(from, file) }
        } catch (e: Exception) {
            try {
                putBack(aside)
            } catch (failed: Exception) {
                e.addSuppressed(failed)
            }
            staged.forEach { (_, from) -> from.deleteRecursively() }
            throw e
        }
        return Replacement(aside)
    }

    /**
     * Where the process left a replacement it did not finish, killed meanwhile, at its next start: one cut off while the
     * files were put in place is undone, and one in place is kept, the files before deleted, as they hold health data
     * that is not to linger. Either way the key file and the database fit each other again.
     */
    fun recover() {
        val staged = kept.map(::restoring).filter(File::exists)
        val aside = kept.filter { old(it).exists() }
        if (staged.isNotEmpty() && aside.isNotEmpty()) putBack(aside) else aside.forEach { old(it).deleteRecursively() }
        staged.forEach(File::deleteRecursively)
        clearAside()
    }

    /** A snapshot put in place, the files before kept aside until it is [done] or [undo]ne. */
    inner class Replacement internal constructor(private val aside: List<File>) {
        /** The files before are gone for good. */
        fun done() = aside.forEach { old(it).deleteRecursively() }

        /** The files before are back in place. */
        fun undo() = putBack(aside)
    }

    /** The files kept [aside] back in place of those put there; those that were not there before deleted. */
    private fun putBack(aside: List<File>) {
        kept.filter { it !in aside }.forEach(File::deleteRecursively)
        sharedMemory.delete()
        aside.forEach { move(old(it), it) }
    }

    /** Atomically, so that a file is either where it was or where it goes. */
    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), ATOMIC_MOVE)
    }

    /** Where [file] is kept aside while a snapshot is put in place. */
    private fun old(file: File) = File("${file.path}.old")

    /** Where the snapshot's part for [file] is written before it is put in place. */
    private fun restoring(file: File) = File("${file.path}.restore")
}
