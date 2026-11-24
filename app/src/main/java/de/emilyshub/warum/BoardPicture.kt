package de.emilyshub.warum

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** The design an image of the board is drawn in, as the app shows it: what "Standard" is, and the colour of the [page]. */
data class BoardTheme(val standard: StandardColors, val page: Int)

/**
 * A Grund as it is drawn: its text, its look, and its photo's bytes, decrypted, if it has one; read for the image only
 * and let go of after.
 */
class DrawnReason(val text: String, val look: ReasonLook, val photo: ByteArray?)

/**
 * Draws the board as images (SPEC.md §18): [WIDTH] pixels wide, two columns as on the screen, each Grund as it stands
 * there, in its font ([typefaceOf]; characters a font lacks in a fitting one of the phone's), size and colours, right
 * to left if it begins so; split by [BoardPages] at [limit]. In memory only: the PNG is never written to the app's own
 * storage.
 */
class BoardPicture(private val typefaceOf: (ReasonFont) -> Typeface, private val limit: Int = LIMIT) {
    private val pages = BoardPages(GAP, PADDING, limit)

    /** How high [reason] stands in a column; a photo's height is read from its bytes without drawing it. */
    fun heightOf(reason: DrawnReason, theme: BoardTheme): Int {
        val photo = reason.photo?.let(::photoHeight) ?: 0
        val text = textLayout(reason, theme)?.let { it.height + 2 * textPadding(reason) } ?: 0
        return maxOf(photo + text, 1)
    }

    /** The Gründe as high as [heights] split into images, by their ranges. */
    fun split(heights: List<Int>): List<IntRange> = pages.split(heights)

    /** [reasons], as high as [heights], drawn as one image, as PNG. */
    fun draw(reasons: List<DrawnReason>, heights: List<Int>, theme: BoardTheme): ByteArray {
        val layout = pages.layout(heights)
        val bitmap = Bitmap.createBitmap(WIDTH, layout.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(theme.page)
            reasons.forEachIndexed { index, reason ->
                val spot = layout.spots[index]
                val left = PADDING + spot.column * (COLUMN + GAP)
                drawReason(canvas, reason, RectF(left.toFloat(), spot.top.toFloat(), (left + COLUMN).toFloat(), (spot.top + heights[index]).toFloat()), theme)
            }
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawReason(canvas: Canvas, reason: DrawnReason, bounds: RectF, theme: BoardTheme) {
        val corners = Path().apply { addRoundRect(bounds, CORNER, CORNER, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(corners)
        canvas.drawColor(reason.look.backgroundIn(theme.standard))
        var top = bounds.top
        reason.photo?.let { bytes ->
            val height = photoHeight(bytes) ?: return@let
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { photo ->
                val target = Rect(bounds.left.roundToInt(), top.roundToInt(), bounds.right.roundToInt(), (top + height).roundToInt())
                canvas.drawBitmap(photo, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
                photo.recycle()
            }
            top += height
        }
        textLayout(reason, theme)?.let { text ->
            canvas.translate(bounds.left + TEXT_SIDE, top + textPadding(reason))
            text.draw(canvas)
        }
        canvas.restore()
    }

    /** How high a photo in [bytes] is at the column's width; none if it is no picture. */
    private fun photoHeight(bytes: ByteArray): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return (COLUMN.toFloat() * bounds.outHeight / bounds.outWidth).roundToInt()
    }

    private fun textLayout(reason: DrawnReason, theme: BoardTheme): StaticLayout? {
        if (reason.text.isEmpty()) return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaceOf(reason.look.font)
            textSize = when (reason.look.size) {
                ReasonSize.Small -> SMALL
                ReasonSize.Medium -> MEDIUM
                ReasonSize.Large -> LARGE
            }
            color = reason.look.textIn(theme.standard)
        }
        return StaticLayout.Builder.obtain(reason.text, 0, reason.text.length, paint, COLUMN - 2 * TEXT_SIDE)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(0f, LINE_SPACING)
            .build()
    }

    private fun textPadding(reason: DrawnReason) = if (reason.photo == null) TEXT_ALONE else TEXT_STRIP

    companion object {
        /** How wide an image is (SPEC.md §18). */
        const val WIDTH = 1080

        /** About how high an image may be before the board goes on in the next (SPEC.md §18). */
        const val LIMIT = 16_000

        // As the board on a phone's screen, at the width of an image: about 2.6 pixels for each dp there.
        private const val PADDING = 48
        private const val GAP = 24
        private const val COLUMN = (WIDTH - 2 * PADDING - GAP) / 2
        private const val CORNER = 30f
        private const val TEXT_SIDE = 40
        private const val TEXT_ALONE = 52
        private const val TEXT_STRIP = 32
        private const val SMALL = 36f
        private const val MEDIUM = 47f
        private const val LARGE = 62f
        private const val LINE_SPACING = 1.3f
    }
}
