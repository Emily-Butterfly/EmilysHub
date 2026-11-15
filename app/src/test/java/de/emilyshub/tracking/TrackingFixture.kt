package de.emilyshub.tracking

import android.content.Context
import de.emilyshub.doku.FakeFingerprint
import de.emilyshub.island.IslandClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Tracking set up and open over a database in a file, with a clock the test sets, for the tests of its modules. */
class TrackingFixture(private val context: Context, now: String = "2026-11-15T10:00:00Z") {
    var clock: Clock = Clock.fixed(Instant.parse(now), ZoneId.of("Europe/Berlin"))

    /** Moves the clock to [now], an instant in UTC. */
    fun at(now: String) {
        clock = Clock.fixed(Instant.parse(now), ZoneId.of("Europe/Berlin"))
    }

    private val ticking = object : Clock() {
        override fun getZone(): ZoneId = clock.zone
        override fun withZone(zone: ZoneId): Clock = clock.withZone(zone)
        override fun instant(): Instant = clock.instant()
    }

    val tracking = Tracking(
        keyFile = File(context.noBackupFilesDir, "tracking-fixture.key"),
        databaseFile = context.getDatabasePath(TrackingDatabase.NAME),
        schema = TrackingDatabase.VERSION,
        fingerprint = FakeFingerprint(),
        openDatabase = { testTrackingDatabase(context) },
        clipboard = IslandClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    ).also {
        runBlocking {
            it.load()
            it.setUp("tracking-geheim")
        }
    }

    val symptoms = TrackingSymptoms(tracking)
    val entries = TrackingEntries(tracking, symptoms, ticking)
    val medications = TrackingMedications(tracking, ticking)
    val baseData = TrackingBaseData(tracking)
    val health = FakeHealthSource()
    val garmin = TrackingGarmin(tracking, health, ticking)

    /** Locks Tracking and deletes its files. */
    fun close() {
        tracking.lock()
        context.deleteDatabase(TrackingDatabase.NAME)
        File(context.noBackupFilesDir, "tracking-fixture.key").delete()
    }
}
