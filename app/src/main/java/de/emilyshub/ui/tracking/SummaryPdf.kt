package de.emilyshub.ui.tracking

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import de.emilyshub.languages.Photos
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/** Where the pages of the summary are drawn: a PDF's, or in the tests bitmaps'. */
interface SummaryPages {
    /** Page [number], counting from 1, to draw on, [SummaryPdf.PAGE_WIDTH] × [SummaryPdf.PAGE_HEIGHT] points. */
    fun start(number: Int): Canvas

    /** The page begun last is drawn. */
    fun finish()
}

/**
 * The doctor's summary as a PDF (SPEC.md §17, "Arzt-Zusammenfassung"), drawn with Android's own [PdfDocument], no
 * library: A4 in points, black on white only, so that it reads as well printed in black and white. Text wraps to the
 * page; a table that runs on to the next page has its headers there again; a photo is drawn to fit the width. Each page
 * has its number at the foot, given by the page label.
 */
object SummaryPdf {
    /** A4 in points, 1/72 inch. */
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    private const val MARGIN = 48f
    private const val FOOTER = 24f
    private const val CELL_PADDING = 3f
    private const val GAP = 6f
    private const val PHOTO_MAX_HEIGHT = 340f

    /** Writes [blocks] to [out] as a PDF: the number of its pages. Blocking. */
    fun write(blocks: List<SummaryBlock>, out: OutputStream, pageLabel: (Int) -> String): Int {
        val document = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            val count = layOut(
                blocks,
                object : SummaryPages {
                    override fun start(number: Int): Canvas =
                        document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()).also { page = it }.canvas

                    override fun finish() = document.finishPage(checkNotNull(page))
                },
                pageLabel,
            )
            document.writeTo(out)
            return count
        } finally {
            document.close()
        }
    }

    /** Draws [blocks] on [pages], one after the other from the top: the number of pages drawn, at least one. */
    fun layOut(blocks: List<SummaryBlock>, pages: SummaryPages, pageLabel: (Int) -> String): Int {
        val flow = Pages(pages, pageLabel)
        blocks.forEach { flow.draw(it) }
        flow.finish()
        return flow.count
    }

    private val title = paint(18f, bold = true)
    private val heading = paint(13f, bold = true)
    private val text = paint(10f)
    private val strong = paint(10f, bold = true)
    private val small = paint(8.5f)
    private val cell = paint(8.5f)
    private val headerCell = paint(8.5f, bold = true)
    private val line = Paint().apply {
        color = Color.BLACK
        strokeWidth = 0.6f
        style = Paint.Style.STROKE
    }

    private fun paint(size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    /** The pages as they are drawn, one after the other, from the top. */
    private class Pages(private val pages: SummaryPages, private val pageLabel: (Int) -> String) {
        var count = 0
            private set
        private var page: Canvas? = null
        private var y = 0f
        private val width = PAGE_WIDTH - 2 * MARGIN
        private val bottom = PAGE_HEIGHT - MARGIN - FOOTER

        private val canvas: Canvas get() = page ?: newPage()

        private fun newPage(): Canvas {
            page?.let { finishPage(it) }
            count++
            return pages.start(count).also {
                page = it
                y = MARGIN
            }
        }

        private fun finishPage(done: Canvas) {
            val label = layout(pageLabel(count), small, width)
            done.withTranslation(MARGIN, PAGE_HEIGHT - MARGIN) { label.draw(this) }
            pages.finish()
        }

        /** Room for [height] more on this page, a new one begun if not, unless the page is still empty. */
        private fun room(height: Float) {
            if (page == null || (y + height > bottom && y > MARGIN)) newPage()
        }

        fun finish() {
            if (page == null) newPage()
            page?.let { finishPage(it) }
            page = null
        }

        fun draw(block: SummaryBlock) {
            when (block) {
                is SummaryBlock.Title -> paragraph(block.text, title, before = 0f)
                is SummaryBlock.Heading -> paragraph(block.text, heading, before = 2 * GAP, keepWith = 3 * text.textSize)
                is SummaryBlock.Text -> paragraph(block.text, if (block.strong) strong else if (block.small) small else text, before = GAP / 2)
                is SummaryBlock.Table -> table(block)
                is SummaryBlock.Photo -> photo(block)
            }
        }

        /** [content] in [paint], wrapped, after [before] of space; kept on a page with [keepWith] of what follows. */
        private fun paragraph(content: String, paint: TextPaint, before: Float, keepWith: Float = 0f) {
            val laid = layout(content, paint, width)
            room(before + laid.height + keepWith)
            if (y > MARGIN) y += before
            // A paragraph longer than a page is drawn line by line.
            for (index in 0 until laid.lineCount) {
                val top = laid.getLineTop(index).toFloat()
                val height = laid.getLineBottom(index) - top
                room(height)
                canvas.withTranslation(MARGIN, y - top) {
                    clipRect(0f, top, this@Pages.width, top + height)
                    laid.draw(this)
                }
                y += height
            }
        }

        private fun table(table: SummaryBlock.Table) {
            val total = table.weights.sum()
            val widths = table.weights.map { it / total * width }
            fun cells(values: List<String>, paint: TextPaint) = values.mapIndexed { index, value -> layout(value, paint, widths[index] - 2 * CELL_PADDING) }
            fun heightOf(laid: List<StaticLayout>) = (laid.maxOfOrNull { it.height } ?: 0) + 2 * CELL_PADDING
            val headers = cells(table.headers, headerCell)
            val headerHeight = heightOf(headers)
            fun row(laid: List<StaticLayout>, height: Float) {
                var x = MARGIN
                laid.forEachIndexed { index, layout ->
                    canvas.withTranslation(x + CELL_PADDING, y + CELL_PADDING) { layout.draw(this) }
                    x += widths[index]
                }
                canvas.drawRect(RectF(MARGIN, y, MARGIN + width, y + height), line)
                var edge = MARGIN
                widths.dropLast(1).forEach { w ->
                    edge += w
                    canvas.drawLine(edge, y, edge, y + height, line)
                }
                y += height
            }
            y += GAP / 2
            room(headerHeight + heightOf(cells(table.rows.firstOrNull() ?: table.headers, cell)))
            row(headers, headerHeight)
            table.rows.forEach { values ->
                val laid = cells(values, cell)
                val height = heightOf(laid)
                if (y + height > bottom) {
                    newPage()
                    row(headers, headerHeight)
                }
                row(laid, height)
            }
        }

        private fun photo(photo: SummaryBlock.Photo) {
            val bitmap = Photos.preview(photo.jpeg, (width * 2).toInt()) ?: return
            try {
                val scale = min(width / bitmap.width, PHOTO_MAX_HEIGHT / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val caption = layout(photo.caption, small, width)
                room(GAP + h + caption.height)
                y += GAP
                canvas.drawBitmap(bitmap, null, RectF(MARGIN, y, MARGIN + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                y += h
                canvas.withTranslation(MARGIN, y) { caption.draw(this) }
                y += caption.height
            } finally {
                bitmap.recycle()
            }
        }
    }
}
