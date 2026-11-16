package de.emilyshub.speech

/** The phone's speech output as a test sets it up: the [voices] without network it has, and what was [spoken], with which voice. */
class FakeSpeech : Speech {
    val voices = mutableListOf<OfflineVoice>()
    val spoken = mutableListOf<Pair<String, String>>()

    override suspend fun voices(): List<OfflineVoice> = voices.toList()

    override suspend fun speak(voice: String, text: String) {
        if (voices.any { it.name == voice }) spoken += voice to text
    }

    /** How often what was read was stopped. */
    var stopped = 0

    override fun stop() {
        stopped++
    }
}
