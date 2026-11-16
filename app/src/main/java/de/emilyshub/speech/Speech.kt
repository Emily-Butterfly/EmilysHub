package de.emilyshub.speech

import java.util.Locale

/** A voice of the phone's speech output that needs no network (SPEC.md §9, §15; #157): its [name], as Android knows it, and the [locale] it speaks. */
data class OfflineVoice(val name: String, val locale: Locale) {
    /** As the app shows it: its language and country in German, and its name, as two voices may speak alike. */
    val label: String get() = "${locale.getDisplayName(Locale.GERMAN)} · $name"

    companion object {
        /**
         * Of [voices], those offered to the language named [language]: first those whose language is called so in
         * German (such as "Arabisch"), then the others, each by their label.
         */
        fun offeredTo(language: String, voices: List<OfflineVoice>): List<OfflineVoice> {
            val (matching, others) = voices.sortedBy { it.label.lowercase(Locale.GERMAN) }
                .partition { it.locale.getDisplayLanguage(Locale.GERMAN).equals(language.trim(), ignoreCase = true) }
            return matching + others
        }
    }
}

/**
 * The phone's speech output, another app (SPEC.md §9, §15; #157): only its voices that need no network, so that what is
 * read never leaves the phone; nothing is recorded or kept. It reads only when asked to.
 */
interface Speech {
    /** Its voices that need no network and are installed, as they are now; none without a speech output. */
    suspend fun voices(): List<OfflineVoice>

    /** Reads [text] aloud with the voice named [voice], if it is one of [voices]; never with another, not even instead. */
    suspend fun speak(voice: String, text: String)
}
