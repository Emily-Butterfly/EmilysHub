package de.emilyshub.reminders

import android.content.Context
import android.media.RingtoneManager
import androidx.core.net.toUri
import java.io.Serializable

/**
 * The tone chosen for the reminders of a routine or to-do (SPEC.md §3), for its full-screen alarm and its notification
 * alike, or as one of the defaults in the settings. Kept as its [stored] text.
 */
sealed interface Tone : Serializable {
    /** For a routine or to-do, the default in the settings; as a default, the phone's own. */
    data object Standard : Tone

    /** No sound: the alarm only vibrates, and the notification pops up quietly. */
    data object Silent : Tone

    /** One of the phone's tones, by the address Android's choice of tones gives it. */
    data class Of(val uri: String) : Tone

    /** As it is stored: the address, "silent", or nothing for the default. */
    val stored: String
        get() = when (this) {
            Standard -> ""
            Silent -> SILENT
            is Of -> uri
        }

    companion object {
        /** The one [stored]; anything but a content address the phone's tones have counts as the default (SPEC.md §9). */
        fun of(stored: String?): Tone = when {
            stored == SILENT -> Silent
            stored != null && stored.startsWith(CONTENT) -> Of(stored)
            else -> Standard
        }

        private const val SILENT = "silent"
        private const val CONTENT = "content://"
    }
}

/** The defaults in the settings (SPEC.md §3): one for full-screen alarms and one for notifications, at first the phone's own. */
data class DefaultTones(val alarm: Tone = Tone.Standard, val notification: Tone = Tone.Standard)

/** What sounds in the end: the phone's own tone of the kind, nothing, or the tone at [Of.uri]. */
sealed interface Sound {
    data object PhoneDefault : Sound

    data object Silent : Sound

    data class Of(val uri: String) : Sound
}

/**
 * What sounds for [tone]: the tone itself, or [default] for the default, and the phone's own for a default at its
 * default. A tone that no longer [exists], deleted or left behind on another phone, falls back the same way.
 */
fun soundOf(tone: Tone, default: Tone, exists: (String) -> Boolean): Sound {
    fun resolve(tone: Tone, otherwise: () -> Sound): Sound = when (tone) {
        Tone.Standard -> otherwise()
        Tone.Silent -> Sound.Silent
        is Tone.Of -> if (exists(tone.uri)) Sound.Of(tone.uri) else otherwise()
    }
    return resolve(tone) { resolve(default) { Sound.PhoneDefault } }
}

/** The tones on the phone: whether one is still there, and its name, as Android's choice of tones shows it. */
interface ToneLibrary {
    fun exists(uri: String): Boolean

    /** Its name; none if it cannot be read. */
    fun title(uri: String): String?
}

/** The phone's own tones, read through Android. */
class PhoneTones(private val context: Context) : ToneLibrary {
    override fun exists(uri: String): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri.toUri(), "r")?.use { true } ?: false
    }.getOrDefault(false)

    override fun title(uri: String): String? =
        runCatching { RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context) }.getOrNull()
}
