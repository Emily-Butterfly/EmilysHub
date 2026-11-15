package de.emilyshub.backup

import de.emilyshub.data.HubData
import java.io.InputStream

/**
 * The ways out when the app's [data] cannot be opened (#100), none of which is taken on its own: a backup loaded into new
 * data with a new key, or a start as on a new installation, both with the old data laid aside, never deleted. Loading a
 * backup goes in two steps, as with [Backups]: [open] and [restore], or [cancel]; until restored, nothing is kept, and
 * whatever fails puts everything back as it was. [onStartedOver] runs once empty data are kept, for the reminders.
 */
class DataRescue(
    private val data: HubData,
    private val backups: Backups,
    private val onStartedOver: suspend () -> Unit = {},
) {
    /** Lays the data aside and opens the backup in [input] with its [password] into empty data; all as it was if that fails. */
    suspend fun open(input: () -> InputStream, password: String): Backup {
        data.undo()
        data.beginAnew()
        return try {
            backups.open(input, password)
        } catch (e: Exception) {
            data.undo()
            throw e
        }
    }

    /**
     * Restores [backup], opened with [open], as the backup screen does, with its Doku only with the [dokuPassword], and
     * keeps it; all as it was if that fails. A wrong Doku password changes nothing, and may be tried again.
     */
    suspend fun restore(backup: Backup, dokuPassword: String? = null) {
        try {
            backups.restore(backup, dokuPassword)
        } catch (e: BackupException) {
            if (e.problem != BackupProblem.WrongDokuPassword) data.undo()
            throw e
        } catch (e: Exception) {
            data.undo()
            throw e
        }
        data.keep()
    }

    /** Takes back a backup opened but not restored: everything is as it was. */
    suspend fun cancel() = data.undo()

    /** Lays the data aside and starts empty, as on a new installation: without a backup password or a folder for the nightly ones. */
    suspend fun startOver() {
        data.undo()
        data.beginAnew()
        data.keep()
        onStartedOver()
    }
}
