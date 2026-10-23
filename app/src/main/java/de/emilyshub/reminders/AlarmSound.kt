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

/** What rings while a full-screen alarm is up. Starting it again while it rings changes nothing. */
interface AlarmRinger {
    fun start()

    fun stop()
}

/**
 * Rings like an alarm clock (SPEC.md §3): the phone's alarm sound over and over at the alarm volume, and
 * vibrating on and off. "Nicht stören" lets it through where it lets alarms through. Without an alarm
 * sound chosen, Android plays its own.
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

    override fun start() {
        if (ringing) return
        ringing = true
        ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
            audioAttributes = ALARM_AUDIO
            isLooping = true
            play()
        }
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
        vibrator.cancel()
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
