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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.PlaceWithWifis
import de.emilyshub.places.Places
import de.emilyshub.places.Wifi
import de.emilyshub.places.placesOf

/**
 * The places (SPEC.md §4), each with its WLANs; tapping one opens it in the editor, "+" starts a new one. Above them,
 * the place recognised now, to try them out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(places: Places, wifi: CurrentWifi, onOpen: (Long) -> Unit, onCreate: () -> Unit, onBack: () -> Unit) {
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
            item(key = "recognised") { RecognisedPlace(list, wifi) }
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
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = { Text(place.ssids.joinToString(" · ")) },
                    modifier = Modifier.clickable { onOpen(place.id) },
                )
            }
        }
    }
}

/**
 * Which of the [places] the device is at now, by the WLAN it is connected to; or why that cannot be told. With a
 * WLAN at several places, it is at all of them.
 */
@Composable
private fun RecognisedPlace(places: List<PlaceWithWifis>, wifi: CurrentWifi) {
    val access = rememberWifiAccess()
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
            if (access.current != WifiAccess.Granted) {
                WifiAccessNotice(access)
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
        }
    }
}
