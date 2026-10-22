package de.emilyshub.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** The in-app design choice (SPEC.md §1): follow the system, or always light (Flieder) or dark (Lila). */
enum class Design(val stored: String) { System("system"), Light("hell"), Dark("dunkel") }

data class Settings(val design: Design, val appLockEnabled: Boolean)

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
        val (design, appLock) = withContext(io) { dao().get(DESIGN) to dao().get(APP_LOCK) }
        return Settings(
            design = Design.entries.firstOrNull { it.stored == design } ?: Design.System,
            appLockEnabled = appLock != OFF,
        ).also { current = it }
    }

    suspend fun setDesign(design: Design) =
        save(DESIGN, design.stored) { it.copy(design = design) }

    suspend fun setAppLockEnabled(enabled: Boolean) =
        save(APP_LOCK, if (enabled) ON else OFF) { it.copy(appLockEnabled = enabled) }

    /** Writes first and only then shows the change, applied to whatever is current by then. */
    private suspend fun save(name: String, value: String, change: (Settings) -> Settings) {
        checkNotNull(current) { "Settings can only change after load()" }
        withContext(io) { dao().put(Setting(name, value)) }
        current = change(current!!)
    }

    private companion object {
        const val DESIGN = "design"
        const val APP_LOCK = "app-sperre"
        const val ON = "an"
        const val OFF = "aus"
    }
}
