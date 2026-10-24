package de.emilyshub.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.places.Area
import de.emilyshub.places.CurrentLocation
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.PlaceDraft
import de.emilyshub.places.PlaceProblem
import de.emilyshub.places.Places
import de.emilyshub.places.Position
import de.emilyshub.places.Ssid
import de.emilyshub.places.Wifi
import de.emilyshub.places.toDraft
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.launchNow
import kotlinx.coroutines.launch
import java.io.Serializable
import kotlin.math.roundToInt

/** Which place the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface PlaceEditorTarget : Serializable {
    data object New : PlaceEditorTarget

    data class Edit(val placeId: Long) : PlaceEditorTarget
}

/**
 * Creates or changes a place with its name, WLANs and GPS area, or deletes it. Leaving without saving drops the
 * changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorScreen(
    places: Places,
    wifi: CurrentWifi,
    location: CurrentLocation,
    target: PlaceEditorTarget,
    onClose: () -> Unit,
) {
    val placeId = (target as? PlaceEditorTarget.Edit)?.placeId
    var draft by rememberSaveable { mutableStateOf(PlaceDraft(name = "", wifis = emptyList())) }
    var loaded by rememberSaveable { mutableStateOf(placeId == null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var triedToSave by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Saving and deleting close the editor at once; this keeps a second tap in the same frame from doing it twice.
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val all by remember(places) { places.all() }.collectAsState(initial = null)
    val access = rememberLocationAccess()

    if (!loaded && placeId != null) {
        LaunchedEffect(placeId) {
            val place = places.get(placeId)
            if (place == null) {
                onClose()
            } else {
                draft = place.toDraft()
                loaded = true
            }
        }
        return
    }
    // The other places, which its name is checked against; saving waits for them.
    val others = all?.filter { it.id != placeId } ?: return
    val problems = draft.problems(others)
    val closeWith = { work: suspend () -> Unit ->
        if (!closing) {
            closing = true
            scope.launchNow { work() }
            onClose()
        }
    }
    val save = {
        if (problems.isNotEmpty()) {
            triedToSave = true
        } else {
            val saved = draft
            closeWith { if (placeId == null) places.create(saved) else places.update(placeId, saved) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (placeId == null) R.string.place_new else R.string.place_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (placeId != null) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    TextButton(onClick = save) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val nameProblem = when {
                triedToSave && PlaceProblem.NameMissing in problems -> stringResource(R.string.place_name_missing)
                PlaceProblem.NameTaken in problems -> stringResource(R.string.place_name_taken)
                PlaceProblem.NameTooLong in problems -> stringResource(R.string.text_too_long, PlaceDraft.NAME_MAX)
                else -> null
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.place_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = nameProblem != null,
                supportingText = nameProblem?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            // Both the WLAN connected to and the position now need the location allowed and on.
            if (access.current != LocationAccess.Granted) LocationAccessNotice(access)
            WifisField(
                wifis = draft.wifis,
                wifi = wifi,
                access = access,
                showMissing = triedToSave && PlaceProblem.WifiOrAreaMissing in problems,
                onChange = { draft = draft.copy(wifis = it) },
            )
            AreaField(
                draft = draft,
                problems = problems,
                triedToSave = triedToSave,
                location = location,
                access = access,
                // A change to the draft as it is then: taking the position over finishes later.
                onChange = { change -> draft = change(draft) },
            )
        }
    }

    if (confirmingDelete && placeId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.place_delete_title)) },
            text = { Text(stringResource(R.string.place_delete_text, draft.cleaned().name)) },
            confirmButton = {
                TextButton(onClick = { closeWith { places.delete(placeId) } }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * The place's WLANs, each marked if Android could not name one so; below them, the WLAN connected to now to take
 * over exactly as Android names it while the location is allowed, or why Android does not tell its name, and a name
 * to type in.
 */
