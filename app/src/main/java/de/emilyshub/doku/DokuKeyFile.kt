package de.emilyshub.doku

import de.emilyshub.security.writeDurably
import java.io.File

/** Where the Doku's sealed keys ([DokuKeys]) are kept: none until the Doku password is set. */
internal class DokuKeyFile(private val file: File) {

    /** The sealed keys, or null before the Doku password is set. */
    fun read(): DokuKeys? = if (file.exists()) DokuKeys.fromBytes(file.readBytes()) else null

    /** Without the file the Doku would be lost for good, so it is written so that nothing can leave it half-written. */
    fun write(keys: DokuKeys) = writeDurably(file, keys.toBytes())
}
