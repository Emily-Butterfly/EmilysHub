package de.emilyshub.reminders

/** Rings without a sound, telling the test whether it rings, and what it plays. */
class FakeRinger : AlarmRinger {
    var ringing = false
        private set

    /** What it plays while it rings, or played last. */
    var sound: Sound? = null
        private set

    override fun start(sound: Sound) {
        ringing = true
        this.sound = sound
    }

    override fun stop() {
        ringing = false
    }
}
