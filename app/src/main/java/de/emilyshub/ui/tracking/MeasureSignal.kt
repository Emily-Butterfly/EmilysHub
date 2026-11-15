package de.emilyshub.ui.tracking

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The tone and vibration of a measuring assistant in the app (SPEC.md §17, "Stehtest"): once at each point, no
 * notification, nothing outside the app. A phone without a vibrator, or with the tone not to be had, still shows the
 * point on the screen.
 */
internal class MeasureSignal(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_ALARM, VOLUME)
    } catch (e: RuntimeException) {
        Log.w(TAG, "No tone for the measurements: ${e.javaClass.simpleName}")
        null
    }

    /** Sounds and vibrates once. */
    fun play() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, LENGTH_MILLIS)
        vibrator?.vibrate(VibrationEffect.createOneShot(LENGTH_MILLIS.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Gives the tone back to Android. */
    fun release() {
        tone?.release()
    }

    private companion object {
        const val VOLUME = 100
        const val LENGTH_MILLIS = 600
        const val TAG = "EmilysHub"
    }
}

/** A [MeasureSignal] for as long as the page shows. */
@Composable
internal fun rememberMeasureSignal(): MeasureSignal {
    val context = LocalContext.current
    val signal = remember(context) { MeasureSignal(context) }
    DisposableEffect(signal) { onDispose { signal.release() } }
    return signal
}
