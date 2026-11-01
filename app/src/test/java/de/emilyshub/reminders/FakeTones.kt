package de.emilyshub.reminders

/** The phone's tones as a test has them: those it [add]s, by address and name. */
class FakeTones : ToneLibrary {
    private val tones = mutableMapOf<String, String>()

    /** A tone on the phone, at [uri] and named [title]; its address, for choosing it. */
    fun add(uri: String, title: String): Tone.Of {
        tones[uri] = title
        return Tone.Of(uri)
    }

    /** Deletes it from the phone. */
    fun remove(uri: String) {
        tones.remove(uri)
    }

    override fun exists(uri: String): Boolean = uri in tones

    override fun title(uri: String): String? = tones[uri]
}
