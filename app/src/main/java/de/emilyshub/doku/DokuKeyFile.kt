package de.emilyshub.doku

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

/** Where the Doku's sealed keys ([DokuKeys]) are kept: none until the Doku password is set. */
internal class DokuKeyFile(private val file: File) {

    fun read(): DokuKeys? = if (file.exists()) DokuKeys.fromBytes(file.readBytes()) else null

    /**
     * Synced to disk under a temporary name and only then moved into place, so that neither a crash nor a power cut can
     * leave a half-written file behind: without it, the Doku would be lost for good.
     */
    fun write(keys: DokuKeys) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(keys.toBytes())
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE)
    }
}
