package de.emilyshub.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Stands in for a folder chosen in Android's dialog, which the Storage Access Framework only gives on a device: its
 * [files] by name, whether its access is [kept], [failing] to write, as when the folder was deleted meanwhile, and
 * [breaking] off while writing, as when the storage is full, leaving what was written so far.
 */
class FakeBackupFolder(override val name: String = "Backups") : BackupFolder {
    val files = linkedMapOf<String, ByteArray>()
    var kept = false
    var failing = false
    var breaking = false

    override fun keepAccess() {
        kept = true
    }

    override fun releaseAccess() {
        kept = false
    }

    override fun fileNames(): List<String> = files.keys.toList()

    override fun write(fileName: String): OutputStream {
        if (failing) throw IOException("The folder is gone")
        return object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (!breaking) return super.write(b, off, len)
                super.write(b, off, len / 2)
                throw IOException("No space left")
            }

            override fun close() {
                files[fileName] = toByteArray()
            }
        }
    }

    override fun delete(fileName: String) {
        files.remove(fileName)
    }
}
