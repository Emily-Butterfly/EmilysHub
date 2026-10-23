package de.emilyshub

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** A clock that tests set and move by hand, in a time zone they can change as if the phone travelled. */
class TestClock(var now: Instant, var timeZone: ZoneId) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = timeZone
    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
}
