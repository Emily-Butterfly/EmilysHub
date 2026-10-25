package de.emilyshub.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** The in-app design choice (SPEC.md §1): follow the system, or always light (Flieder) or dark (Lila). */
enum class Design(val stored: String) { System("system"), Light("light"), Dark("dark") }

/**
 * [remindersAsked]: whether the permissions reminders need were asked for already, which happens once. [lookAround]:
 * whether the app looks around every 30 minutes while a to-do waits for a place (SPEC.md §4), on unless turned off.
 */
data class Settings(val design: Design, val appLockEnabled: Boolean, val remindersAsked: Boolean, val lookAround: Boolean)

/**
 * The settings every screen depends on, read from the encrypted database and then kept in memory.
 *
 * [dao] is only called on [io], since the first call opens the database. Stored values are
 * checked strictly: anything unknown counts as the default, and for the app lock that is "on".
 */
class AppSettings(
    private val dao: () -> SettingsDao,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    /** Compose state: null until [load] has finished. */
    var current: Settings? by mutableStateOf(null)
        private set

    suspend fun load(): Settings {
        val (design, appLock, remindersAsked, lookAround) = withContext(io) {
            listOf(DESIGN, APP_LOCK, REMINDERS_ASKED, LOOK_AROUND).map { dao().get(it) }
        }
        return Settings(
            design = Design.entries.firstOrNull { it.stored == design } ?: Design.System,
            appLockEnabled = appLock != OFF,
            remindersAsked = remindersAsked == YES,
            lookAround = lookAround != OFF,
        ).also { current = it }
    }

    suspend fun setDesign(design: Design) =
        save(DESIGN, design.stored) { it.copy(design = design) }

    suspend fun setAppLockEnabled(enabled: Boolean) =
        save(APP_LOCK, if (enabled) ON else OFF) { it.copy(appLockEnabled = enabled) }

    suspend fun setRemindersAsked() =
        save(REMINDERS_ASKED, YES) { it.copy(remindersAsked = true) }

    suspend fun setLookAround(on: Boolean) =
        save(LOOK_AROUND, if (on) ON else OFF) { it.copy(lookAround = on) }

    /** Whether the app may look around, read anew from the database: the reminders ask while the app may be closed. */
    suspend fun lookAround(): Boolean = withContext(io) { dao().get(LOOK_AROUND) } != OFF

    /** Writes first and only then shows the change, applied to whatever is current by then. */
    private suspend fun save(name: String, value: String, change: (Settings) -> Settings) {
        checkNotNull(current) { "Settings can only change after load()" }
        withContext(io) { dao().put(Setting(name, value)) }
        current = current?.let(change)
    }

    private companion object {
        const val DESIGN = "design"
        const val APP_LOCK = "app-lock"
        const val ON = "on"
        const val OFF = "off"
        const val REMINDERS_ASKED = "reminders-asked"
        const val LOOK_AROUND = "look-around"
        const val YES = "yes"
    }
}
