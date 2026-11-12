package de.emilyshub.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The reminder to cancel an Abo (SPEC.md §13, #80) in the term that holds: of the Abo [subscriptionId] by [name], to be
 * cancelled by [lastDay]. It is on Heute under "Demnächst" from [from], the first day of its lead, to [lastDay], and
 * notifies at [REMIND_AT] on both of those days; once [lastDay] is over ([ends]), it goes.
 */
data class SubscriptionReminder(
    val subscriptionId: Long,
    val name: String,
    val lastDay: LocalDate,
    val from: LocalDate,
    val ends: Instant,
) {
    /** When it notifies, in time order: on the first day of its lead, and again on its last day, once if they are one. */
    fun moments(zone: ZoneId): List<Instant> = listOf(from, lastDay).distinct().map { it.atTime(REMIND_AT).atZone(zone).toInstant() }

    /** Whether it is on Heute of [date]: from the first day of its lead to its last day. */
    fun showsOn(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(lastDay)

    companion object {
        /** The time of day an Abo notifies at (#80): fixed. */
        val REMIND_AT: LocalTime = LocalTime.of(21, 0)
    }
}

/**
 * The reminder of the term that holds on [today], in [zone]: none while it does not remind, cancelled, let run on past
 * its single date, or with that gone by.
 */
fun Subscription.reminderOn(today: LocalDate, zone: ZoneId): SubscriptionReminder? {
    val due = statusOn(today) as? SubscriptionStatus.Due ?: return null
    return SubscriptionReminder(
        subscriptionId = id,
        name = name,
        lastDay = due.lastDay,
        from = due.lastDay.minusDays(leadDays.toLong()),
        ends = due.lastDay.plusDays(1).atStartOfDay(zone).toInstant(),
    )
}

/** Whether its reminder of [today]'s term has come by [now] and is not over: from its first moment until it [ends]. */
fun SubscriptionReminder.dueAt(now: Instant, zone: ZoneId): Boolean = !now.isBefore(moments(zone).first()) && now.isBefore(ends)

/**
 * The reminder of the Abo that comes due after [after] and up to [until], in [zone]: of the term that holds at [until],
 * whose last day is not over; none otherwise. What came due while the phone was off still comes so, but never once its
 * last day is over.
 */
fun Subscription.reminderDue(after: Instant, until: Instant, zone: ZoneId): SubscriptionReminder? {
    val reminder = reminderOn(until.atZone(zone).toLocalDate(), zone) ?: return null
    if (!until.isBefore(reminder.ends)) return null
    return reminder.takeIf { r -> r.moments(zone).any { it.isAfter(after) && !it.isAfter(until) } }
}

/**
 * When the Abo is to notify, or its reminder to go, next after [after], in [zone]: in the term that holds then, or the
 * one after it once that is past; none while it does not remind.
 */
fun Subscription.nextAlarm(after: Instant, zone: ZoneId): Instant? {
    var day = after.atZone(zone).toLocalDate()
    // The term that holds, then the one after it, which is all after [after].
    repeat(2) {
        val reminder = reminderOn(day, zone) ?: return null
        (reminder.moments(zone) + reminder.ends).firstOrNull { it.isAfter(after) }?.let { return it }
        day = reminder.lastDay.plusDays(1)
    }
    return null
}
