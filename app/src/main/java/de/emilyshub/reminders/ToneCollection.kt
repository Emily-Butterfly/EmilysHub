package de.emilyshub.reminders

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext

/**
 * The phone's tones as the app chooses from them (SPEC.md §3), and audio files of one's own it brings into them
 * ([add]): copied into the phone's tones, so that Android plays them in notifications too, shows them in its choice
 * of tones, and keeps them once the file itself is deleted. The same file is copied once, however often it is chosen;
 * which copy each file has is kept on the device in [settings], as the copies are, not in backups.
 */
class ToneCollection(
    private val library: ToneLibrary,
    private val files: ToneFiles,
    private val settings: () -> SettingsDao,
    private val io: CoroutineContext = Dispatchers.IO,
) : ToneLibrary by library {

    /**
     * Brings the audio [file], as Android's file dialog gives it, into the phone's tones as one of [kind], or finds the
     * copy made of it before; turns down one larger than [MAX_BYTES], or that does not play as audio.
     */
    suspend fun add(file: String, kind: ToneKind): ToneAdding {
        val picked = files.read(file, limit = MAX_BYTES + 1) ?: return ToneAdding.NotAudio
        if (picked.bytes.size > MAX_BYTES) return ToneAdding.TooLarge
        if (!picked.playable) return ToneAdding.NotAudio
        val key = COPY_PREFIX + fingerprintOf(picked.bytes)
        val earlier = withContext(io) { settings().get(key) }
        if (earlier != null && library.exists(earlier)) return ToneAdding.Added(Tone.Of(earlier))
        val copy = files.copy(picked, kind) ?: return ToneAdding.Failed
        withContext(io) { settings().put(Setting(key, copy)) }
        return ToneAdding.Added(Tone.Of(copy))
    }

    private fun fingerprintOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        /** The largest file taken: 20 MB. */
        const val MAX_BYTES = 20 * 1024 * 1024

        private const val COPY_PREFIX = "tone-copy:"
    }
}

/** What adding an audio file of one's own came to. */
sealed interface ToneAdding {
    /** It is one of the phone's tones now, [tone]. */
    data class Added(val tone: Tone.Of) : ToneAdding

    /** It does not play as audio, or cannot be read. */
    data object NotAudio : ToneAdding

    /** It is larger than [ToneCollection.MAX_BYTES]. */
    data object TooLarge : ToneAdding

    /** Android did not take the copy. */
    data object Failed : ToneAdding
}

/** What a tone of one's own is for, as the phone's tones are sorted: alarms, notifications, or both, for an entry. */
enum class ToneKind { Any, Alarm, Notification }

/** A file picked to be a tone: its [name] with its ending, its [mimeType], its [bytes], and whether it is [playable]. */
data class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray, val playable: Boolean) {
    /** As the phone's tones name it: without its ending. */
    val title: String get() = name.substringBeforeLast('.').ifBlank { name }
}

/** Files as Android's file dialog gives them, and the phone's tones they are copied into. */
interface ToneFiles {
    /** The file, at most [limit] bytes of it; none if it cannot be read. */
    suspend fun read(file: String, limit: Int): PickedFile?

    /** Copies [file] into the phone's tones as one of [kind]; its address there, or none if Android did not take it. */
    suspend fun copy(file: PickedFile, kind: ToneKind): String?
}

/**
 * The phone's own: reads a file through Android's file dialog and copies it into the phone's media, in "Alarms" or
 * "Notifications", marked as a tone of its kind. Without a permission, as the app adds to the media rather than reads them.
 */
class PhoneToneFiles(private val context: Context, private val io: CoroutineContext = Dispatchers.IO) : ToneFiles {
    private val resolver get() = context.contentResolver

    override suspend fun read(file: String, limit: Int): PickedFile? = withContext(io) {
        runCatching {
            val uri = file.toUri()
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            val mimeType = resolver.getType(uri)
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(CHUNK)
                while (buffer.size() < limit) {
                    val read = input.read(chunk, 0, minOf(CHUNK, limit - buffer.size()))
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return@runCatching null
            val playable = mimeType?.startsWith("audio/") == true && hasAudio(file)
            PickedFile(name ?: DEFAULT_NAME, mimeType ?: "audio/*", bytes, playable)
        }.getOrNull()
    }

    override suspend fun copy(file: PickedFile, kind: ToneKind): String? = withContext(io) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.TITLE, file.title)
            put(MediaStore.Audio.Media.MIME_TYPE, file.mimeType)
            val folder = if (kind == ToneKind.Notification) Environment.DIRECTORY_NOTIFICATIONS else Environment.DIRECTORY_ALARMS
            put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
            put(MediaStore.Audio.Media.IS_ALARM, kind != ToneKind.Notification)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, kind != ToneKind.Alarm)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext null
        runCatching {
            checkNotNull(resolver.openOutputStream(target)).use { it.write(file.bytes) }
            resolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            target.toString()
        }.getOrElse {
            // Half a tone is none: it goes again.
            runCatching { resolver.delete(target, null, null) }
            null
        }
    }

    /** Whether Android finds audio in the file it can play. */
    private fun hasAudio(file: String): Boolean = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, file.toUri())
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        }
    }.getOrDefault(false)

    private companion object {
        const val CHUNK = 64 * 1024
        const val DEFAULT_NAME = "Eigener Ton"
    }
}
