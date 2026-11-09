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
        val counted = answers.filterKeys { it in shown }
        val exact = mutableMapOf<String, Fraction?>()
        return form.scores.map { score ->
            val fraction = when (score) {
                is ItemScore -> reckoner(score.rule).value(Counted(form, score, counted, shown))
                    ?.let { Fraction(it.numerator, it.denominator * score.divisor) }
                    ?.let { stepped(score, it) }
                is WeightedScore -> weighted(score, exact)
                is GradedScore -> grade(score, exact)
            }
            exact[score.id] = fraction
            ScoreResult(score, fraction?.hundredths())
        }
    }

    /** The weighted sum of [score]'s parts, of their [exact] values; none while one of them has none. */
    private fun weighted(score: WeightedScore, exact: Map<String, Fraction?>): Fraction? {
        val parts = score.parts.map { exact[it] ?: return null }
        return parts.zip(weights(score)) { part, weight -> Fraction(part.numerator * weight, part.denominator * HUNDRED) }
            .fold(Fraction(score.constant.toLong(), HUNDRED), Fraction::plus)
    }

    /** The value of the first grade of [score] whose bounds hold of the [exact] values before it; none while one it bounds has none. */
    private fun grade(score: GradedScore, exact: Map<String, Fraction?>): Fraction? {
        if (score.grades.flatMap(Grade::bounds).any { exact[it.scoreId] == null }) return null
        return score.grades.firstOrNull { grade -> grade.bounds.all { it.holds(checkNotNull(exact[it.scoreId])) } }?.let { Fraction(it.value.toLong(), 1) }
    }

    /** Whether [value] lies within this bound. */
    private fun Bound.holds(value: Fraction): Boolean =
        (from == null || value.compareTo(Fraction(from, HUNDRED)) >= 0) && (below == null || value.compareTo(Fraction(below, HUNDRED)) < 0)

    /** What each part [score] adds up counts, in hundredths. */
    private fun weights(score: WeightedScore): List<Long> = score.weights.map(Int::toLong).ifEmpty { score.parts.map { HUNDRED } }

    /** Whether [form] reaches a result with nothing answered, as one counting what is ticked, nothing being 0. */
    fun reachesResultUnanswered(form: Assessment): Boolean = results(form, emptyMap()).any { it.value != null }

    /** The items of [form] shown with [answers]: each without a condition, or whose condition holds of an item shown. */
    fun visible(form: Assessment, answers: Map<String, Answer>): List<Item> {
        val shown = mutableSetOf<String>()
        // Conditions point back to items before, as in the original, so that one pass settles them.
        return form.items.filter { item ->
            val condition = item.showIf
            val show = condition == null || condition.alternatives.any { it.itemId in shown && holds(it, answers[it.itemId]) }
            if (show) shown += item.id
            show
        }
    }

    /**
     * What [answers] hold for items [form] no longer has, or that no longer fit their item, which changed its kind: shown
     * apart, greyed, and counted nowhere.
     */
    fun orphans(form: Assessment, answers: Map<String, Answer>): Map<String, Answer> =
        answers.filter { (id, answer) -> form.item(id)?.let { it.fits(answer) } != true }

    /** The least and the most [score] of [form] can be, in hundredths: for the chart's scale. */
    fun range(form: Assessment, score: Score): LongRange = when (score) {
        is GradedScore -> score.grades.minOf(Grade::value) * HUNDRED..score.grades.maxOf(Grade::value) * HUNDRED
        is WeightedScore -> {
            // Weighed, a part's least may make the most, if its weight is below 0.
            val parts = score.parts.zip(weights(score)) { id, weight ->
                range(form, form.scores.single { it.id == id }).let { listOf(it.first * weight, it.last * weight) }.let { it.min()..it.max() }
            }
            // Hundredths weighed in hundredths: ten-thousandths, back to hundredths.
            fun hundredths(tenThousandths: Long) = Fraction(tenThousandths + score.constant * HUNDRED, HUNDRED * HUNDRED).hundredths()
            hundredths(parts.sumOf { it.first })..hundredths(parts.sumOf { it.last })
        }
        is ItemScore -> score.steps?.range?.let { it.first * HUNDRED..it.last * HUNDRED } ?: run {
            val range = reckoner(score.rule).range(Counted(form, score))
            val by = HUNDRED * score.divisor
            Fraction(range.first, by).hundredths()..Fraction(range.last, by).hundredths()
        }
    }

    /**
     * The [ranges] of items, but items shown in place of each other, by different options of the same choice, as one: the
     * least and the most of them, as only one of them is answered.
     */
    private fun alternativesOnce(form: Assessment, ranges: List<Pair<Item, Pair<Long, Long>>>): List<Pair<Long, Long>> {
        // Shown by one choice alone, not by one or another condition.
        val (alternatives, alone) = ranges.partition { (item, _) -> item.showIf?.takeIf { it.orElse == null }?.let { form.item(it.itemId) } is ChoiceItem }
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

    /** What [answers] hold for [item]: nothing checked of a scored checklist [shown] is an answer too, none applying. */
    private fun answer(item: Item, answers: Map<String, Answer>, shown: Set<String>): Answer? =
        answers[item.id] ?: Answer.Checked(emptySet()).takeIf { (item as? ChecklistItem)?.scored == true && item.id in shown }

    /**
     * The T-score of the goals of [answer] to [item], each rated at one of its levels; none without a goal or while one is
     * unrated. A goal without words or level is none, as it is stored ([AnswerCodec]).
     */
    private fun goals(item: GoalsItem, answer: Answer?): Fraction? {
        val known = item.levels.map(Option::points).toSet()
        val levels = (answer as? Answer.Goals)?.goals?.filterNot(Goal::isEmpty)?.map { goal -> goal.level?.takeIf { it in known } ?: return null }?.takeIf(List<Int>::isNotEmpty) ?: return null
        val n = levels.size.toDouble()
        return Fraction(goalT(10.0 * levels.sum() / Math.sqrt(0.7 * n + 0.3 * n * n)), HUNDRED)
    }

    /** 50 plus [deviation], in hundredths, rounded half away from zero; a root is no fraction, so reckoned as a double. */
    private fun goalT(deviation: Double): Long = Math.round(Math.abs(50 + deviation) * HUNDRED) * (if (50 + deviation < 0) -1 else 1)

    /** How far a T-score may lie from 50: of ever more goals, all at ±2, 20 / √0,3. */
    private val GOAL_T_LIMIT = 20 / Math.sqrt(0.3)

    /** The first of [minutes] in percent of the span from the second to the third, over midnight; none for no span. */
    private fun shareOfSpan(minutes: List<Int>): Fraction? {
        val (part, from, to) = minutes
        val span = Math.floorMod(to - from, TimeItem.DAY)
        return if (span == 0) null else Fraction(part * HUNDRED, span.toLong())
    }

    /** [value] of [score] as the points of its steps, if it has them. */
    private fun stepped(score: ItemScore, value: Fraction): Fraction = score.steps?.let { steps -> Fraction(steps.points { value < Fraction(it.toLong(), 1) }.toLong(), 1) } ?: value

    /**
     * The items of [score] of [form] with what is answered to them, of [answers] to the items [shown]: the one collector
     * each rule reckons from. Without answers, it serves a rule's range.
     */
    private class Counted(val form: Assessment, val score: ItemScore, answers: Map<String, Answer> = emptyMap(), shown: Set<String> = emptySet()) {
        val items: List<Item> = score.items.map { checkNotNull(form.item(it)) }

        /** The fewest and the most points of each item that makes points; not of goals, which a T-score rates. */
        val ranges: Map<String, Pair<Long, Long>> = items.mapNotNull { item -> item.pointRange?.let { item.id to (it.first to it.last) } }.toMap()

        /** The points of each item answered, by id, in the order of its items; nothing ticked of a scored checklist shown is 0. */
        val points: Map<String, Long> = items.mapNotNull { item -> answer(item, answers, shown)?.let(item::points)?.let { item.id to it } }.toMap()

        /** The answers as they are, to those of its items they fit: for rules that read more than points. */
        val raw: Map<String, Answer> = items.mapNotNull { item -> answers[item.id]?.takeIf { item.fits(it) }?.let { item.id to it } }.toMap()

        /** Whether enough is answered: [ItemScore.minAnswered] of its items, or with optional ones all the others. */
        val enough: Boolean = if (score.optional.isEmpty()) points.size >= score.minAnswered else (score.items - score.optional.toSet()).all { it in points }

        val sum: Long get() = points.values.sum()

        /** The fewest and the most points of the items answered, each added up. */
        val least: Long get() = points.keys.sumOf { ranges.getValue(it).first }
        val most: Long get() = points.keys.sumOf { ranges.getValue(it).second }
    }

    /** How a rule reckons: its value and its range, together. */
    private interface Reckoner {
        /** The exact value of what [counted] holds; none while too little is answered. */
        fun value(counted: Counted): Fraction?

        /** The least and the most it can be, in hundredths, before its divisor. */
        fun range(counted: Counted): LongRange
    }

    /** The reckoner of [rule]. */
    private fun reckoner(rule: ScoreRule): Reckoner = when (rule) {
        ScoreRule.Sum -> SUM
        ScoreRule.Mean -> MEAN
        ScoreRule.Percent -> PERCENT
        ScoreRule.ZeroTo100 -> ZERO_TO_100
        ScoreRule.PercentOfAll -> PERCENT_OF_ALL
        ScoreRule.MeanFilled -> MEAN_FILLED
        ScoreRule.GoalT -> GOAL_T
        ScoreRule.ShareOfSpan -> SHARE_OF_SPAN
    }

    /** A reckoner from 0 to 100 %, of [value]. */
    private fun percent(value: (Counted) -> Fraction?) = object : Reckoner {
        override fun value(counted: Counted) = if (counted.enough) value(counted)?.takeIf { it.denominator != 0L } else null
        override fun range(counted: Counted) = 0L..HUNDRED * HUNDRED
    }

    /** The sum; of each pair of [ItemScore.worseOf], the one with fewer points, all needed then. */
    private val SUM = object : Reckoner {
        override fun value(counted: Counted): Fraction? {
            val score = counted.score
            if (score.worseOf.isEmpty()) return if (counted.enough) Fraction(counted.sum, 1) else null
            if (counted.points.size < score.items.size) return null
            val paired = score.worseOf.flatten().toSet()
            val points = counted.points
            return Fraction(points.filterKeys { it !in paired }.values.sum() + score.worseOf.sumOf { pair -> pair.minOf { points.getValue(it) } }, 1)
        }

        override fun range(counted: Counted): LongRange {
            val score = counted.score
            // Of each pair the worse: the lesser least and the lesser most.
            val paired = score.worseOf.flatten().toSet()
            val pairs = score.worseOf.map { pair -> pair.map { counted.ranges.getValue(it) }.let { (a, b) -> minOf(a.first, b.first) to minOf(a.second, b.second) } }
            val ranged = alternativesOnce(counted.form, counted.items.filter { it.id !in paired }.map { it to counted.ranges.getValue(it.id) }) + pairs
            return ranged.sumOf { it.first } * HUNDRED..ranged.sumOf { it.second } * HUNDRED
        }
    }

    private val MEAN = object : Reckoner {
        override fun value(counted: Counted) = if (counted.enough) Fraction(counted.sum, counted.points.size.toLong()) else null
        override fun range(counted: Counted) = counted.ranges.values.minOf { it.first } * HUNDRED..counted.ranges.values.maxOf { it.second } * HUNDRED
    }

    /** Of the most the items answered could reach: the maximum shrinks with each left out. */
    private val PERCENT = percent { Fraction(it.sum * HUNDRED, it.most) }

    private val ZERO_TO_100 = percent { Fraction((it.sum - it.least) * HUNDRED, it.most - it.least) }

    /** Of the most all items could reach: one left out counts its least. */
    private val PERCENT_OF_ALL = percent { counted ->
        val all = counted.ranges.values
        Fraction((counted.sum + (all.sumOf { it.first } - counted.least)) * HUNDRED, all.sumOf { it.second })
    }

    /** The sum, each section's items left out counted as the mean of those answered there, once half of each is. */
    private val MEAN_FILLED = object : Reckoner {
        override fun value(counted: Counted): Fraction? {
            val sections = counted.form.sections.map { section -> section.items.map(Item::id).filter { it in counted.score.items } }.filter(List<String>::isNotEmpty)
            return sections.map { ids ->
                val points = ids.mapNotNull { counted.points[it] }
                if (points.size < half(ids.size)) return null
                Fraction(points.sum() * ids.size, points.size.toLong())
            }.reduce(Fraction::plus)
        }

        override fun range(counted: Counted) = counted.ranges.values.sumOf { it.first } * HUNDRED..counted.ranges.values.sumOf { it.second } * HUNDRED
    }

    private val GOAL_T = object : Reckoner {
        override fun value(counted: Counted) = goals(counted.items.single() as GoalsItem, counted.raw[counted.items.single().id])
        override fun range(counted: Counted) = goalT(-GOAL_T_LIMIT)..goalT(GOAL_T_LIMIT)
    }

    private val SHARE_OF_SPAN = object : Reckoner {
        override fun value(counted: Counted) = shareOfSpan(counted.items.map { (counted.raw[it.id] as? Answer.Time)?.minutes ?: return null })
        override fun range(counted: Counted) = 0L..HUNDRED * HUNDRED
    }

    /** A value as it is, [numerator] over [denominator], to be rounded once at the end. */
    private data class Fraction(val numerator: Long, val denominator: Long) {
        // Reduced, as a sum of many, such as the PSQI's seven components, would else overflow its denominators.
        operator fun plus(other: Fraction) = reduced(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

        /** Below 0 if less than [other], 0 if as much, else above; denominators are positive. */
        operator fun compareTo(other: Fraction): Int = (numerator * other.denominator).compareTo(other.numerator * denominator)

        companion object {
            /** [numerator] over [denominator], divided by what they share. */
            fun reduced(numerator: Long, denominator: Long): Fraction {
                val shared = gcd(Math.abs(numerator), Math.abs(denominator)).coerceAtLeast(1)
                return Fraction(numerator / shared, denominator / shared)
            }

            private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        }

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
