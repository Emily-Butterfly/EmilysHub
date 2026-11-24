package de.emilyshub.ui.warum

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.languages.Photos
import de.emilyshub.warum.Reasons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A reason's photo kept under [file], as wide as there is room and as high as it is then, read and decrypted off the
 * main thread; until it is, a space of its own.
 */
@Composable
internal fun ReasonPhotoImage(reasons: Reasons, file: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, reasons, file) {
        value = reasons.photo(file)?.let { withContext(Dispatchers.Default) { Photos.preview(it, PREVIEW_EDGE) } }
    }
    val shown = bitmap
    if (shown == null) {
        Box(modifier.fillMaxWidth().height(LOADING_HEIGHT.dp))
    } else {
        Image(
            shown.asImageBitmap(),
            contentDescription = stringResource(R.string.reason_photo),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(shown.width.toFloat() / shown.height),
        )
    }
}

/**
 * The photo in [bytes] as large as there is room, to zoom into with two fingers up to five times and to move about while
 * zoomed. At its whole size, one finger is left to the pager, which swipes to the next reason.
 */
@Composable
internal fun ZoomablePhoto(bytes: ByteArray) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } ?: return
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.reason_photo),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
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

private const val MAX_ZOOM = 5f

/** As large as a tile or the editor needs it: half the width of a phone's screen, sharp. */
private const val PREVIEW_EDGE = 720

private const val LOADING_HEIGHT = 120
