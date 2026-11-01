package de.emilyshub.reminders

/**
 * Where the reminders are shown, the seam between [Reminders] and Android: [ReminderNotifications] on the phone, and an
 * in-memory one in the tests. One reminder at most for each thing it is about, which [show] puts up or changes.
 */
interface ReminderScreen {

    /** Shows the [reminder] offering [offering], popping up with a sound if it [alert]s, quietly otherwise. */
    suspend fun show(reminder: DueReminder, alert: Boolean, offering: Offering)

    /** What the reminder about [about] offers, as it is on the screen; its actions if it is not. */
    fun offering(about: Alarmed): Offering

    /** Takes the reminder about [about] down. */
    fun cancel(about: Alarmed)

    /** What the reminders on the screen are about. */
    fun shown(): List<Alarmed>

    /** Takes away the channels of tones no longer used, those [inUse] aside. */
    fun tidyChannels(inUse: Collection<Sound>)

    /** Pops the reminder to try out up (SPEC.md §11). */
    fun showProbe()
}

/** What a reminder on the screen offers (SPEC.md §3). */
enum class Offering {
    /** Its actions: "Erledigt · Später · Überspringen", or "Erledigt · Später" for a to-do. */
    Actions,

    /** After "Später": when to come back. */
    PutOff,

    /** After "Erledigt" on a joint reminder: which one it was. */
    Done,
}
