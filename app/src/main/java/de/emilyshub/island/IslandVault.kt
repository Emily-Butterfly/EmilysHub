package de.emilyshub.island

/** What the backups need of the island (SPEC.md §10, §14): to take it along and put it back, never unlocking it. */
interface IslandVault {
    /**
     * Runs [block] with the island as it is now ([IslandSnapshot]), null while it has no password yet; its own files
     * listed there stay until [block] is done, to be read one by one as the backup is written.
     */
    suspend fun <T> reading(block: suspend (IslandSnapshot?) -> T): T

    /** Puts [snapshot] in place of the island, then runs [during]; if that fails, the island is put back as it was. */
    suspend fun <T> replacedBy(snapshot: IslandSnapshot, during: suspend () -> T): T

    /** Lets go of the files of a backup laid aside ([putAside]), to begin anew. */
    fun clearAside()

    /**
     * Lays [bytes] aside as the island's file [name] of a backup being opened, apart from its own, as they are, encrypted;
     * null if [name] is none an island's file can have.
     */
    fun putAside(name: String, bytes: ByteArray): IslandFile?
}
