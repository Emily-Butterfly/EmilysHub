package de.emilyshub.backup

/**
 * What the backups need of the lessons' attachments (SPEC.md §10, §15), which lie in files of their own beside the
 * database: to read them as a backup is made, to lay those of a backup aside as it is opened, and to put those in place
 * of the app's own as it is restored. Blocking but [steady]: call it off the main thread.
 */
interface AttachmentVault {
    /** Runs [block] while no attachment is taken in or deleted, so that the table and the files agree throughout. */
    suspend fun <T> steady(block: suspend () -> T): T

    /** The attachment kept under [file], decrypted; none if there is none. */
    fun read(file: String): ByteArray?

    /** Whether [file] is a name an attachment can have. */
    fun isName(file: String): Boolean

    /** Lets go of all laid aside, to begin anew. */
    fun clearAside()

    /** Lays [bytes] aside as the attachment [file] of a backup being opened, apart from the app's own; encrypted. */
    fun putAside(file: String, bytes: ByteArray)

    /**
     * Makes the attachments those laid aside under [files]: the app's own given up, but for those still being imported,
     * and those laid aside put in their place. Only within [steady].
     */
    fun takeAside(files: Set<String>)
}
