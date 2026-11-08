package de.emilyshub.doku.assessments

/** A [score] reckoned from the answers: its [value] in hundredths and the [band] it lies in, none while too little is answered. */
data class ScoreResult(val score: Score, val value: Long?) {
    /** The name of the band [value] lies in, if the score has bands. */
    val band: String? get() = value?.let { v -> score.bands.firstOrNull { v <= it.upTo }?.label }
}

/** How a form reckons its results from its answers, as the original does (SPEC.md §14): pure logic, without Android. */
object Scoring {

    /** Each score of [form] from [answers] by item id; items not shown ([visible]) count for nothing. */
    fun results(form: Assessment, answers: Map<String, Answer>): List<ScoreResult> {
        val shown = visible(form, answers).map(Item::id).toSet()
        return form.scores.map { score -> ScoreResult(score, value(form, score, answers.filterKeys { it in shown }, shown)) }
    }

    /** Whether [form] reaches a result with nothing answered, as one counting what is ticked, nothing being 0. */
    fun reachesResultUnanswered(form: Assessment): Boolean = results(form, emptyMap()).any { it.value != null }

    /** The items of [form] shown with [answers]: each without a condition, or whose condition holds of an item shown. */
    fun visible(form: Assessment, answers: Map<String, Answer>): List<Item> {
        val shown = mutableSetOf<String>()
        // Conditions point back to items before, as in the original, so that one pass settles them.
        return form.items.filter { item ->
            val condition = item.showIf
            val show = condition == null || (condition.itemId in shown && holds(condition, answers[condition.itemId]))
            if (show) shown += item.id
            show
        }
    }

    /**
     * What [answers] hold for items [form] no longer has, or that no longer fit their item, which changed its kind: shown
     * apart, greyed, and counted nowhere.
     */
    fun orphans(form: Assessment, answers: Map<String, Answer>): Map<String, Answer> =
        answers.filter { (id, answer) -> form.item(id)?.let { fits(it, answer) } != true }

    /** Whether [answer] is of the kind [item] takes. */
    fun fits(item: Item, answer: Answer): Boolean = when (item) {
        is ChoiceItem -> answer is Answer.Chosen
        is YesNoItem -> answer is Answer.YesNo
        is ScaleItem -> answer is Answer.OnScale
        is ChecklistItem -> answer is Answer.Checked
        is NumberItem -> answer is Answer.Number
        is TextItem -> answer is Answer.Text
        is BodyRegionsItem -> answer is Answer.Regions
    }

    /** The least and the most [score] of [form] can be, in hundredths: for the chart's scale. */
    fun range(form: Assessment, score: Score): LongRange {
        val items = score.items.map { checkNotNull(form.item(it)) }
        val ranges = items.map { checkNotNull(range(it)) }
        return when (score.rule) {
            ScoreRule.Sum -> {
                // Items shown in place of each other, by different answers to the same item, count as one: the least and
                // the most of them.
                val alternatives = items.zip(ranges).groupBy { (item, _) -> item.showIf?.itemId to item.id.takeIf { item.showIf == null } }.values.map { group ->
                    val exclusive = group.size > 1 && group.flatMap { (item, _) -> item.showIf!!.values }.let { it.size == it.toSet().size }
                    if (exclusive) listOf(group.minOf { it.second.first } to group.maxOf { it.second.second }) else group.map { it.second }
                }.flatten()
                alternatives.sumOf { it.first } * HUNDRED..alternatives.sumOf { it.second } * HUNDRED
            }
            ScoreRule.Mean -> ranges.minOf { it.first } * HUNDRED..ranges.maxOf { it.second } * HUNDRED
            ScoreRule.Percent, ScoreRule.ZeroTo100 -> 0L..HUNDRED * HUNDRED
        }
    }

    /** Whether [answer] is one [condition] asks for. */
    private fun holds(condition: Condition, answer: Answer?): Boolean = when (answer) {
        is Answer.Chosen -> answer.optionId in condition.values
        is Answer.YesNo -> answer.yes && Condition.YES in condition.values
        is Answer.Checked -> answer.optionIds.any { it in condition.values }
        else -> false
    }

    /** [score] of [form] from [answers] to the items [shown], in hundredths; none while fewer than its [Score.minAnswered] are answered. */
    private fun value(form: Assessment, score: Score, answers: Map<String, Answer>, shown: Set<String>): Long? {
        val answered = score.items.mapNotNull { id ->
            val item = checkNotNull(form.item(id))
            // Nothing checked of a scored checklist shown is an answer too: none applies.
            val answer = answers[id] ?: Answer.Checked(emptySet()).takeIf { (item as? ChecklistItem)?.scored == true && id in shown }
            answer?.let { points(item, it)?.let { p -> p to checkNotNull(range(item)) } }
        }
        if (answered.size < score.minAnswered) return null
        val sum = answered.sumOf { it.first }
        val least = answered.sumOf { it.second.first }
        val most = answered.sumOf { it.second.second }
        return when (score.rule) {
            ScoreRule.Sum -> sum * HUNDRED
            ScoreRule.Mean -> rounded(sum * HUNDRED, answered.size.toLong())
            ScoreRule.Percent -> rounded(sum * HUNDRED * HUNDRED, most)
            ScoreRule.ZeroTo100 -> rounded((sum - least) * HUNDRED * HUNDRED, most - least)
        }
    }

    /** The points [answer] makes for [item], reversed ones counted the other way round; none for an answer that does not fit it. */
    internal fun points(item: Item, answer: Answer): Long? {
        val raw = when (item) {
            is ChoiceItem -> (answer as? Answer.Chosen)?.let { chosen -> item.options.find { it.id == chosen.optionId && !it.notApplicable }?.points }
            is ScaleItem -> (answer as? Answer.OnScale)?.value?.takeIf { it in item.steps }
            is YesNoItem -> (answer as? Answer.YesNo)?.let { if (it.yes) item.yesPoints else item.noPoints }
            is ChecklistItem -> (answer as? Answer.Checked)?.takeIf { item.scored }?.let { checked ->
                item.options.filter { it.id in checked.optionIds }.sumOf(Option::points)
            }
            else -> null
        }?.toLong() ?: return null
        val (least, most) = range(item) ?: return null
        return if (item.reversed) least + most - raw else raw
    }

    /** The fewest and the most points [item] makes, if it makes any. */
    private fun range(item: Item): Pair<Long, Long>? = when (item) {
        is ChoiceItem -> item.options.filterNot(Option::notApplicable).map(Option::points).let { it.min().toLong() to it.max().toLong() }
        is ScaleItem -> item.min.toLong() to item.max.toLong()
        is YesNoItem -> minOf(item.yesPoints, item.noPoints).toLong() to maxOf(item.yesPoints, item.noPoints).toLong()
        is ChecklistItem -> if (item.scored) item.options.sumOf { minOf(it.points, 0) }.toLong() to item.options.sumOf { maxOf(it.points, 0) }.toLong() else null
        else -> null
    }

    private val Item.reversed: Boolean
        get() = when (this) {
            is ChoiceItem -> reversed
            is ScaleItem -> reversed
            else -> false
        }

    /** [dividend] / [divisor], rounded half away from zero; none if the divisor is 0. */
    private fun rounded(dividend: Long, divisor: Long): Long? {
        if (divisor == 0L) return null
        val half = Math.abs(divisor) / 2
        val sign = if ((dividend < 0) != (divisor < 0)) -1 else 1
        return sign * ((Math.abs(dividend) + half) / Math.abs(divisor))
    }

    private const val HUNDRED = 100L
}
