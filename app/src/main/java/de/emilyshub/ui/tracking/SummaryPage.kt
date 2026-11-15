package de.emilyshub.ui.tracking

import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.languages.Photos
import de.emilyshub.tracking.SummaryWeeks
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingBaseData
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.ui.island.KeepIslandDraft
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.rememberIslandTrip
import de.emilyshub.ui.island.touchesIsland
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How saving the PDF went, shown under the button. */
private sealed interface SaveNote {
    data class Saved(val pages: Int) : SaveNote

    data object Failed : SaveNote
}

/**
 * The doctor's summary (SPEC.md §17, "Arzt-Zusammenfassung"): its weeks from 1 to as many as are tracked, 4 unless
 * fewer are; the photos of the events only if chosen; one's own questions in Markdown, kept for next time; a preview
 * of the summary as the PDF has it. "Als PDF speichern" first says that the PDF is not encrypted and to delete it after
 * the appointment, then asks for a folder and a name (Storage Access Framework), Tracking staying open for that trip, and
 * writes the PDF straight there: no copy is kept in the app, and nothing is shared.
 */
@Composable
internal fun SummaryPage(tracking: Tracking, modules: TrackingModules, contentPadding: PaddingValues) {
    val evaluation = modules.evaluation
    val tracked = rememberIslandLoad(tracking) { evaluation.weeksTracked() } ?: return
    var loadedQuestions by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { loadedQuestions = evaluation.questions() }
    val original = loadedQuestions ?: return
    if (tracked == 0) {
        Text(stringResource(R.string.tracking_summary_nothing), modifier = Modifier.padding(contentPadding).padding(16.dp))
        return
    }
    SummaryForm(tracking, modules, tracked, original, contentPadding)
}

