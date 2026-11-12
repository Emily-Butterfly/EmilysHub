// Android's own ExifInterface reads the orientation well from Android 7 on, and the app needs Android 11.
@file:Suppress("ExifInterface")

package de.emilyshub.languages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Photos as a lesson keeps them (SPEC.md §15): encoded anew as JPEG, no longer than [MAX_EDGE] at the long edge, the
 * right way up as the camera turned it; nothing of the original's metadata, such as where it was taken, goes along.
 */
object Photos {
    /** The longest edge a photo keeps, in pixels. */
    const val MAX_EDGE = 2000

    private const val QUALITY = 85

    /** [bytes] as a photo is kept; none if they are no picture Android can read. */
    fun shrink(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        // Read no larger than needed: halved while it stays at least as long as it is to be.
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = minOf(1f, MAX_EDGE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationOf(bytes).toFloat())
        }
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        turned.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (turned !== decoded) turned.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    /** [bytes] read as a picture no longer than about [edge] at its long edge, to be shown; none if they are none. */
    fun preview(bytes: ByteArray, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * How far the camera says the picture in [bytes] is to be turned clockwise, in degrees; 0 if it says nothing.
     */
    private fun rotationOf(bytes: ByteArray): Int = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
