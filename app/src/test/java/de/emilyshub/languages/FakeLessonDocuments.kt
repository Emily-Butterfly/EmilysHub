package de.emilyshub.languages

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.crypto.KeyGenerator

/**
 * Files as the tests put them in Android's file dialog ([files], by address) and as the app writes them there
 * ([written]); a PDF opens if it starts as one, as the tests have no [android.graphics.pdf.PdfRenderer] to ask.
 */
class FakeLessonDocuments : LessonDocuments {
    val files = mutableMapOf<String, PickedDocument>()
    val written = mutableMapOf<String, ByteArray>()

    /** Puts a file named [name] holding [bytes] at an address of its own, which it returns. */
    fun put(name: String, bytes: ByteArray): String = "content://test/${files.size}/$name".also { files[it] = PickedDocument(name, bytes) }

    override suspend fun read(address: String, limit: Int): PickedDocument? =
        files[address]?.let { PickedDocument(it.name, it.bytes.copyOf(minOf(limit, it.bytes.size))) }

    override suspend fun write(address: String, bytes: ByteArray): Boolean {
        written[address] = bytes
        return true
    }

    override fun pdfPages(pdf: ByteArray): Int? = if (String(pdf.copyOf(minOf(5, pdf.size)), Charsets.ISO_8859_1) == "%PDF-") 1 else null
}

/** A store in a folder of its own under the app's files, with a key of its own, as the tests need one. */
fun testAttachmentStore(folder: String = "attachments-${System.nanoTime()}"): AttachmentStore {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    val dir = File(ApplicationProvider.getApplicationContext<Context>().filesDir, folder)
    return AttachmentStore({ dir }, { key })
}