@Composable
private fun SummaryForm(tracking: Tracking, modules: TrackingModules, tracked: Int, original: String, contentPadding: PaddingValues) {
    val evaluation = modules.evaluation
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // Not saveable, as all that is typed in Tracking: nothing of it goes into the saved state.
    var weeks by remember { mutableIntStateOf(SummaryWeeks.default(tracked)) }
    var withPhotos by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf(original) }
    var savedQuestions by remember { mutableStateOf(original) }
    var warning by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<SaveNote?>(null) }
    KeepIslandDraft(tracking, TrackingPage.Summary, modules.writes) {
        val typed = questions
        if (typed == savedQuestions) null else ({ database -> evaluation.writeQuestions(database, typed) })
    }
    val summary = rememberIslandLoad(tracking, weeks, withPhotos) { evaluation.summary(weeks, withPhotos) }
    val blocks = remember(summary, questions) { summary?.let { summaryBlocks(resources, it, questions, evaluation.zone(), evaluation.today()) } }

    val trip = rememberIslandTrip(tracking)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF)) { uri ->
        trip.end()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val typed = questions
            evaluation.saveQuestions(typed)
            savedQuestions = typed
            // Made anew now, from what Tracking holds; none once it locked meanwhile, and the empty file goes.
            val made = evaluation.summary(weeks, withPhotos)?.let { summaryBlocks(resources, it, typed, evaluation.zone(), evaluation.today()) }
            note = withContext(Dispatchers.IO) { writePdf(context, uri, made) { resources.getString(R.string.tracking_summary_page, it) } }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.tracking_summary_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.tracking_summary_weeks_label), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { weeks = SummaryWeeks.within(weeks - 1, tracked) }, enabled = weeks > 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tracking_summary_fewer))
            }
            Text(pluralStringResource(R.plurals.tracking_summary_weeks, weeks, weeks), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { weeks = SummaryWeeks.within(weeks + 1, tracked) }, enabled = weeks < tracked) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tracking_summary_more))
            }
            Text(stringResource(R.string.tracking_summary_weeks_tracked, tracked), style = MaterialTheme.typography.bodySmall)
        }
        CheckField(stringResource(R.string.tracking_summary_with_photos), withPhotos, { withPhotos = it })
        OutlinedTextField(
            value = questions,
            onValueChange = { typed -> if (typed.length <= TrackingBaseData.TEXT_MAX) questions = typed },
            label = { Text(stringResource(R.string.tracking_summary_questions)) },
            supportingText = { Text(stringResource(R.string.tracking_summary_questions_hint)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { warning = true }, enabled = blocks != null) { Text(stringResource(R.string.tracking_summary_save)) }
        when (val shown = note) {
            is SaveNote.Saved -> Text(pluralStringResource(R.plurals.tracking_summary_saved, shown.pages, shown.pages), style = MaterialTheme.typography.bodySmall)
            SaveNote.Failed -> Text(stringResource(R.string.tracking_summary_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            null -> Unit
        }
        HorizontalDivider()
        Text(stringResource(R.string.tracking_summary_preview), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        blocks?.forEach { SummaryPreviewBlock(it) }
    }

    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            title = { Text(stringResource(R.string.tracking_summary_warning_title)) },
            text = { Text(stringResource(R.string.tracking_summary_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    warning = false
                    note = null
                    trip.start()
                    try {
                        save.launch(resources.getString(R.string.tracking_summary_file, evaluation.today().toString()))
                    } catch (e: android.content.ActivityNotFoundException) {
                        trip.end()
                        note = SaveNote.Failed
                    }
                }) { Text(stringResource(R.string.tracking_summary_warning_confirm)) }
            },
            dismissButton = { TextButton(onClick = { warning = false }) { Text(stringResource(R.string.cancel)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/**
 * Writes the PDF of [blocks] to [uri], the file just made in the folder chosen, so empty: "w", as not every provider
 * takes "wt"; with none, as Tracking locked meanwhile,
 * or failing, that file is deleted again, so that no empty or broken one is left.
 */
private fun writePdf(context: android.content.Context, uri: Uri, blocks: List<SummaryBlock>?, pageLabel: (Int) -> String): SaveNote? {
    val resolver = context.contentResolver
    val written = blocks?.let {
        runCatching { checkNotNull(resolver.openOutputStream(uri, "w")).use { out -> SummaryPdf.write(it, out, pageLabel) } }.getOrNull()
    }
    if (written == null) runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    return when {
        written != null -> SaveNote.Saved(written)
        blocks == null -> null
        else -> SaveNote.Failed
    }
}

/** A block of the summary as the preview shows it. */
@Composable
private fun SummaryPreviewBlock(block: SummaryBlock) {
    when (block) {
        is SummaryBlock.Title -> Text(block.text, style = MaterialTheme.typography.titleLarge)
        is SummaryBlock.Heading -> Text(block.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        is SummaryBlock.Text -> Text(
            block.text,
            style = if (block.small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (block.strong) FontWeight.Bold else null,
        )
        is SummaryBlock.Table -> PreviewTable(block)
        is SummaryBlock.Photo -> PreviewPhoto(block)
    }
}

@Composable
private fun PreviewTable(table: SummaryBlock.Table) {
    val border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    Column(Modifier.fillMaxWidth().border(border)) {
        (listOf(table.headers) + table.rows).forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { column, value ->
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (index == 0) FontWeight.Bold else null,
                        modifier = Modifier
                            .weight(table.weights[column])
                            .fillMaxHeight()
                            .border(border)
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPhoto(photo: SummaryBlock.Photo) {
    // By its bytes, which stay the same while only the questions change, so that typing draws no photo anew.
    val bitmap by produceState<Bitmap?>(null, photo.jpeg) { value = withContext(Dispatchers.Default) { Photos.preview(photo.jpeg, PREVIEW_EDGE) } }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = photo.caption, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(200.dp)) }
    Text(photo.caption, style = MaterialTheme.typography.bodySmall)
}

private const val PDF = "application/pdf"

/** The pixels a photo is drawn with in the preview: small, as there may be many. */
private const val PREVIEW_EDGE = 480
