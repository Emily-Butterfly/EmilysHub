package de.emilyshub.ui.warum

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.PickedDocument
import de.emilyshub.ui.differsAsSaved
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.fields.SaveBar
import de.emilyshub.ui.fields.rememberFirstField
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.rememberCameraTrip
import de.emilyshub.ui.rememberClosing
import de.emilyshub.ui.rememberLeaving
import de.emilyshub.vault.Vault
import de.emilyshub.warum.ReasonDraft
import de.emilyshub.warum.ReasonLook
import de.emilyshub.warum.ReasonProblem
import de.emilyshub.warum.Reasons
import java.io.Serializable

/** Which reason the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface ReasonEditorTarget : Serializable {
    /** A new reason, on top of the board. */
    data object New : ReasonEditorTarget

    data class Edit(val reasonId: Long) : ReasonEditorTarget
}

/**
 * Adds or changes a Grund of Mein Warum (SPEC.md §18): its photo, chosen or taken with the phone's camera app, replaced or
 * taken off, and its text, plain, up to 500 characters, in the middle as on the board. One with neither is not saved,
 * and once 100 others have a photo, it says so. Leaving with changes asks whether to save them, and a photo imported
 * but not saved is let go of; one deleted meanwhile closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonEditorScreen(reasons: Reasons, target: ReasonEditorTarget, onClose: () -> Unit, onOpenLicenses: () -> Unit) {
    val reasonId = (target as? ReasonEditorTarget.Edit)?.reasonId
    // As saved: known at once for a new one, read first for one saved.
    var original by rememberSaveable { mutableStateOf(if (reasonId == null) ReasonDraft() else null) }
    var draft by rememberSaveable { mutableStateOf(ReasonDraft()) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var tried by rememberSaveable { mutableStateOf(false) }
    // What saving found that the draft does not tell: a photo gone, or too many with one.
    var refused by rememberSaveable { mutableStateOf(emptySet<ReasonProblem>()) }
    val scope = rememberCoroutineScope()

    val saved = original
    if (saved == null) {
        LaunchedEffect(reasonId) {
            val found = reasons.editing(checkNotNull(reasonId))
            if (found == null) {
                onClose()
            } else {
                draft = found
                original = found
            }
        }
        return
    }

    val problems = draft.problems
    val closeWith = rememberClosing(onClose)
    val firstField = rememberFirstField(new = reasonId == null)
    val save = {
        if (problems.isNotEmpty()) {
            tried = true
            firstField.saveFailed(atField = true)
        } else {
            val typed = draft
            closeWith.leaveThrough {
                when (val saving = reasons.save(reasonId, typed)) {
                    is Saving.Saved -> onClose()
                    is Saving.Refused -> if (ReasonProblem.Gone in saving.problems) {
                        onClose()
                    } else {
                        refused = saving.problems
                        closeWith.reopen()
                    }
                }
            }
        }
    }
    // Left without saving: a photo imported for it is let go of.
    val discard = {
        val left = draft
        scope.launchNow { reasons.discard(left, saved) }
        onClose()
    }
    val leave = rememberLeaving(
        closing = closeWith,
        changed = { differsAsSaved(draft, saved, ReasonDraft::cleaned) },
        onSave = save,
        onDiscard = discard,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (reasonId == null) R.string.reason_new else R.string.reason_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = { SaveBar(onSave = save) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReasonPhotoField(
                reasons = reasons,
                reasonId = reasonId,
                photo = draft.photo,
                saved = saved.photo,
                onChange = { photo ->
                    draft = draft.copy(photo = photo)
                    refused = emptySet()
                },
            )
            if (ReasonProblem.PhotoGone in refused) Problem(stringResource(R.string.reason_photo_gone))
            if (ReasonProblem.TooManyPhotos in refused) Problem(stringResource(R.string.reason_photo_limit))
            val empty = tried && ReasonProblem.Empty in problems
            val direction = if (InputText.startsRightToLeft(draft.text)) TextDirection.Rtl else TextDirection.Ltr
            OutlinedTextField(
                value = draft.text,
                onValueChange = { draft = draft.copy(text = it) },
                label = { Text(stringResource(R.string.reason_text)) },
                minLines = 4,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    textDirection = direction,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = empty || ReasonProblem.TextTooLong in problems,
                supportingText = {
                    if (empty) {
                        Text(stringResource(R.string.reason_empty))
                    } else {
                        LengthHint(InputText.lengthOf(draft.cleaned().text), ReasonDraft.TEXT_MAX)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstField.requester),
            )
            LookFields(reasons, draft, onChange = { draft = draft.copy(look = it) }, onOpenLicenses = onOpenLicenses)
        }
    }
}

/**
 * How the reason looks (SPEC.md §18): shown first as it will stand on the board, then its font, size and both colours
 * to choose; "Schwer zu lesen" while its text has too little contrast on its background, which it may keep all the same.
 * The fonts' licences open from here ([onOpenLicenses]).
 */
