package de.emilyshub.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How an editor or a page of the Doku saves or deletes and leaves: once, and to the end. */
class ClosingTest {

    @Test
    fun `a second tap neither does the work again nor leaves twice`() = runTest {
        var done = 0
        var closed = 0
        val closing = Closing(this) { closed++ }

        closing { done++ }
        closing { done++ }
        advanceUntilIdle()

        assertEquals(1, done)
        assertEquals(1, closed)
    }

    @Test
    fun `the work runs to its end though the editor is gone meanwhile`() = runTest {
        val editor = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        var saved = false
        val closing = Closing(editor) {}

        closing {
            delay(100)
            saved = true
        }
        editor.cancel()
        advanceUntilIdle()

        assertTrue(saved)
    }

    @Test
    fun `it says when it has begun, so that the editor is not left another way meanwhile`() = runTest {
        val closing = Closing(this) {}
        assertFalse(closing.begun)

        closing.leaveThrough { delay(100) }

        assertTrue(closing.begun)
    }
}
