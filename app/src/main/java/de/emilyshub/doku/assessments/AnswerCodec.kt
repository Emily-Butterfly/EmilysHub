package de.emilyshub.doku.assessments

/**
 * An answer as it is stored: its [value], which names its kind, so that it reads back without its item, even once the
 * item is gone from the form; and its [text], what it holds in words, which the Doku's search reads.
 */
data class StoredAnswer(val value: String, val text: String)

/** How an [Answer] is stored and read back. */
object AnswerCodec {

    /** [answer] as stored; none for nothing answered: nothing checked, no words. */
    fun encode(answer: Answer): StoredAnswer? = when (answer) {
        is Answer.Chosen -> StoredAnswer("$CHOSEN${answer.optionId}", "")
        is Answer.YesNo -> StoredAnswer("$YES_NO${if (answer.yes) 1 else 0}", "")
        is Answer.OnScale -> StoredAnswer("$SCALE${answer.value}", "")
        is Answer.Number -> StoredAnswer("$NUMBER${answer.hundredths}", "")
        is Answer.Checked -> answer.other.trim().let { other ->
            if (answer.optionIds.isEmpty() && other.isEmpty()) null else StoredAnswer(CHECKED + answer.optionIds.sorted().joinToString(","), other)
        }
        is Answer.Text -> answer.text.trim().takeIf(String::isNotEmpty)?.let { StoredAnswer(TEXT, it) }
        is Answer.Regions -> answer.text.trim().let { text ->
            if (answer.regions.isEmpty() && text.isEmpty()) null else StoredAnswer(REGIONS + answer.regions.sorted().joinToString(",") { it.id }, text)
        }
        // A line of words each, in the order of the levels; a goal without words or level is none.
        is Answer.Goals -> answer.goals.map { it.copy(text = it.text.replace('\n', ' ').trim()) }.filter { it.text.isNotEmpty() || it.level != null }
            .takeIf(List<Goal>::isNotEmpty)
            ?.let { goals -> StoredAnswer(GOALS + goals.joinToString(",") { it.level?.toString().orEmpty() }, goals.joinToString("\n", transform = Goal::text)) }
    }

    /** The answer [stored] holds; none for one of a kind this version does not know. */
    fun decode(stored: StoredAnswer): Answer? {
        val kind = stored.value.take(2)
        val rest = stored.value.drop(2)
        val ids = { rest.split(',').filter(String::isNotEmpty).toSet() }
        return when (kind) {
            CHOSEN -> Answer.Chosen(rest)
            YES_NO -> when (rest) {
                "1" -> Answer.YesNo(true)
                "0" -> Answer.YesNo(false)
                else -> null
            }
            SCALE -> rest.toIntOrNull()?.let(Answer::OnScale)
            NUMBER -> rest.toLongOrNull()?.let(Answer::Number)
            CHECKED -> Answer.Checked(ids(), stored.text)
            TEXT -> Answer.Text(stored.text)
            REGIONS -> Answer.Regions(ids().mapNotNull(BodyRegion::of).toSet(), stored.text)
            GOALS -> {
                val levels = rest.split(',')
                val texts = stored.text.split('\n')
                if (levels.size != texts.size || levels.any { it.isNotEmpty() && it.toIntOrNull() == null }) {
                    null
                } else {
                    Answer.Goals(texts.zip(levels) { text, level -> Goal(text, level.toIntOrNull()) })
                }
            }
            else -> null
        }
    }

    private const val CHOSEN = "c:"
    private const val YES_NO = "y:"
    private const val SCALE = "s:"
    private const val NUMBER = "n:"
    private const val CHECKED = "k:"
    private const val TEXT = "t:"
    private const val REGIONS = "r:"
    private const val GOALS = "g:"
}
