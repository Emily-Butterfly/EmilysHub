package de.emilyshub.ui.fields

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.places.Places
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.Hint
import de.emilyshub.ui.NewChip
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.places.BackgroundLocationNotice

/**
 * The places a routine or to-do pops up at (SPEC.md §4): any of those chosen; how it reminds with none and with some,
 * [hintNone] and [hintChosen] say. Once it is bound to one, the location is asked for in the background here, and only
 * here, so that it pops up on arrival with the app closed.
 *
 * "Neuer Ort" opens the place editor over the editor ([onNewPlace], #112), whose draft stays as it is. A place made
 * there is chosen here once the field shows again: one that was not there when it was tapped, which the field keeps in
 * mind through a rotation and the lock. Left without a place, nothing changes; choosing one by hand then forgets
 * what there was.
 */
@Composable
fun PlacesField(
    places: Places,
    selected: Set<Long>,
    @StringRes hintNone: Int,
    @StringRes hintChosen: Int,
    onChange: (Set<Long>) -> Unit,
    onNewPlace: () -> Unit,
) {
    val all by remember(places) { places.all() }.collectAsState(initial = null)
    // The places there were when "Neuer Ort" was tapped, until one more is there, or one is chosen by hand. Only the
    // place editor opened from here can make one meanwhile: nothing else that makes places opens over an editor.
    var before by rememberSaveable { mutableStateOf<LongArray?>(null) }
    val list = all ?: return
    // One deleted meanwhile is chosen no more.
    val chosen = selected.filter { id -> list.any { it.id == id } }.toSet()
    val currentChosen by rememberUpdatedState(chosen)
    val currentOnChange by rememberUpdatedState(onChange)
    LaunchedEffect(list, before) {
        val known = before ?: return@LaunchedEffect
        val made = list.map { it.id }.filter { it !in known }
        if (made.isNotEmpty()) {
            before = null
            currentOnChange(currentChosen + made)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.routine_places))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { place ->
                val isChosen = place.id in chosen
                ChoiceChip(
                    place.name,
                    isChosen,
                    onClick = {
                        // Chosen by hand: a place made after the editor was left without one is not this field's.
                        before = null
                        onChange(if (isChosen) chosen - place.id else chosen + place.id)
                    },
                )
            }
            NewChip(
                R.string.place_new,
                onClick = {
                    before = list.map { it.id }.toLongArray()
                    onNewPlace()
                },
            )
        }
        Hint(stringResource(if (chosen.isEmpty()) hintNone else hintChosen))
        if (chosen.isNotEmpty()) BackgroundLocationNotice()
    }
}

/** "Ort: Zuhause oder Arbeit": the places a to-do reminds at, by [names], as its row and its reminder say; none without. */
fun Resources.placesLabel(names: List<String>): String? =
    names.takeIf { it.isNotEmpty() }?.let { getString(R.string.todo_reminder_places, it.joinToString(getString(R.string.names_or))) }
