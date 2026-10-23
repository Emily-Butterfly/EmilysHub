package de.emilyshub

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The phone's clock, in the time zone the phone is in right now. Clock.systemDefaultZone() would keep
 * the zone it was created in, even after a flight.
 */
object DeviceClock : Clock() {
    override fun instant(): Instant = Instant.now()
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
}
