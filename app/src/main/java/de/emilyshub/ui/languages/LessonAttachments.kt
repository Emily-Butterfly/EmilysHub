package de.emilyshub.ui.languages

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.AttachmentKind
import de.emilyshub.languages.AttachmentRef
import de.emilyshub.languages.CameraShot
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.Photos
import de.emilyshub.languages.TextImport
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.launchNow

/** What importing came to, to be told under the buttons; kept past a turn. */
private enum class ImportNote(val text: Int, val problem: Boolean) {
    Busy(R.string.lesson_import_busy, false),
    TextDone(R.string.lesson_import_text_done, false),
    TextTooLong(R.string.lesson_import_text_too_long, true),
    NotText(R.string.lesson_import_not_text, true),
    TooMany(R.string.lesson_import_too_many, true),
    TooLarge(R.string.lesson_import_too_large, true),
    NotPhoto(R.string.lesson_import_not_photo, true),
    NotPdf(R.string.lesson_import_not_pdf, true),
}

/**
 * The lesson's imports and attachments (SPEC.md §15): a text file added to its note; a photo chosen or taken with the
 * phone's camera app, and a PDF, attached; each attachment shown small, opened in full by tapping ([onOpen]) and
 * taken off with a question, which deletes it only once the lesson is saved. [draft] is the lesson as typed;
 * [onChange] what imports make of it, read when they are done, so that typing meanwhile is kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LessonAttachments(
    files: LessonFiles,
    draft: LessonDraft,
    tooMany: Boolean,
    onChange: ((LessonDraft) -> LessonDraft) -> Unit,
    onOpen: (AttachmentRef) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by rememberSaveable { mutableStateOf<ImportNote?>(null) }
    var removing by rememberSaveable { mutableStateOf<AttachmentRef?>(null) }
    val current by rememberUpdatedState(draft)

    fun attached(result: AttachmentImport, notReadable: ImportNote) {
        note = when (result) {
            is AttachmentImport.Added -> {
                onChange { it.copy(attachments = it.attachments + result.attachment) }
                null
            }
            AttachmentImport.TooMany -> ImportNote.TooMany
            AttachmentImport.TooLarge -> ImportNote.TooLarge
            AttachmentImport.Unreadable -> notReadable
        }
    }

    val text = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = ImportNote.Busy
        scope.launchNow {
            note = when (val result = files.importText(uri.toString(), current.note)) {
                is TextImport.Appended -> {
                    onChange { it.copy(note = result.note) }
                    ImportNote.TextDone
                }
                TextImport.TooLong -> ImportNote.TextTooLong
                TextImport.NotText -> ImportNote.NotText
            }
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = ImportNote.Busy
        scope.launchNow { attached(files.importPhoto(uri.toString(), current.attachments.size), ImportNote.NotPhoto) }
    }
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val address = shot ?: return@rememberLauncherForActivityResult
        shot = null
        if (!taken) {
            CameraShot.clear(context)
            return@rememberLauncherForActivityResult
        }
        note = ImportNote.Busy
        scope.launchNow {
            try {
                attached(files.importPhoto(address, current.attachments.size), ImportNote.NotPhoto)
            } finally {
                // The photo lay there in plain text only until read.
                CameraShot.clear(context)
            }
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = ImportNote.Busy
        scope.launchNow { attached(files.importPdf(uri.toString(), current.attachments.size), ImportNote.NotPdf) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lesson_attachments), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { text.launch(arrayOf("text/*")) }) { Text(stringResource(R.string.lesson_import_text)) }
            OutlinedButton(onClick = { photo.launch(arrayOf("image/*")) }) { Text(stringResource(R.string.lesson_import_photo)) }
            OutlinedButton(
                onClick = {
                    val uri = CameraShot.prepare(context)
                    shot = uri.toString()
                    camera.launch(uri)
                },
            ) { Text(stringResource(R.string.lesson_take_photo)) }
            OutlinedButton(onClick = { pdf.launch(arrayOf("application/pdf")) }) { Text(stringResource(R.string.lesson_import_pdf)) }
        }
        val shown = note
        when {
            shown == null -> Text(
                stringResource(R.string.lesson_attachments_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            shown.problem -> Problem(stringResource(shown.text))
            else -> Text(stringResource(shown.text), style = MaterialTheme.typography.bodySmall)
        }
        if (tooMany) Problem(stringResource(R.string.lesson_too_many_attachments))
        if (draft.attachments.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.attachments.forEach { attachment ->
                    AttachmentTile(files, attachment, onOpen = { onOpen(attachment) }, onRemove = { removing = attachment })
                }
            }
        }
    }

    removing?.let { attachment ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.attachment_remove_title)) },
            text = { Text(stringResource(R.string.attachment_remove_text, attachment.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = null
                        onChange { it.copy(attachments = it.attachments - attachment) }
                    },
                ) { Text(stringResource(R.string.attachment_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** An attachment shown small: a photo as itself, a PDF by its name; tapped, opened; with its way to be taken off. */
@Composable
private fun AttachmentTile(files: LessonFiles, attachment: AttachmentRef, onOpen: () -> Unit, onRemove: () -> Unit) {
    val label = stringResource(
        if (attachment.kind == AttachmentKind.Photo) R.string.attachment_photo else R.string.attachment_pdf,
        attachment.name,
    )
    val preview by produceState<Bitmap?>(null, attachment.file) {
        if (attachment.kind == AttachmentKind.Photo) value = files.content(attachment)?.let { Photos.preview(it, PREVIEW_EDGE) }
    }
    Box(Modifier.size(TILE.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen)
                .semantics { contentDescription = label },
        ) {
            val bitmap = preview
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.lesson_import_pdf), style = MaterialTheme.typography.titleSmall)
                    Text(attachment.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.attachment_remove, attachment.name))
        }
    }
}

private const val TILE = 96
private const val PREVIEW_EDGE = 256
