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
        val decoded = decode(bytes, MAX_EDGE) ?: return null
        val scale = minOf(1f, MAX_EDGE.toFloat() / max(decoded.width, decoded.height))
        val matrix = orientationOf(bytes).apply { postScale(scale, scale) }
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        turned.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (turned !== decoded) turned.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    /** [bytes] read as a picture no longer than about [edge] at its long edge, to be shown; none if they are none. */
    fun preview(bytes: ByteArray, edge: Int): Bitmap? = decode(bytes, edge)

    /**
     * [bytes] read as a picture no smaller than needed for [edge] at its long edge: halved while it stays at least as
     * long as that; none if they are no picture.
     */
    private fun decode(bytes: ByteArray, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * How the camera says the picture in [bytes] is to be turned, and mirrored as front cameras may, to be the right way
     * round; as it is if it says nothing.
     */
    private fun orientationOf(bytes: ByteArray): Matrix = Matrix().apply {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> setRotate(90f).also { postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> setRotate(-90f).also { postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
}
