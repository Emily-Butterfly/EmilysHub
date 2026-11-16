package de.emilyshub.ui.tracking

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.emilyshub.R
import de.emilyshub.languages.CameraShot
import de.emilyshub.languages.Photos
import de.emilyshub.tracking.EventPhotos
import de.emilyshub.tracking.PhotoAdd
import de.emilyshub.tracking.PhotoInfo
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingEvents
import de.emilyshub.ui.dateAndTime
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.rememberIslandTrip
import de.emilyshub.ui.island.touchesIsland
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What adding a photo came to, shown under the button. */
private enum class PhotoNote { Busy, TooMany, Unreadable, NotWritten }

/**
 * The photos of event [eventId] (SPEC.md §17, "Ereignisse"): "+ Foto" lends the camera app the one file it writes the
 * photo to, without a camera permission; Tracking stays open for that trip ([rememberIslandTrip]). Back, the photo is
 * read, kept encrypted in Tracking at once and the file deleted, so that it is never in the gallery. Each shown small,
 * full-screen once tapped, and deleted from there after asking.
 */
@Composable
internal fun EventPhotosSection(tracking: Tracking, events: TrackingEvents, eventId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = rememberIslandLoad(tracking, eventId) { events.photos(eventId) }.orEmpty()
    var note by remember { mutableStateOf<PhotoNote?>(null) }
    var shown by remember { mutableStateOf<PhotoInfo?>(null) }
    val trip = rememberIslandTrip(tracking)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        trip.returned()
        if (!taken) {
            trip.end()
            note = null
            CameraShot.clear(context)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val added = try {
                val bytes = withContext(Dispatchers.IO) { CameraShot.read(context, EventPhotos.MAX_TAKEN_BYTES) }
                if (bytes == null) PhotoAdd.Unreadable else events.addPhoto(eventId, bytes)
            } finally {
                // Kept first, then the trip ends, which locks Tracking if it took too long.
                trip.end()
                // It lay there in plain text only until read.
                withContext(Dispatchers.IO) { CameraShot.clear(context) }
            }
            note = when (added) {
                is PhotoAdd.Added, PhotoAdd.Locked, PhotoAdd.NoEvent -> null
                PhotoAdd.TooMany -> PhotoNote.TooMany
                PhotoAdd.Unreadable -> PhotoNote.Unreadable
                PhotoAdd.NotWritten -> PhotoNote.NotWritten
            }
        }
    }

    EntryHeading(stringResource(R.string.tracking_photos_heading))
    val full = photos.size >= EventPhotos.MAX_PER_EVENT
    OutlinedButton(
        onClick = {
            note = PhotoNote.Busy
            val uri = CameraShot.prepare(context)
            trip.start()
            try {
                camera.launch(uri)
            } catch (e: android.content.ActivityNotFoundException) {
                trip.end()
                CameraShot.clear(context)
                note = PhotoNote.Unreadable
            }
        },
        enabled = !full && note != PhotoNote.Busy,
    ) { Text(stringResource(R.string.tracking_photo_add)) }
    val noteText = when {
        full -> stringResource(R.string.tracking_photo_too_many, EventPhotos.MAX_PER_EVENT)
        note == PhotoNote.Busy -> stringResource(R.string.tracking_photo_busy)
        note == PhotoNote.TooMany -> stringResource(R.string.tracking_photo_too_many, EventPhotos.MAX_PER_EVENT)
        note == PhotoNote.Unreadable -> stringResource(R.string.tracking_photo_unreadable)
        note == PhotoNote.NotWritten -> stringResource(R.string.tracking_photo_not_written)
        else -> stringResource(R.string.tracking_photo_hint)
    }
    Text(noteText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (photos.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { photo -> PhotoTile(tracking, events, photo, onOpen = { shown = photo }) }
        }
    }
    shown?.let { photo ->
        PhotoViewer(
            tracking,
            events,
            photo,
            onDelete = {
                shown = null
                scope.launch { events.deletePhoto(photo.id) }
            },
            onClose = { shown = null },
        )
    }
}

/** A photo shown small; tapped, opened. */
@Composable
private fun PhotoTile(tracking: Tracking, events: TrackingEvents, photo: PhotoInfo, onOpen: () -> Unit) {
    val label = stringResource(R.string.tracking_photo_description, photo.takenAt.atZone(events.zone()).dateAndTime())
    val preview = rememberPhoto(tracking, events, photo, TILE_EDGE)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(TILE.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .semantics { contentDescription = label },
    ) {
        preview?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Photo [photo] over the whole screen, still under Tracking's FLAG_SECURE; deleted after asking ([onDelete]). */
@Composable
private fun PhotoViewer(tracking: Tracking, events: TrackingEvents, photo: PhotoInfo, onDelete: () -> Unit, onClose: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    val label = stringResource(R.string.tracking_photo_description, photo.takenAt.atZone(events.zone()).dateAndTime())
    val bitmap = rememberPhoto(tracking, events, photo, FULL_EDGE)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .touchesIsland(tracking),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.tracking_photo_delete), color = Color.White) }
                Button(onClick = onClose) { Text(stringResource(R.string.tracking_photo_close)) }
            }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.tracking_photo_delete_title)) },
            text = { Text(stringResource(R.string.tracking_photo_delete_text)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.tracking_event_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.tracking_event_cancel)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** [photo] decrypted and drawn about [edge] pixels at its long edge, in memory only; none until then, or once locked. */
@Composable
private fun rememberPhoto(tracking: Tracking, events: TrackingEvents, photo: PhotoInfo, edge: Int): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, tracking, photo.id, edge) {
        val jpeg = events.photo(photo.id) ?: return@produceState
        value = withContext(Dispatchers.Default) { Photos.preview(jpeg, edge) }
    }
    return bitmap
}

/** The size of a photo shown small, in dp, and the pixels it is drawn with. */
private const val TILE = 96
private const val TILE_EDGE = 320

/** The pixels a photo is drawn with over the whole screen. */
private const val FULL_EDGE = EventPhotos.MAX_EDGE
