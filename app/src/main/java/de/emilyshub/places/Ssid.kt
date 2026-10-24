package de.emilyshub.places

import de.emilyshub.data.InputText

/** The rules for WLAN names (SSIDs), which Android matches exactly: upper and lower case and spaces count. */
object Ssid {
    /** How long a WLAN name can be, in bytes of UTF-8: an "ä" takes two. */
    const val MAX_BYTES = 32

    /** Whether a WLAN could be named so: it shows something, without control characters, in at most [MAX_BYTES]. */
    fun isValid(ssid: String): Boolean =
        InputText.isVisible(ssid) && ssid.none(Char::isISOControl) && ssid.toByteArray(Charsets.UTF_8).size <= MAX_BYTES

    /**
     * The name as Android reports it, without the quotes around it; none while Android keeps it back. A name that
     * is no UTF-8 comes as hex digits without quotes, and is taken as it is.
     */
    fun of(reported: String?): String? = when {
        reported == null || reported == UNKNOWN -> null
        reported.length >= 2 && reported.startsWith('"') && reported.endsWith('"') ->
            reported.substring(1, reported.length - 1).ifEmpty { null }
        else -> reported.ifEmpty { null }
    }

    /** What Android says for a name it keeps back, as `WifiManager.UNKNOWN_SSID`. */
    private const val UNKNOWN = "<unknown ssid>"
}
