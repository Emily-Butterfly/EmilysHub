package de.emilyshub.reminders

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One of the choices "Später" offers (SPEC.md §3): back in some minutes ([By]), or, for a to-do only, at a fixed time on
 * the clock, [Tonight] at 20:00 or [TomorrowMorning] at 08:00 (#98). Each is [until] a moment, which is what is kept, so
 * that it outlasts a restart, and a change of time zone leaves it as it was.
 */
sealed interface PutOffChoice {
    /** What tells it apart in the intent of a notification's action: the minutes, or its name. */
    val key: String

    /** When it comes back, chosen at [now] in [zone], the phone's. */
    fun until(now: Instant, zone: ZoneId): Instant

    /** Back after [duration], however the clock is set meanwhile. */
    data class By(val duration: Duration) : PutOffChoice {
        override val key get() = duration.toMinutes().toString()

        override fun until(now: Instant, zone: ZoneId): Instant = now + duration
    }

    /** "Heute Abend": back at [EVENING] today. Offered only before [EVENING_OFFERED_UNTIL]. */
    data object Tonight : PutOffChoice {
        override val key get() = "tonight"

        override fun until(now: Instant, zone: ZoneId): Instant = at(now.atZone(zone).toLocalDate(), EVENING, zone)
    }

    /**
     * "Morgen früh": back at [MORNING] on the next day; at night, before [NIGHT_ENDS], the morning of the same day, as
     * that is the one still ahead.
     */
    data object TomorrowMorning : PutOffChoice {
        override val key get() = "morning"

        override fun until(now: Instant, zone: ZoneId): Instant {
            val local = now.atZone(zone)
            val day = if (local.toLocalTime().isBefore(NIGHT_ENDS)) local.toLocalDate() else local.toLocalDate().plusDays(1)
            return at(day, MORNING, zone)
        }
    }

    companion object {
        private val TEN_MINUTES = By(Duration.ofMinutes(10))
        private val HALF_AN_HOUR = By(Duration.ofMinutes(30))
        private val AN_HOUR = By(Duration.ofMinutes(60))

        /** What "Später" offers a routine's reminder: 10, 30 and 60 minutes, as long as its time window lasts. */
        val MINUTES: List<By> = listOf(TEN_MINUTES, HALF_AN_HOUR, AN_HOUR)

        /** When "Heute Abend" comes back. */
        val EVENING: LocalTime = LocalTime.of(20, 0)

        /** From when on "Heute Abend" is no longer offered: too close to it to be worth it. */
        val EVENING_OFFERED_UNTIL: LocalTime = LocalTime.of(19, 0)

        /** When "Morgen früh" comes back. */
        val MORNING: LocalTime = LocalTime.of(8, 0)

        /** Until when it is still night, so that "Morgen früh" is the morning of the same day. */
        val NIGHT_ENDS: LocalTime = LocalTime.of(4, 0)

        /** What a to-do's full-screen alarm offers at [now] in [zone]: the minutes, "Heute Abend" until 19:00, "Morgen früh". */
        fun forTodo(now: Instant, zone: ZoneId): List<PutOffChoice> =
            MINUTES + listOfNotNull(Tonight.takeIf { stillOffered(now, zone) }, TomorrowMorning)

        /**
         * What a to-do's notification offers at [now] in [zone], as Android shows three actions at most: 30 minutes,
         * "Heute Abend", or 1 hour once that is no longer offered, and "Morgen früh".
         */
        fun forTodoNotification(now: Instant, zone: ZoneId): List<PutOffChoice> =
            listOf(HALF_AN_HOUR, if (stillOffered(now, zone)) Tonight else AN_HOUR, TomorrowMorning)

        /** The choice [key] stands for; none for anything else. Whether it is offered is another question. */
        fun of(key: String): PutOffChoice? = when (key) {
            Tonight.key -> Tonight
            TomorrowMorning.key -> TomorrowMorning
            else -> key.takeIf { it.length in 1..MAX_DIGITS && it.all(Char::isDigit) }?.toLong()?.let { By(Duration.ofMinutes(it)) }
        }

        /** More than any choice has, and few enough to be read as minutes. */
        private const val MAX_DIGITS = 4

        private fun stillOffered(now: Instant, zone: ZoneId) = now.atZone(zone).toLocalTime().isBefore(EVENING_OFFERED_UNTIL)

        /** [time] on [day] in [zone]; in a gap of a change to summer time, as the clock reads after it. */
        private fun at(day: LocalDate, time: LocalTime, zone: ZoneId): Instant = day.atTime(time).atZone(zone).toInstant()
    }
}
