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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.PlaceDraft
import de.emilyshub.places.PlaceProblem
import de.emilyshub.places.Places
import de.emilyshub.places.Ssid
import de.emilyshub.places.Wifi
import de.emilyshub.places.toDraft
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.launchNow
import java.io.Serializable

/** Which place the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface PlaceEditorTarget : Serializable {
    data object New : PlaceEditorTarget

    data class Edit(val placeId: Long) : PlaceEditorTarget
}

/** Creates or changes a place with its name and WLANs, or deletes it. Leaving without saving drops the changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorScreen(places: Places, wifi: CurrentWifi, target: PlaceEditorTarget, onClose: () -> Unit) {
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
            WifisField(
                wifis = draft.wifis,
                wifi = wifi,
                showMissing = triedToSave && PlaceProblem.WifisMissing in problems,
                onChange = { draft = draft.copy(wifis = it) },
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
 * over exactly as Android names it, or why Android does not tell its name, and a name to type in.
 */
@Composable
private fun WifisField(wifis: List<String>, wifi: CurrentWifi, showMissing: Boolean, onChange: (List<String>) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val access = rememberWifiAccess()
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
                stringResource(R.string.place_wifis_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (wifis.size >= PlaceDraft.WIFIS_MAX) {
            Text(stringResource(R.string.place_too_many_wifis, PlaceDraft.WIFIS_MAX), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        when (val connected = rememberConnectedWifi(wifi, access)) {
            null -> if (access.current != WifiAccess.Granted) WifiAccessNotice(access)
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

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
