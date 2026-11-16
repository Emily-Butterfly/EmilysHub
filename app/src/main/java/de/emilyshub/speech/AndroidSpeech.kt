package de.emilyshub.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [Speech] by Android's [TextToSpeech] (SPEC.md §9, §15; #157): the engine the phone has chosen, bound on first use and
 * kept while it answers; one that does not start, or stops answering, is let go and bound again next time. A voice
 * that needs the network, synthesises over it, or is not installed is never offered nor used: the voice is checked
 * again once set, and the engine told to stay on the phone.
 */
class AndroidSpeech(private val context: Context) : Speech {
    private val lock = Mutex()
    private var engine: TextToSpeech? = null

    override suspend fun voices(): List<OfflineVoice> = withEngine { tts -> tts.voices?.let(::offlineOf) }.orEmpty()

    override suspend fun speak(voice: String, text: String) {
        withEngine { tts ->
            // None at all: the engine no longer answers.
            val all = tts.voices ?: return@withEngine null
            val chosen = all.find { it.name == voice && it.isOffline() } ?: return@withEngine Unit
            // Set and still the one asked for: an engine that took another instead reads nothing.
            if (tts.setVoice(chosen) == TextToSpeech.SUCCESS && tts.voice?.name == voice) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, ON_THE_PHONE, "card")
            }
            Unit
        }
    }

    override fun stop() {
        engine?.stop()
    }

    /**
     * [block] with the engine once it has started; none if it cannot, within [START_TIMEOUT_MS], or if it no longer
     * answers ([block] gives none), when it is let go to be bound again next time.
     */
    private suspend fun <T : Any> withEngine(block: (TextToSpeech) -> T?): T? = lock.withLock {
        val tts = engine ?: start() ?: return@withLock null
        val result = block(tts)
        if (result == null) {
            tts.shutdown()
            engine = null
        }
        result
    }

    private suspend fun start(): TextToSpeech? {
        val started = CompletableDeferred<Boolean>()
        val made = TextToSpeech(context.applicationContext) { status -> started.complete(status == TextToSpeech.SUCCESS) }
        if (withTimeoutOrNull(START_TIMEOUT_MS) { started.await() } != true) {
            made.shutdown()
            return null
        }
        engine = made
        return made
    }

    companion object {
        /** How long the engine may take to start before it counts as none. */
        private const val START_TIMEOUT_MS = 5_000L

        /** Asks the engine to synthesise on the phone, never over the network. */
        private val ON_THE_PHONE = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
            putString(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS, "true")
        }

        /** Of [voices], those that need no network, do not synthesise over it, and are installed. */
        internal fun offlineOf(voices: Collection<Voice>): List<OfflineVoice> =
            voices.filter { it.isOffline() }.map { OfflineVoice(it.name, it.locale) }

        private fun Voice.isOffline(): Boolean {
            val features = features.orEmpty()
            return !isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features &&
                TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS !in features
        }
    }
}
