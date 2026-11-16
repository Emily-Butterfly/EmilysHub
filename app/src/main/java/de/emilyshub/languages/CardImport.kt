package de.emilyshub.languages

/** A card read from line [line] of a file, as it is to be made. */
data class FileCard(val line: Int, val draft: CardDraft)

/** What reading a file of cards came to (SPEC.md §15, Anlegen). */
sealed interface CardFile {
    /** Its [cards], and the numbers of the lines that do not read as one ([unreadable]). */
    data class Read(val cards: List<FileCard>, val unreadable: List<Int>) : CardFile

    /** Over [CardImport.FILE_MAX_BYTES]. */
    data object TooLarge : CardFile

    /** Over [CardImport.FILE_MAX_LINES] lines. */
    data object TooManyLines : CardFile

    /** It is no text that reads as UTF-8, or cannot be read. */
    data object NotText : CardFile
}

/**
 * Cards from where the words stand already (SPEC.md §15, Anlegen; #105): the lines of a lesson's note with two sides
 * split by " – ", " - ", " = " or a tab, Markdown at their start and bold type aside; and a text file, a card on each
 * line, its front, back, transcription, note and Beispielsatz (#155) split by tab or semicolon, the last three
 * optional, a field in quotes allowed to hold the separator; a quote opens a field only at its start, elsewhere it is a letter like any other. Nothing read is cut short: a field too long leaves its line unread.
 */
object CardImport {
    /** The largest file read, in bytes. */
    const val FILE_MAX_BYTES = 1024 * 1024

    /** The most lines a file may have. */
    const val FILE_MAX_LINES = 2_000

    /** What splits a line of a note into its two sides; the first of them on the line splits it. */
    private val NOTE_SEPARATORS = listOf(" – ", " - ", " = ", "\t")

    /** Markdown a line may start with: a heading, a quote, a bullet, a box to tick. */
    private val LEADING_MARKDOWN = Regex("""^\s*(?:#{1,6}\s+|>\s?|[-*+]\s+|\[[ xX]]\s+)""")

    /** Bold type, in either way Markdown writes it. */
    private val BOLD = Regex("""\*\*|__""")

    /** The lines of [note] that read as a card, in their order, with front and back only. */
    fun fromNote(note: String): List<CardDraft> = note.lines().mapNotNull { line ->
        var text = line
        while (true) {
            val stripped = LEADING_MARKDOWN.replace(text, "")
            if (stripped == text) break
            text = stripped
        }
        text = BOLD.replace(text, "")
        val (at, separator) = NOTE_SEPARATORS.mapNotNull { sep -> text.indexOf(sep).takeIf { it >= 0 }?.let { it to sep } }
            .minByOrNull { it.first } ?: return@mapNotNull null
        CardDraft(front = text.substring(0, at), back = text.substring(at + separator.length)).cleaned()
            .takeIf { it.problems.isEmpty() }
    }

    /** The cards of a file of [bytes], or why it is refused. */
    fun fromFile(bytes: ByteArray): CardFile {
        if (bytes.size > FILE_MAX_BYTES) return CardFile.TooLarge
        val text = utf8Text(bytes)?.takeUnless { '\u0000' in it } ?: return CardFile.NotText
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').removeSuffix("\n").split('\n')
        if (lines.size > FILE_MAX_LINES) return CardFile.TooManyLines
        val cards = mutableListOf<FileCard>()
        val unreadable = mutableListOf<Int>()
        lines.forEachIndexed { index, line ->
            if (line.isBlank() || line.trimStart().startsWith('#')) return@forEachIndexed
            when (val draft = cardOf(line)) {
                null -> unreadable += index + 1
                else -> cards += FileCard(index + 1, draft)
            }
        }
        return CardFile.Read(cards, unreadable)
    }

    /** The card a line of a file reads as; none if it does not, or has a field too long. */
    private fun cardOf(line: String): CardDraft? {
        // A tab splits, if one stands outside quotes; else a semicolon. Both read quotes alike.
        val byTab = fields(line, '\t')
        val fields = (if (byTab != null && byTab.size > 1) byTab else fields(line, ';'))?.takeIf { it.size in 2..5 } ?: return null
        val draft = CardDraft(
            front = fields[0],
            back = fields[1],
            transcription = fields.getOrElse(2) { "" },
            note = fields.getOrElse(3) { "" },
            example = fields.getOrElse(4) { "" },
        ).cleaned()
        return draft.takeIf { it.problems.isEmpty() }
    }

    /** The fields of [line] split by [separator], a field in quotes as written within them; none if a quote is left open. */
    private fun fields(line: String, separator: Char): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var i = 0
        var quoted = false
        while (i < line.length) {
            val char = line[i]
            when {
                quoted && char == '"' && line.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }
                char == '"' && (quoted || field.isBlank()) -> {
                    if (!quoted) field.clear()
                    quoted = !quoted
                }
                char == separator && !quoted -> {
                    fields += field.toString()
                    field.clear()
                }
                else -> field.append(char)
            }
            i++
        }
        if (quoted) return null
        fields += field.toString()
        return fields
    }
}
