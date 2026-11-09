package de.emilyshub.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.emilyshub.reminders.DefaultTones
import de.emilyshub.reminders.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** The in-app design choice (SPEC.md §1): follow the system, or always light (Flieder) or dark (Lila). */
enum class Design(val stored: String) { System("system"), Light("light"), Dark("dark") }

/**
 * [remindersAsked]: whether the permissions reminders need were asked for already, which happens once.
 * [lookAroundEnabled]: whether the app looks around every 30 minutes while a to-do waits for a place, so that arriving
 * there is noticed without a WLAN too; on unless turned off. [tones]: the default tones of alarms and notifications.
 */
data class Settings(
    val design: Design,
    val appLockEnabled: Boolean,
    val remindersAsked: Boolean,
    val lookAroundEnabled: Boolean,
    val tones: DefaultTones = DefaultTones(),
)

/**
 * The settings every screen depends on, read from the encrypted database and then kept in memory.
 *
 * [dao] is only called on [io], since the first call opens the database. Stored values are
 * checked strictly: anything unknown counts as the default, and for the app lock that is "on".
 * After looking around is turned on or off, [onLookAroundChange] runs, for the reminders to follow, and after a default
 * tone changes, [onTonesChange], for the reminders' channels to follow.
 */
class AppSettings(
    private val dao: () -> SettingsDao,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onLookAroundChange: suspend () -> Unit = {},
    private val onTonesChange: suspend () -> Unit = {},
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
            appLockEnabled = isOn(appLock),
            remindersAsked = remindersAsked == YES,
            lookAroundEnabled = isOn(lookAround),
            tones = defaultTones(),
        ).also { current = it }
    }

    suspend fun setDesign(design: Design) =
        save(DESIGN, design.stored) { it.copy(design = design) }

    suspend fun setAppLockEnabled(enabled: Boolean) =
        save(APP_LOCK, if (enabled) ON else OFF) { it.copy(appLockEnabled = enabled) }

    suspend fun setRemindersAsked() =
        save(REMINDERS_ASKED, YES) { it.copy(remindersAsked = true) }

    suspend fun setLookAroundEnabled(enabled: Boolean) =
        save(LOOK_AROUND, if (enabled) ON else OFF, then = onLookAroundChange) { it.copy(lookAroundEnabled = enabled) }

    /** The default tone of full-screen alarms (SPEC.md §3), for those without one of their own. */
    suspend fun setAlarmTone(tone: Tone) =
        save(ALARM_TONE, tone.stored, then = onTonesChange) { it.copy(tones = it.tones.copy(alarm = tone)) }

    /** The default tone of notifications (SPEC.md §3), for those without one of their own. */
    suspend fun setNotificationTone(tone: Tone) =
        save(NOTIFICATION_TONE, tone.stored, then = onTonesChange) { it.copy(tones = it.tones.copy(notification = tone)) }

    /** The default tones, read anew from the database: the reminders ask while the app may be closed. */
    suspend fun defaultTones(): DefaultTones = withContext(io) {
        DefaultTones(alarm = Tone.of(dao().get(ALARM_TONE)), notification = Tone.of(dao().get(NOTIFICATION_TONE)))
    }

    /** Whether the app may look around, read anew from the database: the reminders ask while the app may be closed. */
    suspend fun mayLookAround(): Boolean = isOn(withContext(io) { dao().get(LOOK_AROUND) })

    /** On unless stored as off, as for the app lock and looking around. */
    private fun isOn(stored: String?) = stored != OFF

    /**
     * Writes first and only then shows the change, applied to whatever is current by then, and does what follows it
     * ([then]); all of it to its end, though the screen that asked is gone meanwhile, as when the app locks.
     */
    private suspend fun save(name: String, value: String, then: suspend () -> Unit = {}, change: (Settings) -> Settings) =
        withContext(NonCancellable) {
            checkNotNull(current) { "Settings can only change after load()" }
            withContext(io) { dao().put(Setting(name, value)) }
            current = current?.let(change)
            then()
        }

    private companion object {
        const val DESIGN = "design"
        const val APP_LOCK = "app-lock"
        const val ON = "on"
        const val OFF = "off"
        const val REMINDERS_ASKED = "reminders-asked"
        const val LOOK_AROUND = "look-around"
        const val YES = "yes"
        const val ALARM_TONE = "alarm-tone"
        const val NOTIFICATION_TONE = "notification-tone"
    }
}
