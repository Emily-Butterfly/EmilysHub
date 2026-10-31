package de.emilyshub.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.OutputStream

/** A folder chosen in Android's dialog (ACTION_OPEN_DOCUMENT_TREE), at its [tree] address, as the Storage Access Framework gives it. */
class DocumentFolder(context: Context, private val tree: Uri) : BackupFolder {
    private val resolver = context.contentResolver
    private val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    private val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    override val name: String?
        get() = resolver.query(folder, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    override fun keepAccess() = resolver.takePersistableUriPermission(tree, ACCESS)

    override fun releaseAccess() {
        try {
            resolver.releasePersistableUriPermission(tree, ACCESS)
        } catch (_: SecurityException) {
            // Gone already.
        }
    }

    override fun fileNames(): List<String> = documents().keys.toList()

    override fun write(fileName: String): OutputStream {
        val document = documents()[fileName]
            ?: checkNotNull(DocumentsContract.createDocument(resolver, folder, MIME_TYPE, fileName)) { "Not created" }
        return checkNotNull(resolver.openOutputStream(document, "wt")) { "Not opened" }
    }

    override fun delete(fileName: String) {
        documents()[fileName]?.let { DocumentsContract.deleteDocument(resolver, it) }
    }

    /** The documents in the folder by their names. */
    private fun documents(): Map<String, Uri> {
        val columns = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID)
        val cursor = checkNotNull(resolver.query(children, columns, null, null)) { "The folder cannot be read" }
        return cursor.use {
            buildMap {
                while (it.moveToNext()) put(it.getString(0), DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(1)))
            }
        }
    }

    private companion object {
        const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Android knows no type for a backup of EmilysHub. */
        const val MIME_TYPE = "application/octet-stream"
    }
}
