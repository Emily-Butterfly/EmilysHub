package de.emilyshub.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** One app setting, stored under its [name]. */
@Entity(tableName = "settings")
data class Setting(@PrimaryKey val name: String, val value: String)

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE name = :name")
    suspend fun get(name: String): String?

    @Upsert
    suspend fun put(setting: Setting)
}
