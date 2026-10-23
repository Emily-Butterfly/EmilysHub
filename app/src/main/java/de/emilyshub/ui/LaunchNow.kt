package de.emilyshub.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Starts [block] right away instead of on the next frame. For a save, this means the write is under
 * way before the screen that asked for it can go away; the repositories then finish what they started.
 */
fun CoroutineScope.launchNow(block: suspend CoroutineScope.() -> Unit): Job =
    launch(start = CoroutineStart.UNDISPATCHED, block = block)
