package de.emilyshub.reminders

/**
 * Files a test puts in Android's file dialog, and the phone's tones the app copies them into: each copy is in [tones]
 * from then on, under the file's name without its ending.
 */
class FakeToneFiles(private val tones: FakeTones) : ToneFiles {
    private val files = mutableMapOf<String, PickedFile>()

    /** The copies made, in order. */
    val copies = mutableListOf<Copy>()

    /** The copies made a tone of another kind too, by their address, in order. */
    val widened = mutableListOf<Pair<String, ToneKind>>()

    /** A file at [uri], [named] so, with [bytes], and [playable] as audio unless said otherwise. */
    fun add(uri: String, named: String, bytes: ByteArray = named.toByteArray(), playable: Boolean = true) {
        files[uri] = PickedFile(named, "audio/mpeg", bytes, playable)
    }

    override suspend fun read(file: String, limit: Int): PickedFile? =
        files[file]?.let { it.copy(bytes = it.bytes.copyOf(minOf(it.bytes.size, limit))) }

    override suspend fun copy(file: PickedFile, kind: ToneKind): String {
        val uri = "content://media/external/audio/media/${100 + copies.size}"
        copies += Copy(file.name, kind, uri)
        tones.add(uri, file.title)
        return uri
    }

    override suspend fun widen(copy: String, kind: ToneKind) {
        widened += copy to kind
    }

    /** A copy of the file [name]d so, made as a tone of [kind], at [uri]. */
    data class Copy(val name: String, val kind: ToneKind, val uri: String)
}
