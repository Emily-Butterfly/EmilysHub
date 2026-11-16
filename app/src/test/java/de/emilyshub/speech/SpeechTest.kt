package de.emilyshub.speech

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** The voices offered to read cards aloud (SPEC.md §9, §15; #157): only those without network, those of the language first. */
@RunWith(AndroidJUnit4::class)
class SpeechTest {

    @Test
    fun `a voice that needs the network, synthesises over it, or is not installed is never offered`() {
        val voices = listOf(
            voice("ar-local", Locale("ar"), network = false),
            voice("ar-network", Locale("ar"), network = true),
            voice("ar-missing", Locale("ar"), network = false, features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)),
            voice("ar-online", Locale("ar"), network = false, features = setOf(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS)),
        )

        assertEquals(listOf(OfflineVoice("ar-local", Locale("ar"))), AndroidSpeech.offlineOf(voices))
    }

    @Test
    fun `the voices whose language is called as the app's language come first, then the others by name`() {
        val voices = listOf(
            OfflineVoice("it-local", Locale.ITALY),
            OfflineVoice("de-local", Locale.GERMANY),
            OfflineVoice("ar-local", Locale("ar", "EG")),
        )

        assertEquals(listOf("ar-local", "de-local", "it-local"), OfflineVoice.offeredTo(" arabisch ", voices).map { it.name })
        assertEquals(listOf("it-local", "ar-local", "de-local"), OfflineVoice.offeredTo("Italienisch", voices).map { it.name })
    }

    @Test
    fun `a voice is shown by its language and country in German, and its name`() {
        assertEquals("Arabisch (Ägypten) · ar-local", OfflineVoice("ar-local", Locale("ar", "EG")).label)
    }

    private fun voice(name: String, locale: Locale, network: Boolean, features: Set<String> = emptySet()) =
        Voice(name, locale, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, network, features)
}
