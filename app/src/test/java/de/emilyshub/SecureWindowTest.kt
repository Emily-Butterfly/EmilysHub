package de.emilyshub

import android.app.Activity
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class SecureWindowTest {

    @Test
    fun `app window blocks screenshots and is hidden in recents`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and FLAG_SECURE != 0)
            }
        }
    }

    @Test
    fun `every activity window is secure, not only the launcher`() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            assertTrue(controller.get().window.attributes.flags and FLAG_SECURE != 0)
        }
    }
}
