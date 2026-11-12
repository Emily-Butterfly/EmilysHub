package de.emilyshub.languages

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * The one file the camera app writes a photo for a lesson to (SPEC.md §15), lent to it through the app's FileProvider,
 * so that the app needs no camera permission. The photo lies in it in plain text only until the app has read it: it is
 * deleted at once after; and anything left of it on a later start, once it is older than any photo still on its way
 * back from the camera app, which Android may bring back to an app it ended meanwhile.
 */
object CameraShot {
    /** A new, empty file for the next photo, as the camera app is to be given it. */
    fun prepare(context: Context): Uri {
        val file = fileOf(context).apply {
            parentFile?.mkdirs()
            delete()
            createNewFile()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
    }

    /** Deletes the photo taken, if it is older than [STALE_MILLIS]: what a crash left, not one being taken. */
    fun clearStale(context: Context) {
        val file = fileOf(context)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() >= STALE_MILLIS) clear(context)
    }

    /** Deletes the photo taken, if any. */
    fun clear(context: Context) {
        fileOf(context).parentFile?.deleteRecursively()
    }

    /** An hour: longer than a photo takes to be taken. */
    private const val STALE_MILLIS = 60 * 60 * 1000L

    private fun fileOf(context: Context) = File(File(context.cacheDir, "camera"), "photo.jpg")
}
