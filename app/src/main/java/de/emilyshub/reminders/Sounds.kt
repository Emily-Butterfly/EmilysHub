package de.emilyshub.reminders


/**
 * What sounds for a reminder (SPEC.md §3): its tone, or the default for its kind in the settings, as [defaults] has
 * them now, and the phone's own where a tone is missing from [tones]. The reminders ask while the app may be closed.
 */
class Sounds(
    private val tones: ToneLibrary,
    private val defaults: suspend () -> DefaultTones,
) {
    /** What a notification of [tone] plays once. */
    suspend fun notification(tone: Tone): Sound = soundOf(tone, defaults().notification, tones::exists)

    /** What a full-screen alarm of [tone] plays over and over. */
    suspend fun alarm(tone: Tone): Sound = soundOf(tone, defaults().alarm, tones::exists)

    /** What the full-screen alarm plays for [reminder]: by its tone while it is due, by the default once it is not. */
    suspend fun alarmOf(reminder: DueReminder?): Sound = alarm(reminder?.tone ?: Tone.Standard)

    /** The name of the tone at [uri], as Android's choice of tones shows it; none if it cannot be read. */
    fun title(uri: String): String? = tones.title(uri)
}
