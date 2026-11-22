package de.emilyshub.ui.languages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.emilyshub.Day
import de.emilyshub.languages.Flashcards
import de.emilyshub.routines.Routines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The routines done by the cards of a language whose workload is done at its limit (SPEC.md §15; #156) are done too,
 * also with no answer that day: as soon as the app shows, at midnight, and as the shares of the overall limit move, say
 * as a language comes back from the archive. Each routine once a day, however often it is seen so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun RoutinesDoneAtLimit(flashcards: Flashcards, routines: Routines, day: Day) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(flashcards, routines, day, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            day.dates().flatMapLatest(flashcards::doneAtLimit).collect { languages ->
                languages.forEach { routines.doneByVocabularyOnce(it) }
            }
        }
    }
}
