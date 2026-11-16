package de.emilyshub.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [Speech] by Android's [TextToSpeech] (#157): the engine the phone has chosen, bound on first use and kept; none, and
 * there are no voices. A voice that needs the network, or is not installed, is never offered nor used.
 */
class AndroidSpeech(private val context: Context) : Speech {
    private val lock = Mutex()
    private var engine: TextToSpeech? = null

    override suspend fun voices(): List<OfflineVoice> = withEngine { tts -> offlineOf(tts.voices.orEmpty()) } ?: emptyList()

    override suspend fun speak(voice: String, text: String) {
        withEngine { tts ->
            val chosen = tts.voices.orEmpty().find { it.name == voice && it.isOffline() } ?: return@withEngine
            if (tts.setVoice(chosen) == TextToSpeech.SUCCESS) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "card")
            }
        }
    }

    /** [block] with the engine once it has started; none if it cannot, tried again next time. */
    private suspend fun <T> withEngine(block: (TextToSpeech) -> T): T? = lock.withLock {
        val tts = engine ?: run {
            val started = CompletableDeferred<Boolean>()
            val made = TextToSpeech(context.applicationContext) { status -> started.complete(status == TextToSpeech.SUCCESS) }
            if (!started.await()) {
                made.shutdown()
                return@withLock null
            }
            made.also { engine = it }
        }
        block(tts)
    }

    companion object {
        /** Of [voices], those that need no network and are installed. */
        internal fun offlineOf(voices: Collection<Voice>): List<OfflineVoice> =
            voices.filter { it.isOffline() }.map { OfflineVoice(it.name, it.locale) }

        private fun Voice.isOffline() =
            !isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty()
    }
}
