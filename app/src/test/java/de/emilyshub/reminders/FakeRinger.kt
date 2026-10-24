package de.emilyshub.reminders

/** Rings only in the test's mind: whether the alarm rings right now. */
class FakeRinger : AlarmRinger {
    var ringing = false
        private set

    override fun start() {
        ringing = true
    }

    override fun stop() {
        ringing = false
    }
}
