package de.emilyshub.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.places.CurrentLocation
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.PlaceWithWifis
import de.emilyshub.places.Places
import de.emilyshub.places.Position
import de.emilyshub.places.Wifi
import de.emilyshub.places.placesOf
import kotlin.math.roundToInt

/**
 * The places (SPEC.md §4), each with its WLANs and GPS area; tapping one opens it in the editor, "+" starts a new one.
 * Above them, the place recognised now, to try them out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    places: Places,
    wifi: CurrentWifi,
    location: CurrentLocation,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    val all by remember(places) { places.all() }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.place_create))
            }
        },
    ) { contentPadding ->
        val list = all ?: return@Scaffold
        LazyColumn(contentPadding = contentPadding) {
            item(key = "recognised") { RecognisedPlace(list, wifi, location) }
            if (list.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(R.string.places_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(list, key = { it.id }) { place ->
                val area = place.area?.let { stringResource(R.string.place_area_summary, it.radius) }
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = { Text((place.ssids + listOfNotNull(area)).joinToString(" · ")) },
                    modifier = Modifier.clickable { onOpen(place.id) },
                )
            }
        }
    }
}

/**
 * Which of the [places] the device is at now, by the WLAN it is connected to and, while a place has a GPS area, by
 * its position; or why that cannot be told. With a WLAN at several places, it is at all of them.
 */
@Composable
private fun RecognisedPlace(places: List<PlaceWithWifis>, wifi: CurrentWifi, location: CurrentLocation) {
    val access = rememberLocationAccess()
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.places_recognised_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { heading() },
            )
            if (access.current != LocationAccess.Granted) {
                LocationAccessNotice(access)
                return@Column
            }
            val connected = rememberConnectedWifi(wifi, access) ?: return@Column
            val here = places.placesOf(connected)
            Text(
                text = when (connected) {
                    Wifi.None -> stringResource(R.string.places_recognised_no_wifi)
                    Wifi.NameHidden -> stringResource(R.string.places_recognised_name_hidden)
                    is Wifi.Named -> when {
                        here.isEmpty() -> stringResource(R.string.places_recognised_unknown, connected.ssid)
                        else -> stringResource(R.string.places_recognised, here.joinToString { it.name }, connected.ssid)
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (places.any { it.area != null }) RecognisedByPosition(places, location)
        }
    }
}

/**
 * Which of the [places] the device is at by its position: those whose GPS area it lies within. Asked for once, as the
 * places show, so that Android is not kept finding the position for as long as they do.
 */
@Composable
private fun RecognisedByPosition(places: List<PlaceWithWifis>, location: CurrentLocation) {
    var asked by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf<Position?>(null) }
    LaunchedEffect(location) {
        position = location.position()
        asked = true
    }
    val found = position
    Text(
        text = when {
            !asked -> stringResource(R.string.places_locating)
            found == null -> stringResource(R.string.places_no_position)
            else -> {
                val accuracy = found.accuracy.roundToInt()
                val told = places.mapNotNull { place -> place.area?.let { place to it.includes(found) } }
                val here = told.filter { (_, within) -> within == true }.map { (place) -> place.name }
                when {
                    here.isNotEmpty() -> stringResource(R.string.places_recognised_area, here.joinToString(), accuracy)
                    told.any { (_, within) -> within == null } -> stringResource(R.string.places_position_imprecise, accuracy)
                    else -> stringResource(R.string.places_recognised_no_area, accuracy)
                }
            }
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}
