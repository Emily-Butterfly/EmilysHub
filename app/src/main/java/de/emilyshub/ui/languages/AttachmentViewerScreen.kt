package de.emilyshub.ui.languages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import de.emilyshub.R
import de.emilyshub.languages.AttachmentKind
import de.emilyshub.languages.AttachmentRef
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.PdfInMemory
import de.emilyshub.ui.launchNow

/**
 * An attachment of a lesson in full (SPEC.md §15), in the app and nowhere else: a photo to zoom with two fingers, a PDF
 * page by page as Android draws it, from memory only. "Exportieren" saves a decrypted copy where Android's file dialog
 * says; there is no sharing with other apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentViewerScreen(files: LessonFiles, attachment: AttachmentRef, onBack: () -> Unit) {
    val content by produceState<Loaded>(Loaded.Reading, attachment.file) {
        value = files.content(attachment)?.let(Loaded::Read) ?: Loaded.Unreadable
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val exported = stringResource(R.string.attachment_exported)
    val failed = stringResource(R.string.attachment_export_failed)
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(if (attachment.kind == AttachmentKind.Pdf) "application/pdf" else "image/jpeg"),
    ) { uri ->
        if (uri != null) {
            scope.launchNow { snackbar.showSnackbar(if (files.export(attachment, uri.toString())) exported else failed) }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    TextButton(onClick = { export.launch(attachment.name) }) { Text(stringResource(R.string.attachment_export)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { contentPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val shown = content) {
                Loaded.Reading -> Unit
                Loaded.Unreadable -> Unreadable()
                is Loaded.Read -> when (attachment.kind) {
                    AttachmentKind.Photo -> ZoomablePhoto(shown.bytes, attachment.name)
                    AttachmentKind.Pdf -> PdfPages(shown.bytes)
                }
            }
        }
    }
}

/** What is shown: not read yet, its content, or that it cannot be read. */
private sealed interface Loaded {
    data object Reading : Loaded

    class Read(val bytes: ByteArray) : Loaded

    data object Unreadable : Loaded
}

@Composable
private fun Unreadable() {
    Text(
        stringResource(R.string.attachment_unreadable),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/** The photo in [bytes], to zoom into with two fingers, from its whole size up to five times it, and to move about. */
@Composable
private fun ZoomablePhoto(bytes: ByteArray, name: String) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bitmap == null) {
        Unreadable()
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bitmap.asImageBitmap(),
        contentDescription = name,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** The PDF in [bytes], its pages one under the other, each as wide as the screen; drawn by Android from memory. */
@Composable
private fun PdfPages(bytes: ByteArray) {
    val renderer = remember(bytes) { runCatching { PdfInMemory.open(bytes) }.getOrNull() }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    if (renderer == null) {
        Unreadable()
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }
        val count = renderer.pageCount
        LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(count) { index ->
                val label = stringResource(R.string.attachment_page, index + 1, count)
                val page = remember(renderer, index, width) { render(renderer, index, width) }
                Image(
                    page.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = label },
                )
            }
        }
    }
}

/** Page [index] of [renderer], drawn [width] pixels wide on white. */
private fun render(renderer: PdfRenderer, index: Int, width: Int): Bitmap = synchronized(renderer) {
    renderer.openPage(index).use { page ->
        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        createBitmap(width, height).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }
}

private const val MAX_ZOOM = 5f
