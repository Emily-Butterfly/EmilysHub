package de.emilyshub.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.places.Places
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.places.BackgroundLocationNotice

/**
 * The places the routine pops up at (SPEC.md §4): any of those chosen, everywhere with none. Once it is bound to one,
 * the location is asked for in the background here, and only here, so that it pops up on arrival with the app closed.
 */
@Composable
fun PlacesField(places: Places, selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    val all by remember(places) { places.all() }.collectAsState(initial = null)
    val list = all ?: return
    // One deleted meanwhile is chosen no more.
    val chosen = selected.filter { id -> list.any { it.id == id } }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.routine_places))
        if (list.isEmpty()) {
            Hint(stringResource(R.string.routine_places_none))
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { place ->
                val isChosen = place.id in chosen
                ChoiceChip(place.name, isChosen, onClick = { onChange(if (isChosen) chosen - place.id else chosen + place.id) })
            }
        }
        Hint(stringResource(if (chosen.isEmpty()) R.string.routine_places_everywhere else R.string.routine_places_chosen))
        if (chosen.isNotEmpty()) BackgroundLocationNotice()
    }
}
