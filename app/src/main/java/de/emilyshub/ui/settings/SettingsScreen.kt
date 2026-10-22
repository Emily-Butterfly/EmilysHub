package de.emilyshub.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.data.Design
import de.emilyshub.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    canAuthenticate: () -> Boolean,
    onDesignChange: (Design) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings), modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(R.string.settings_design)
            DesignChoice(selected = settings.design, onSelect = onDesignChange)

            SectionHeader(R.string.settings_security)
            AppLockSwitch(on = settings.appLockEnabled, canAuthenticate = canAuthenticate, onChange = onAppLockChange)

            Text(
                text = stringResource(R.string.placeholder_settings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun DesignChoice(selected: Design, onSelect: (Design) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Design.entries.forEach { design ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = design == selected, role = Role.RadioButton, onClick = { onSelect(design) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = design == selected, onClick = null)
                Text(
                    text = stringResource(design.label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/** Without a fingerprint or screen lock there is nothing to unlock with, so the switch gives way to a hint. */
@Composable
private fun AppLockSwitch(on: Boolean, canAuthenticate: () -> Boolean, onChange: (Boolean) -> Unit) {
    // Checked again on every return, e.g. after setting up a screen lock in Android's settings.
    var deviceSecure by remember { mutableStateOf(canAuthenticate()) }
    LifecycleResumeEffect(canAuthenticate) {
        deviceSecure = canAuthenticate()
        onPauseOrDispose {}
    }
    val checked = on && deviceSecure

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = deviceSecure, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_app_lock), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (deviceSecure) R.string.settings_app_lock_summary else R.string.settings_app_lock_needs_screen_lock,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = deviceSecure,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

private val Design.label: Int
    @StringRes get() = when (this) {
        Design.System -> R.string.design_system
        Design.Light -> R.string.design_light
        Design.Dark -> R.string.design_dark
    }
