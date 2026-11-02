package de.emilyshub.reminders

import androidx.room.ColumnInfo
import java.io.Serializable

/**
 * How a routine or to-do reminds (SPEC.md §3), the same for both: as a [fullScreen] alarm, like an alarm clock, or a
 * notification; whether the lock screen shows its name and when it is due ([nameOnLockScreen]), never more; and the
 * [tone] its reminders and alarm play, the default in the settings unless chosen. Embedded in both entities under
 * the same columns, carried by their drafts and reminders, and edited in one section; a new way of reminding is a field
 * here. Serializable, so that the editors keep it.
 */
data class RemindingStyle(
    @ColumnInfo(name = "full_screen", defaultValue = "0") val fullScreen: Boolean = false,
    @ColumnInfo(name = "name_on_lock_screen", defaultValue = "0") val nameOnLockScreen: Boolean = false,
    @ColumnInfo(defaultValue = "''") val tone: Tone = Tone.Standard,
) : Serializable