@Composable
private fun LookFields(reasons: Reasons, draft: ReasonDraft, onChange: (ReasonLook) -> Unit, onOpenLicenses: () -> Unit) {
    val look = draft.look
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reason_look), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.reason_preview), style = MaterialTheme.typography.bodySmall)
        val cleaned = draft.cleaned()
        // Half the width, as on the board.
        Box(Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally)) {
            ReasonAsOnBoard(reasons, cleaned.text, cleaned.photo, look, roomForMenu = false)
        }
        // A warning only: it may be saved all the same.
        if (look.hardToReadIn(standardColors())) {
            Text(stringResource(R.string.reason_hard_to_read), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.reason_font), style = MaterialTheme.typography.titleSmall)
        FontChoice(look.font, onChange = { onChange(look.copy(font = it)) })
        Text(stringResource(R.string.reason_size), style = MaterialTheme.typography.titleSmall)
        SizeChoice(look.size, onChange = { onChange(look.copy(size = it)) })
        val standard = Color(standardColors().background)
        ColorChoice(stringResource(R.string.reason_background), look.background, standard, onChange = { onChange(look.copy(background = it)) })
        ColorChoice(
            stringResource(R.string.reason_text_color),
            look.text,
            Color(standardColors().text),
            onChange = { onChange(look.copy(text = it)) },
        )
        TextButton(onClick = onOpenLicenses) { Text(stringResource(R.string.font_licenses)) }
    }
}

/** What importing a photo came to, to be told under its buttons. */
private enum class PhotoNote(val text: Int, val problem: Boolean) {
    Busy(R.string.reason_photo_busy, false),
    Unreadable(R.string.reason_photo_unreadable, true),
    TooLarge(R.string.reason_photo_too_large, true),
    TooMany(R.string.reason_photo_limit, true),
    NoCamera(R.string.camera_missing, true),
}

/**
 * The photo of the reason [reasonId], or a new one, as [photo]: chosen from the gallery or taken with the phone's camera
 * app, without the app's own camera permission (SPEC.md §15); shown, replaced by choosing anew, or taken off. One
 * imported and replaced or taken off again goes at once; the one [saved] goes only once the reason is saved without it.
 * Once 100 others have a photo, it says so, with nothing to choose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonPhotoField(reasons: Reasons, reasonId: Long?, photo: String?, saved: String?, onChange: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var note by rememberSaveable { mutableStateOf<PhotoNote?>(null) }
    val current by rememberUpdatedState(photo)
    val change by rememberUpdatedState(onChange)
    val allowed by produceState<Boolean?>(null, reasons, reasonId) { value = reasons.photoAllowed(reasonId) }

    /** [file] in place of the photo it had, which goes if it was imported and not saved. */
    fun replaceWith(file: String?) {
        val before = current
        change(file)
        if (before != null && before != saved && before != file) scope.launchNow { reasons.drop(before) }
    }

    /** Shows what importing a photo came to, and takes it if it was added. */
    fun shown(result: AttachmentImport) {
        note = when (result) {
            is AttachmentImport.Added -> {
                replaceWith(result.attachment.file)
                null
            }
            AttachmentImport.TooMany -> PhotoNote.TooMany
            AttachmentImport.TooLarge -> PhotoNote.TooLarge
            AttachmentImport.Unreadable -> PhotoNote.Unreadable
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = PhotoNote.Busy
        scope.launchNow { shown(reasons.importPhoto(uri.toString(), reasonId)) }
    }
    val camera = rememberCameraTrip { taken ->
        note = PhotoNote.Busy
        val bytes = taken.read(Vault.PHOTO_MAX_BYTES)
        shown(bytes?.let { reasons.importPhoto(PickedDocument(Vault.TAKEN_NAME, it), reasonId) } ?: AttachmentImport.Unreadable)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photo?.let { ReasonPhotoImage(reasons, it, Modifier.heightIn(max = PHOTO_MAX_HEIGHT.dp)) }
        val limited = allowed == false && photo == null
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, enabled = allowed != null && !limited) {
                Text(stringResource(R.string.reason_photo_choose))
            }
            OutlinedButton(onClick = { if (!camera.take()) note = PhotoNote.NoCamera }, enabled = allowed != null && !limited) {
                Text(stringResource(R.string.reason_photo_take))
            }
            if (photo != null) {
                OutlinedButton(onClick = { replaceWith(null) }) { Text(stringResource(R.string.reason_photo_remove)) }
            }
        }
        val shown = note
        when {
            limited -> Text(stringResource(R.string.reason_photo_limit), style = MaterialTheme.typography.bodySmall)
            shown == null -> Unit
            shown.problem -> Problem(stringResource(shown.text))
            else -> Text(stringResource(shown.text), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** How high the photo stands in the editor at most, so that the text below stays in sight. */
private const val PHOTO_MAX_HEIGHT = 240
