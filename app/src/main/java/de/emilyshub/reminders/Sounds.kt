package de.emilyshub.reminders

import de.emilyshub.routines.Routines
import de.emilyshub.todos.Todos
import kotlinx.coroutines.flow.first

/**
 * What sounds for a reminder (SPEC.md §3): its tone, or the default for its kind in the settings, as [defaults] has
 * them now, and the phone's own where a tone is missing from [tones]. The reminders ask while the app may be closed.
 */
class Sounds(
    private val tones: ToneLibrary,
    private val defaults: suspend () -> DefaultTones,
    private val routines: Routines,
    private val todos: Todos,
) {
    /** What a notification of [tone] plays once. */
    suspend fun notification(tone: Tone): Sound = soundOf(tone, defaults().notification, tones::exists)

    /** What a full-screen alarm of [tone] plays over and over. */
    suspend fun alarm(tone: Tone): Sound = soundOf(tone, defaults().alarm, tones::exists)

    /** What the full-screen alarm plays for [alarmed]: by its tone while it is due, by the default otherwise. */
    suspend fun alarmOf(alarmed: Alarmed): Sound {
        val tone = when (alarmed) {
            is Alarmed.Occurrence -> routines.reminder(alarmed.id).first()?.tone
            is Alarmed.Todo -> todos.reminder(alarmed.id).first()?.tone
        }
        return alarm(tone ?: Tone.Standard)
    }

    /** The name of the tone at [uri], as Android's choice of tones shows it; none if it cannot be read. */
    fun title(uri: String): String? = tones.title(uri)
}
