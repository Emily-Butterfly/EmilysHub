package de.emilyshub

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.security.FakeAuthenticator
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

/** What a user does with the whole app, shared by the Robolectric tests that drive [MainActivity]. */
fun launchApp(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

/** Leaves the app the way the home button does, lets [whileAway] happen, and opens it again. */
fun ActivityScenario<*>.leaveAndReturn(whileAway: () -> Unit) {
    moveToState(Lifecycle.State.CREATED)
    whileAway()
    moveToState(Lifecycle.State.RESUMED)
}

/**
 * A routine at [times] ("07:30"), daily unless given another [repetition] and with the default follow-ups
 * unless given others, set up before the test opens the app.
 */
fun TestApplication.routine(
    name: String,
    vararg times: String,
    repetition: Repetition = Repetition.Daily,
    followUps: FollowUps = FollowUps(),
): Long = runBlocking {
    routines.create(
        RoutineDraft(name = name, description = "", times = times.map(LocalTime::parse), repetition = repetition, followUps = followUps),
    )
}

/** Once the prompt is up, the user puts a finger on the sensor and the app shows. */
fun ComposeTestRule.unlockWithFingerprint(authenticator: FakeAuthenticator) {
    waitForIdle()
    authenticator.succeed()
    waitForIdle()
}
