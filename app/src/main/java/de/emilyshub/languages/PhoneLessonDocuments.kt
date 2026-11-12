package de.emilyshub.languages

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlin.coroutines.CoroutineContext

/**
 * The phone's own: reads and writes files through Android's file dialog (and the photo the camera app took), and opens
 * PDFs with Android's [PdfRenderer] from memory, so that no decrypted copy ever lies on the disk.
 */
class PhoneLessonDocuments(private val context: Context, private val io: CoroutineContext = Dispatchers.IO) : LessonDocuments {
    private val resolver get() = context.contentResolver

    override suspend fun read(address: String, limit: Int): PickedDocument? = withContext(io) {
        runCatching {
            val uri = address.toUri()
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(CHUNK)
                while (buffer.size() < limit) {
                    val read = input.read(chunk, 0, minOf(CHUNK, limit - buffer.size()))
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return@runCatching null
            PickedDocument(name ?: uri.lastPathSegment.orEmpty(), bytes)
        }.getOrNull()
    }

    override suspend fun write(address: String, bytes: ByteArray): Boolean = withContext(io) {
        runCatching {
            resolver.openOutputStream(address.toUri(), "wt")?.use { it.write(bytes) } != null
        }.getOrDefault(false)
    }

    override fun pdfPages(pdf: ByteArray): Int? = runCatching { PdfInMemory.open(pdf).use { it.pageCount } }.getOrNull()

    private companion object {
        const val CHUNK = 64 * 1024
    }
}

/** PDFs opened with Android's [PdfRenderer] from a file that lives in memory only (memfd), never on the disk. */
object PdfInMemory {
    /** A renderer of [pdf]; close it once done. Fails if it does not open as a PDF. */
    fun open(pdf: ByteArray): PdfRenderer {
        val fd = Os.memfd_create("attachment", 0)
        try {
            FileOutputStream(fd).let { out ->
                out.write(pdf)
                out.flush()
            }
            Os.lseek(fd, 0, OsConstants.SEEK_SET)
            return PdfRenderer(ParcelFileDescriptor.dup(fd))
        } finally {
            Os.close(fd)
        }
    }
}
