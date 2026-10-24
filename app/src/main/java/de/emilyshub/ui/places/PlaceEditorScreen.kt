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
import de.emilyshub.places.PlaceWithWifis
import de.emilyshub.places.Places
import de.emilyshub.places.toDraft
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.routines.SectionTitle
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
    // The other places, which its name and WLANs are checked against; saving waits for them.
    val others = all?.filter { it.place.id != placeId } ?: return
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
                draft = draft,
                others = others,
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
 * The place's WLANs, each with what keeps it from being saved; below them, the WLAN connected to now to take over,
 * or why Android does not tell its name, and a name to type in.
 */
@Composable
private fun WifisField(
    draft: PlaceDraft,
    others: List<PlaceWithWifis>,
    wifi: CurrentWifi,
    showMissing: Boolean,
    onChange: (List<String>) -> Unit,
) {
    val wifis = draft.wifis
    var typed by rememberSaveable { mutableStateOf("") }
    val access = rememberWifiAccess()
    val add = { ssid: String ->
        val name = ssid.trim()
        if (name.isNotEmpty() && name !in wifis) onChange(wifis + name)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.place_wifis))
        wifis.forEach { ssid ->
            val problem = when {
                PlaceDraft.isTooLong(ssid) -> stringResource(R.string.place_wifi_too_long, PlaceDraft.SSID_MAX_BYTES)
                else -> draft.ownerOf(ssid, others)?.let { stringResource(R.string.place_wifi_taken, it.place.name) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ssid, style = MaterialTheme.typography.bodyLarge)
                    problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
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
        if (access.current == WifiAccess.Granted) {
            val current by remember(wifi, access.current) { wifi.ssid() }.collectAsState(initial = null)
            val connected = current
            when {
                connected == null -> Text(stringResource(R.string.place_wifi_none), style = MaterialTheme.typography.bodyMedium)
                connected !in wifis -> TextButton(onClick = { add(connected) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.place_wifi_take_current, connected))
                }
            }
        } else {
            WifiAccessNotice(access)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.place_wifi_typed)) },
                singleLine = true,
                isError = PlaceDraft.isTooLong(typed),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    add(typed)
                    typed = ""
                },
                enabled = typed.isNotBlank(),
            ) { Text(stringResource(R.string.place_wifi_add)) }
        }
    }
}
