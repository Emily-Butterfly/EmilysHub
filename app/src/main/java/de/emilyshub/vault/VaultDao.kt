package de.emilyshub.vault

import androidx.room.Dao
import androidx.room.Query

@Dao
interface VaultDao {
    /** The files of the Ablage's owners: the Anhänge of the Sessions and the photos of the Gründe. */
    @Query("SELECT file FROM lesson_attachments UNION ALL SELECT photo FROM reasons WHERE photo IS NOT NULL")
    suspend fun owned(): List<String>
}
