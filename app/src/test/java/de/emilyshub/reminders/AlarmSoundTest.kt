package de.emilyshub.reminders

import android.content.Context
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AlarmSoundTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val sound = AlarmSound(context)

    @Test
    fun `it vibrates over and over like an alarm clock, until it is stopped`() {
        sound.start()

        assertTrue(shadowOf(vibrator).isVibrating)
        assertEquals("repeats", 1, shadowOf(vibrator).repeat)
        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes
        assertEquals(VibrationAttributes.USAGE_ALARM, attributes.usage)

        sound.stop()

        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun `starting it twice or stopping it unstarted changes nothing`() {
        sound.stop()
        sound.start()
        sound.start()
        sound.stop()

        assertFalse(shadowOf(vibrator).isVibrating)
    }
}