@Composable
private fun WifisField(
    wifis: List<String>,
    wifi: CurrentWifi,
    access: Checked<LocationAccess>,
    showMissing: Boolean,
    onChange: (List<String>) -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val add = { ssid: String -> if (ssid.isNotBlank() && ssid !in wifis) onChange(wifis + ssid) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.place_wifis))
        wifis.forEach { ssid ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ssid, style = MaterialTheme.typography.bodyLarge)
                    if (!Ssid.isValid(ssid)) {
                        Text(
                            stringResource(R.string.place_wifi_invalid, Ssid.MAX_BYTES),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = { onChange(wifis - ssid) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.place_wifi_remove, ssid))
                }
            }
        }
        if (showMissing) {
            Text(
                stringResource(R.string.place_wifi_or_area_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (wifis.size >= PlaceDraft.WIFIS_MAX) {
            Text(stringResource(R.string.place_too_many_wifis, PlaceDraft.WIFIS_MAX), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        when (val connected = rememberConnectedWifi(wifi, access)) {
            // Without the location allowed and on, the notice above the WLANs says why.
            null -> Unit
            Wifi.None -> Hint(stringResource(R.string.place_wifi_none))
            Wifi.NameHidden -> Hint(stringResource(R.string.places_recognised_name_hidden))
            is Wifi.Named -> if (connected.ssid !in wifis) {
                TextButton(onClick = { add(connected.ssid) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.place_wifi_take_current, connected.ssid))
                }
            }
        }
        // Typed in, it is taken without the spaces around it, which keyboards add after a word; as typed otherwise.
        val trimmed = typed.trim()
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.place_wifi_typed)) },
                singleLine = true,
                // A keyboard's corrections would change a name that has to match exactly.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                isError = trimmed.isNotEmpty() && !Ssid.isValid(trimmed),
                supportingText = { Text(stringResource(R.string.place_wifi_typed_hint)) },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    add(trimmed)
                    typed = ""
                },
                enabled = trimmed.isNotEmpty(),
            ) { Text(stringResource(R.string.place_wifi_add)) }
        }
    }
}

/**
 * The place's GPS area: its center typed in degrees or the position now taken over, while the location is allowed,
 * and its radius; or none, the place then recognised by its WLANs alone.
 */
@Composable
private fun AreaField(
    draft: PlaceDraft,
    problems: Set<PlaceProblem>,
    triedToSave: Boolean,
    location: CurrentLocation,
    access: Checked<LocationAccess>,
    onChange: ((PlaceDraft) -> PlaceDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.place_area))
        Hint(stringResource(R.string.place_area_hint))
        if (access.current == LocationAccess.Granted) {
            TakePosition(location) { position -> onChange { it.withCenter(position.coordinates) } }
        }
        DegreesField(
            value = draft.latitude,
            label = stringResource(R.string.place_latitude),
            hint = stringResource(R.string.place_latitude_hint),
            problem = stringResource(R.string.place_latitude_invalid)
                .takeIf { PlaceProblem.LatitudeInvalid in problems && (triedToSave || draft.latitude.isNotBlank()) },
            onChange = { typed -> onChange { it.copy(latitude = typed) } },
        )
        DegreesField(
            value = draft.longitude,
            label = stringResource(R.string.place_longitude),
            hint = stringResource(R.string.place_longitude_hint),
            problem = stringResource(R.string.place_longitude_invalid)
                .takeIf { PlaceProblem.LongitudeInvalid in problems && (triedToSave || draft.longitude.isNotBlank()) },
            onChange = { typed -> onChange { it.copy(longitude = typed) } },
        )
        if (draft.hasArea) {
            val radius = stringResource(R.string.place_radius, draft.radius)
            Text(radius, style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = draft.radius.toFloat(),
                onValueChange = { chosen -> onChange { it.copy(radius = chosen.roundToInt()) } },
                valueRange = Area.RADIUS_MIN.toFloat()..Area.RADIUS_MAX.toFloat(),
                steps = (Area.RADIUS_MAX - Area.RADIUS_MIN) / RADIUS_STEP - 1,
                // 44 steps would mark the track all over.
                colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                modifier = Modifier.semantics { stateDescription = radius },
            )
            TextButton(onClick = { onChange(PlaceDraft::withoutArea) }) {
                Text(stringResource(R.string.place_area_remove))
            }
        }
    }
}

/** Takes over where Android says the device is now, and says how sure Android is of it, or that it cannot tell. */
@Composable
private fun TakePosition(location: CurrentLocation, onTaken: (Position) -> Unit) {
    var locating by remember { mutableStateOf(false) }
    // Of the position taken over last, give or take how many meters; or that there was none to be had.
    var accuracy by rememberSaveable { mutableStateOf<Int?>(null) }
    var noPosition by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    TextButton(
        onClick = {
            locating = true
            scope.launch {
                val position = location.position()
                locating = false
                noPosition = position == null
                accuracy = position?.accuracy?.roundToInt()
                position?.let(onTaken)
            }
        },
        enabled = !locating,
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (locating) R.string.places_locating else R.string.place_area_take_current))
    }
    when {
        noPosition -> Hint(stringResource(R.string.places_no_position))
        accuracy != null -> Hint(stringResource(R.string.place_area_taken, accuracy ?: 0))
    }
}

/** A field for degrees, which takes a point or a comma and, for south and west, a minus. */
@Composable
private fun DegreesField(value: String, label: String, hint: String, problem: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = problem != null,
        supportingText = { Text(problem ?: hint) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The radius goes in steps of 10 m. */
private const val RADIUS_STEP = 10
