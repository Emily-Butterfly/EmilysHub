package de.emilyshub

import de.emilyshub.backup.Backups
import de.emilyshub.data.AppSettings
import de.emilyshub.data.InMemorySettingsDao
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.FakeLocation
import de.emilyshub.places.FakeWifi
import de.emilyshub.places.Locator
import de.emilyshub.places.Places
import de.emilyshub.reminders.FakeRinger
import de.emilyshub.reminders.Reminders
import de.emilyshub.routines.Routines
import de.emilyshub.security.FakeAuthenticator
import de.emilyshub.tags.Tags
import de.emilyshub.todos.Todos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import javax.crypto.KeyGenerator

/**
 * The app as every Robolectric test sees it (set in robolectric.properties): settings in memory and
 * the database schema in memory, both read and written on the spot, a clock the test sets, work in the
 * background done on the spot too, a fake unlock prompt, a full-screen alarm that rings without a sound, a WLAN
 * the test connects to, a position the test sets, and backups with a key derived quickly.
 */
class TestApplication : EmilysHubApplication() {
    val settingsDao = InMemorySettingsDao()
    override val settings = AppSettings({ settingsDao }, Dispatchers.Unconfined, onLookAroundChange = ::updateReminders)
    override val authenticator = FakeAuthenticator()
    override val ringer = FakeRinger()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    /** In memory, open to the tests, such as for the history of days gone by. */
    val testDatabase by lazy { inMemoryDatabase() }
    override val routines: Routines = Routines({ testDatabase }, clock, Dispatchers.Unconfined, onChange = ::updateReminders)
    override val tags = Tags({ testDatabase }, Dispatchers.Unconfined)
    override val todos = Todos({ testDatabase }, clock, Dispatchers.Unconfined, onChange = ::updateReminders)
    override val places = Places({ testDatabase }, Dispatchers.Unconfined, onChange = ::updateReminders)
    override val wifi = FakeWifi()
    override val location = FakeLocation()
    override val locator = Locator(wifi, location, places)
    override val reminders: Reminders by lazy {
        Reminders(this, routines, todos, clock, locator::whereabouts, settings::mayLookAround) { settingsDao }
    }
    override val background = CoroutineScope(Dispatchers.Unconfined)

    /** Stands in for the Android Keystore key, which only exists on a device. */
    private val backupWrappingKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override val backups = Backups(
        database = { testDatabase },
        settings = { settingsDao },
        wrappingKey = { backupWrappingKey },
        clock = clock,
        io = Dispatchers.Unconfined,
        // Enough for the tests, which would take seconds with the real count.
        iterations = 1_000,
        onRestore = ::updateReminders,
    )

    /** Whether Android lets the app fill the screen with a reminder ([ShadowFullScreenIntents]); not until a test says. */
    var fullScreenAlarmsAllowed = false
}
