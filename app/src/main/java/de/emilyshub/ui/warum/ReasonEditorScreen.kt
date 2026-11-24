package de.emilyshub.ui.warum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.ui.differsAsSaved
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.SaveBar
import de.emilyshub.ui.fields.rememberFirstField
import de.emilyshub.ui.rememberClosing
import de.emilyshub.ui.rememberLeaving
import de.emilyshub.warum.ReasonDraft
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
 * Adds or changes a Grund of Mein Warum (SPEC.md §18): its text, plain, up to 500 characters, in the middle as on the
 * board. An empty one is not saved. Leaving with changes asks whether to save them; one deleted meanwhile closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonEditorScreen(reasons: Reasons, target: ReasonEditorTarget, onClose: () -> Unit) {
    val reasonId = (target as? ReasonEditorTarget.Edit)?.reasonId
    // As saved: known at once for a new one, read first for one saved.
    var original by rememberSaveable { mutableStateOf(if (reasonId == null) ReasonDraft() else null) }
    var draft by rememberSaveable { mutableStateOf(ReasonDraft()) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var tried by rememberSaveable { mutableStateOf(false) }

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
            closeWith { reasons.save(reasonId, typed) }
        }
    }
    val leave = rememberLeaving(
        closing = closeWith,
        changed = { differsAsSaved(draft, saved, ReasonDraft::cleaned) },
        onSave = save,
        onDiscard = onClose,
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
        }
    }
}
