package de.emilyshub.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

class ToneTest {

    private val morning = "content://media/internal/audio/media/12"
    private val bell = "content://media/internal/audio/media/34"

    @Test
    fun `a tone is stored as its address, the default as nothing, and silent as such`() {
        for (tone in listOf(Tone.Standard, Tone.Silent, Tone.Of(morning))) {
            assertEquals(tone, Tone.of(tone.stored))
        }
        assertEquals("", Tone.Standard.stored)
    }

    @Test
    fun `a tone the phone has as a resource of an app counts as a tone too`() {
        val chime = "android.resource://com.android.sounds/raw/chime"

        assertEquals(Tone.Of(chime), Tone.of(chime))
    }

    @Test
    fun `anything stored but an address of the phone's tones counts as the default`() {
        assertEquals(Tone.Standard, Tone.of("file:///sdcard/tone.mp3"))
        assertEquals(Tone.Standard, Tone.of("Morgentau"))
        assertEquals(Tone.Standard, Tone.of(null))
    }

    @Test
    fun `a tone of its own sounds as it is, the default sounds as the settings have it`() {
        val exists = { _: String -> true }

        assertEquals(Sound.Of(morning), soundOf(Tone.Of(morning), default = Tone.Of(bell), exists))
        assertEquals(Sound.Of(bell), soundOf(Tone.Standard, default = Tone.Of(bell), exists))
        assertEquals(Sound.Silent, soundOf(Tone.Silent, default = Tone.Of(bell), exists))
        assertEquals(Sound.Silent, soundOf(Tone.Standard, default = Tone.Silent, exists))
    }

    @Test
    fun `with the settings at their default, it is the phone's own`() {
        assertEquals(Sound.PhoneDefault, soundOf(Tone.Standard, default = Tone.Standard) { true })
    }

    @Test
    fun `a tone gone falls back to the default, and a default gone to the phone's own`() {
        val onlyBell = { uri: String -> uri == bell }

        assertEquals(Sound.Of(bell), soundOf(Tone.Of(morning), default = Tone.Of(bell), onlyBell))
        assertEquals(Sound.PhoneDefault, soundOf(Tone.Of(morning), default = Tone.Of("content://media/gone/1"), onlyBell))
        assertEquals(Sound.Silent, soundOf(Tone.Of(morning), default = Tone.Silent, onlyBell))
    }
}
