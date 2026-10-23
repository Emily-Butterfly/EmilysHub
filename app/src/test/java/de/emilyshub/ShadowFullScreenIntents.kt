package de.emilyshub

import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/**
 * Robolectric's notifications, plus a say in whether the app may fill the screen with one: from Android 14
 * on that is up to the user, and Robolectric always says no. The test says, in
 * [TestApplication.fullScreenAlarmsAllowed]. Set for every test in robolectric.properties.
 */
@Implements(NotificationManager::class)
class ShadowFullScreenIntents : ShadowNotificationManager() {

    @Implementation(minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    protected fun canUseFullScreenIntent(): Boolean =
        ApplicationProvider.getApplicationContext<TestApplication>().fullScreenAlarmsAllowed
}
