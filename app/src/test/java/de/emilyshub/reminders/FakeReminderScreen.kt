package de.emilyshub.reminders

/**
 * Stands in for Android's notifications at the [ReminderScreen] seam: what is on the screen, by what it is about, as it
 * was shown last, and what popped up with a sound.
 */
class FakeReminderScreen : ReminderScreen {

    /** A reminder as it is on the screen: what it says, and what it offers. */
    data class Shown(val reminder: DueReminder, val offering: Offering)

    /** What is on the screen now, by what it is about. */
    val onScreen = mutableMapOf<Alarmed, Shown>()

    /** What popped up with a sound, in order. */
    val alerted = mutableListOf<Alarmed>()

    /** How often the reminder to try out popped up. */
    var probes = 0

    override suspend fun show(reminder: DueReminder, alert: Boolean, offering: Offering) {
        onScreen[reminder.about] = Shown(reminder, offering)
        if (alert) alerted += reminder.about
    }

    override fun offering(about: Alarmed): Offering = onScreen[about]?.offering ?: Offering.Actions

    override fun cancel(about: Alarmed) {
        onScreen.remove(about)
    }

    override fun shown(): List<Alarmed> = onScreen.keys.toList()

    override fun tidyChannels(inUse: Collection<Sound>) = Unit

    override fun showProbe() {
        probes++
    }
}
