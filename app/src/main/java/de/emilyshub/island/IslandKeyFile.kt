package de.emilyshub.island

import de.emilyshub.security.writeDurably
import java.io.File

/** Where the island's sealed keys ([IslandKeys]) are kept: none until the island password is set. */
internal class IslandKeyFile(private val file: File) {

    /** The sealed keys, or null before the island password is set. */
    fun read(): IslandKeys? = if (file.exists()) IslandKeys.fromBytes(file.readBytes()) else null

    /** Without the file the island would be lost for good, so it is written so that nothing can leave it half-written. */
    fun write(keys: IslandKeys) = writeDurably(file, keys.toBytes())
}
