package de.emilyshub.reminders

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.net.toUri
import java.time.Duration

/** What rings for a full-screen alarm, while it is up and in the background ([AlarmRingService]). */
interface AlarmRinger {
    /**
     * Rings [sound] until [stop], vibrating all along; while it rings already, it rings on, with [sound] from then on.
     * Silent, it only vibrates (SPEC.md §3).
     */
    fun start(sound: Sound)

    /** Stops the ringing; while it is silent, it stays so. */
    fun stop()

    companion object {
        /** As long as an alarm clock rings before it goes quiet by itself. */
        val LIMIT: Duration = Duration.ofMinutes(10)
    }
}

/**
 * Rings like an alarm clock (SPEC.md §3): the tone of the alarm, or the alarm sound chosen in Android's settings, over
 * and over at the alarm volume, and vibrating on and off. "Nicht stören" lets it through where it lets alarms through.
 */
class AlarmSound(private val context: Context) : AlarmRinger {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    private var ringtone: Ringtone? = null
    private var ringing = false

    /** What [ringtone] plays. */
    private var playing: Sound? = null

    override fun start(sound: Sound) {
        if (ringing && sound == playing) return
        play(sound)
        if (ringing) return
        ringing = true
        val onAndOff = VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_FROM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(onAndOff, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(onAndOff, ALARM_AUDIO)
        }
    }

    override fun stop() {
        if (!ringing) return
        ringing = false
        ringtone?.stop()
        ringtone = null
        playing = null
        vibrator.cancel()
    }

    /** Plays [sound] over and over instead of what played until now; nothing, silent. */
    private fun play(sound: Sound) {
        ringtone?.stop()
        playing = sound
        val uri = when (sound) {
            Sound.PhoneDefault -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            Sound.Silent -> null
            is Sound.Of -> sound.uri.toUri()
        }
        ringtone = uri?.let { RingtoneManager.getRingtone(context, it) }?.apply {
            audioAttributes = ALARM_AUDIO
            isLooping = true
            play()
        }
    }

    private companion object {
        val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** Off at first for no time, then on and off for 0.8 s each, the on and off repeating. */
        val VIBRATION_PATTERN = longArrayOf(0, 800, 800)
        const val VIBRATION_REPEAT_FROM = 1
    }
}
