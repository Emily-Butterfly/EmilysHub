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
        val exact = mutableMapOf<String, Fraction?>()
        return form.scores.map { score ->
            val fraction = if (score.sumOf.isNotEmpty()) {
                score.sumOf.map { exact[it] }.takeIf { parts -> parts.all { it != null } }?.map { checkNotNull(it) }?.reduce(Fraction::plus)
            } else {
                value(form, score, answers.filterKeys { it in shown }, shown)
            }?.let { Fraction(it.numerator, it.denominator * score.divisor) }
            exact[score.id] = fraction
            ScoreResult(score, fraction?.hundredths())
        }
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
        is ScaleItem -> answer is Answer.OnScale && answer.value in item.steps
        is ChecklistItem -> answer is Answer.Checked
        is NumberItem -> answer is Answer.Number
        is TextItem -> answer is Answer.Text
        is BodyRegionsItem -> answer is Answer.Regions
    }

    /** The least and the most [score] of [form] can be, in hundredths: for the chart's scale. */
    fun range(form: Assessment, score: Score): LongRange {
        if (score.sumOf.isNotEmpty()) {
            val parts = score.sumOf.map { id -> range(form, form.scores.single { it.id == id }) }
            return parts.sumOf { it.first }..parts.sumOf { it.last }
        }
        val items = score.items.map { checkNotNull(form.item(it)) }
        val ranges = items.map { checkNotNull(range(it)) }
        val range = when (score.rule) {
            ScoreRule.Sum -> {
                val counted = alternativesOnce(form, items.zip(ranges))
                counted.sumOf { it.first } * HUNDRED..counted.sumOf { it.second } * HUNDRED
            }
            ScoreRule.MeanFilled -> ranges.sumOf { it.first } * HUNDRED..ranges.sumOf { it.second } * HUNDRED
            ScoreRule.Mean -> ranges.minOf { it.first } * HUNDRED..ranges.maxOf { it.second } * HUNDRED
            ScoreRule.Percent, ScoreRule.ZeroTo100, ScoreRule.PercentOfAll -> 0L..HUNDRED * HUNDRED
        }
        val by = HUNDRED * score.divisor
        return Fraction(range.first, by).hundredths()..Fraction(range.last, by).hundredths()
    }

    /**
     * The [ranges] of items, but items shown in place of each other, by different options of the same choice, as one: the
     * least and the most of them, as only one of them is answered.
     */
    private fun alternativesOnce(form: Assessment, ranges: List<Pair<Item, Pair<Long, Long>>>): List<Pair<Long, Long>> {
        val (alternatives, alone) = ranges.partition { (item, _) -> item.showIf?.let { form.item(it.itemId) } is ChoiceItem }
        return alone.map { it.second } + alternatives.groupBy { (item, _) -> checkNotNull(item.showIf).itemId }.values.flatMap { group ->
            val options = group.flatMap { (item, _) -> checkNotNull(item.showIf).values }
            if (options.size == options.toSet().size) listOf(group.minOf { it.second.first } to group.maxOf { it.second.second }) else group.map { it.second }
        }
    }

    /** Whether [answer] is one [condition] asks for. */
    private fun holds(condition: Condition, answer: Answer?): Boolean = when (answer) {
        is Answer.Chosen -> answer.optionId in condition.values
        is Answer.YesNo -> answer.yes && Condition.YES in condition.values
        is Answer.Checked -> answer.optionIds.any { it in condition.values }
        else -> false
    }

    /** [score] of [form] from [answers] to the items [shown], exact; none while fewer than its [Score.minAnswered] are answered. */
    private fun value(form: Assessment, score: Score, answers: Map<String, Answer>, shown: Set<String>): Fraction? {
        fun answered(ids: List<String>) = ids.mapNotNull { id ->
            val item = checkNotNull(form.item(id))
            // Nothing checked of a scored checklist shown is an answer too: none applies.
            val answer = answers[id] ?: Answer.Checked(emptySet()).takeIf { (item as? ChecklistItem)?.scored == true && id in shown }
            answer?.let { points(item, it)?.let { p -> p to checkNotNull(range(item)) } }
        }
        if (score.rule == ScoreRule.MeanFilled) return meanFilled(form, score, ::answered)
        val answered = answered(score.items)
        if (answered.size < score.minAnswered) return null
        val sum = answered.sumOf { it.first }
        val least = answered.sumOf { it.second.first }
        val most = answered.sumOf { it.second.second }
        return when (score.rule) {
            ScoreRule.Sum -> Fraction(sum, 1)
            ScoreRule.Mean -> Fraction(sum, answered.size.toLong())
            ScoreRule.Percent -> Fraction(sum * HUNDRED, most)
            ScoreRule.ZeroTo100 -> Fraction((sum - least) * HUNDRED, most - least)
            ScoreRule.PercentOfAll -> {
                val all = score.items.mapNotNull { range(checkNotNull(form.item(it))) }
                Fraction((sum + (all.sumOf { it.first } - least)) * HUNDRED, all.sumOf { it.second })
            }
            ScoreRule.MeanFilled -> error("reckoned by section")
        }.takeIf { it.denominator != 0L }
    }

    /** [score] filled up by the mean of each section of [form]'s items: the sum of each section times its items over those answered. */
    private fun meanFilled(form: Assessment, score: Score, answered: (List<String>) -> List<Pair<Long, Pair<Long, Long>>>): Fraction? {
        val sections = form.sections.map { section -> section.items.map(Item::id).filter { it in score.items } }.filter(List<String>::isNotEmpty)
        return sections.map { ids ->
            val points = answered(ids)
            if (points.size < half(ids.size)) return null
            Fraction(points.sumOf { it.first } * ids.size, points.size.toLong())
        }.reduce(Fraction::plus)
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

    /** A value as it is, [numerator] over [denominator], to be rounded once at the end. */
    private data class Fraction(val numerator: Long, val denominator: Long) {
        operator fun plus(other: Fraction) = Fraction(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

        /** In hundredths, rounded half away from zero. */
        fun hundredths(): Long {
            val dividend = numerator * HUNDRED
            val by = denominator
            val sign = if ((dividend < 0) != (by < 0)) -1 else 1
            return sign * ((Math.abs(dividend) + Math.abs(by) / 2) / Math.abs(by))
        }
    }

    private const val HUNDRED = 100L
}
