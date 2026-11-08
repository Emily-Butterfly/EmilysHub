package de.emilyshub.security

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

/**
 * Writes [bytes] to [file] so that neither a crash nor a power cut can leave it empty or half-written: synced to disk
 * under a temporary name and only then moved into place. For the files that hold sealed keys, without which data is
 * locked for good.
 */
fun writeDurably(file: File, bytes: ByteArray) {
    val temp = File(file.parentFile, "${file.name}.tmp")
    FileOutputStream(temp).use { out ->
        out.write(bytes)
        out.fd.sync()
    }
    Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE)
}
