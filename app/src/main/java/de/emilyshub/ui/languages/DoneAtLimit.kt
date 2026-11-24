package de.emilyshub.ui.languages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.emilyshub.Day
import de.emilyshub.languages.Workloads

/**
 * While the app shows, day by day, the Tagespensen geschafft at their limit tell their routines so ([Workloads.tellDoneAtLimit];
 * SPEC.md §15; #156): as soon as the app shows, at midnight, and as the shares of the overall limit move. Which, and how
 * often, the Tagespensum and the routines decide.
 */
@Composable
fun WorkloadsDoneWhileShown(workloads: Workloads, day: Day) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(workloads, day, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { workloads.tellDoneAtLimit(day.dates()) }
    }
}
