package de.emilyshub.ui.warum

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.ui.MoreMenu
import de.emilyshub.ui.MoreMenuItem
import de.emilyshub.ui.launchNow
import de.emilyshub.warum.BoardExport
import de.emilyshub.warum.BoardTheme
import de.emilyshub.warum.Reasons

/**
 * The menu (⋮) of Mein Warum as it is looked at (SPEC.md §18), only while the board has a Grund: "Als Bild exportieren",
 * after the hint that the image is not encrypted. One image goes to the file chosen in Android's file dialog, several
 * into a folder chosen once; drawn in the design the app shows now. Never shared with other apps.
 */
@Composable
fun WarumMenu(reasons: Reasons, export: BoardExport) {
    val board by remember(reasons) { reasons.board() }.collectAsState(initial = null)
    if (board.isNullOrEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf(false) }
    val theme = BoardTheme(standardColors(), page = MaterialTheme.colorScheme.background.toArgb())

    fun told(saved: Boolean) {
        val text = if (saved) R.string.warum_export_done else R.string.warum_export_failed
        Toast.makeText(context, context.getString(text), Toast.LENGTH_SHORT).show()
    }

    val one = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launchNow { told(export.saveOne(uri.toString(), theme)) }
    }
    val several = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launchNow { told(export.saveInto(uri.toString(), theme)) }
    }

    MoreMenu(stringResource(R.string.destination_warum), expanded = open, onExpandedChange = { open = it }) { close ->
        MoreMenuItem(stringResource(R.string.warum_export), close) { asking = true }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.warum_export)) },
            text = { Text(stringResource(R.string.warum_export_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        Toast.makeText(context, context.getString(R.string.warum_export_busy), Toast.LENGTH_SHORT).show()
                        scope.launchNow {
                            when (val count = export.count(theme)) {
                                0 -> Unit
                                1 -> one.launch(export.fileName(page = 1, of = count))
                                else -> several.launch(null)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.warum_export_go)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
