package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * A built-in assessment form of the Doku (SPEC.md §14), from the "Scores" of DIGOTOR's downloads: fixed in the code, not
 * to change in the app; a new or corrected one comes with an update. Its [id] and those of its items stay as they are
 * from one version to the next, as only the answers by item id are stored and every result is reckoned from them anew.
 */
data class Assessment(
    /** Stable across versions of the app: what is stored of a filled-in form names it by this. */
    val id: String,
    val name: String,
    /** Such as "PHQ-9", for the search; none if the form has no short name. */
    val abbreviation: String? = null,
    /** Where DIGOTOR sorts it; in each of them, the same form. */
    val categories: List<AssessmentCategory>,
    /** The PDF it is taken from, with its date, such as "PHQ-9, Stand 10/2026". */
    val source: String,
    /** What the form says to those filling it in, above its items; empty for nothing. */
    val hint: String = "",
    val sections: List<Section>,
    /** What it reckons from the answers: none, one, or several subscales. */
    val scores: List<Score> = emptyList(),
) {
    /** All items, in the order of the original. */
    val items: List<Item> get() = sections.flatMap(Section::items)

    /** Item [id], if this version of the form has it. */
    fun item(id: String): Item? = items.find { it.id == id }

    init {
        val ids = items.map(Item::id)
        require(ids.size == ids.toSet().size) { "$id: item ids must be unique" }
        require(scores.map(Score::id).toSet().size == scores.size) { "$id: score ids must be unique" }
        scores.forEach { score ->
            // Of all items, an item hidden would still count its most.
            require(score.rule != ScoreRule.PercentOfAll || score.items.all { item(it)?.showIf == null }) {
                "$id: score ${score.id} is a percent of all, which counts no item shown only under a condition"
            }
            score.items.forEach { itemId ->
                require(item(itemId)?.scorable == true) { "$id: score ${score.id} counts $itemId, which has no points" }
                // A T-score's goals and its rule come only together.
                require((item(itemId) is GoalsItem) == (score.rule == ScoreRule.GoalT)) { "$id: score ${score.id}: goals are counted by a T-score alone" }
            }
            require(score.rule != ScoreRule.ShareOfSpan || score.items.all { item(it) is TimeItem }) { "$id: score ${score.id} is a share of a span of times" }
            // Filled up by section, it needs half of each section, not a number of its own.
            require(score.rule != ScoreRule.MeanFilled || score.minAnswered == score.items.size) {
                "$id: score ${score.id} is filled by the mean, which needs half of each section answered, not minAnswered"
            }
            val before = scores.takeWhile { it !== score }.map(Score::id)
            require(score.sumOf.all { it in before }) { "$id: score ${score.id} adds up scores that do not come before it" }
            require(score.grades.flatMap(Grade::bounds).all { it.scoreId in before }) { "$id: score ${score.id} grades by scores that do not come before it" }
        }
        items.forEach { item ->
            val options = (item as? ChoiceItem)?.options ?: (item as? ChecklistItem)?.options.orEmpty()
            require(options.map(Option::id).let { ids -> ids.size == ids.toSet().size && ids.none { ',' in it || it.isEmpty() } }) {
                "$id: ${item.id} needs option ids of its own, without commas"
            }
            item.showIf?.alternatives?.forEach { require(item(it.itemId) != null) { "$id: ${item.id} depends on unknown ${it.itemId}" } }
        }
    }
}

/** DIGOTOR's subcategories of its "Scores", in their order there. */
enum class AssessmentCategory(val label: String) {
    Anamnesis("Anamnesebogen"),
    JawCmd("Kiefer / CMD"),
    UpperExtremity("Obere Extremität"),
    Neurology("Neurologie"),
    Sleep("Schlafverhalten"),
    PainPsychosocial("Schmerz und Psychosoziales"),
    LowerExtremity("Untere Extremität"),
    Miscellaneous("Verschiedenes"),
    Spine("Wirbelsäule"),
}

/** A part of a form, with a [title] and a [hint] above its items, if it has them. */
data class Section(val title: String? = null, val hint: String = "", val items: List<Item>)

/**
 * An answer to choose, with its [points]; [exclusive] such as "nein", which unchecks the others of a checklist;
 * [notApplicable] such as "Nicht zutreffend", which answers a [ChoiceItem] but counts as left out, without points.
 */
data class Option(val id: String, val label: String, val points: Int = 0, val exclusive: Boolean = false, val notApplicable: Boolean = false)

/**
 * That an item shows only once item [itemId] is answered with one of [values]: option ids, or [YES] for a yes; or, if
 * given, once [orElse] holds, such as "Falls eine oder beide Fragen mit „Ja“ beantwortet werden".
 */
data class Condition(val itemId: String, val values: Set<String>, val orElse: Condition? = null) {
    /** This one and each it may hold in place of. */
    val alternatives: List<Condition> get() = generateSequence(this, Condition::orElse).toList()

    /** Holds once this or [other] holds. */
    infix fun or(other: Condition): Condition = copy(orElse = orElse?.or(other) ?: other)

    companion object {
        const val YES = "ja"

        /** Shown once [itemId] is answered yes. */
        fun ifYes(itemId: String) = Condition(itemId, setOf(YES))
    }
}

/** A question or field of a form, by its stable [id]; shown only once [showIf] holds, if it has a condition. */
sealed interface Item {
    val id: String
    val text: String
    val showIf: Condition?

    /** Whether its answer has points, to count in a score. */
    val scorable: Boolean get() = false
}

/** One of fixed [options] to choose, each with points; [reversed] counts them the other way round. */
data class ChoiceItem(
    override val id: String,
    override val text: String,
    val options: List<Option>,
    val reversed: Boolean = false,
    override val showIf: Condition? = null,
) : Item {
    override val scorable: Boolean get() = true
}

/** Yes or no, with [yesPoints] and [noPoints] where a score counts it. */
data class YesNoItem(
    override val id: String,
    override val text: String,
    val yesPoints: Int = 1,
    val noPoints: Int = 0,
    override val showIf: Condition? = null,
) : Item {
    override val scorable: Boolean get() = true
}

/**
 * A whole number from [min] to [max] in [step]s, such as 0, 10 … 100, its ends named, such as "kein Schmerz" and
 * "maximaler Schmerz"; [reversed] counts it the other way round.
 */
data class ScaleItem(
    override val id: String,
    override val text: String,
    val min: Int = 0,
    val max: Int = 10,
    val minLabel: String = "",
    val maxLabel: String = "",
    val reversed: Boolean = false,
    val step: Int = 1,
    override val showIf: Condition? = null,
) : Item {
    override val scorable: Boolean get() = true

    /** The numbers to choose, from [min] to [max]. */
    val steps: List<Int> get() = (min..max step step).toList()

    init {
        require(step >= 1 && (max - min) % step == 0) { "$id: the steps must lead from min to max" }
    }
}

/**
 * Any of [options] to check, and if [otherLabel] is given, such as "Sonstiges", something else in words. If [scored],
 * it makes the points of the options checked, such as one for each statement that applies; nothing checked makes 0.
 */
data class ChecklistItem(
    override val id: String,
    override val text: String,
    val options: List<Option>,
    val otherLabel: String? = null,
    val scored: Boolean = false,
    override val showIf: Condition? = null,
) : Item {
    override val scorable: Boolean get() = scored
}

/**
 * A number in [unit], such as minutes or metres, with two [decimals] at most, or whole. With a [max], such as the days of
 * three months, a whole number from 0 up to it, which a score counts as its points: [pointsEach] for each, such as 3 for
 * each problem "stark belastend". Or with [steps], any number, counted by the step it lies in, such as the PSQI's minutes
 * until asleep.
 */
data class NumberItem(
    override val id: String,
    override val text: String,
    val unit: String = "",
    val decimals: Boolean = false,
    val max: Int? = null,
    val pointsEach: Int = 1,
    override val showIf: Condition? = null,
    val steps: Steps? = null,
) : Item {
    override val scorable: Boolean get() = max != null || steps != null

    /** Whether [hundredths] may be answered: any number, or with a [max], a whole one from 0 up to it. */
    fun accepts(hundredths: Long): Boolean = max == null || hundredths % 100 == 0L && hundredths in 0..max * 100L

    init {
        require(max == null || max >= 1 && !decimals) { "$id: a number counted as points is whole, up to a positive most" }
        require(pointsEach >= 1 && (pointsEach == 1 || max != null)) { "$id: only a number counted as points counts each as more" }
        require(max == null || steps == null) { "$id: a number counts as points or by steps, not both" }
    }
}

/**
 * A time of the day, as hh:mm, or with [duration] a time as long, such as hours slept; from 0:00 to 23:59 either way. A
 * score counts its minutes, such as the PSQI's hours slept, or the share of a span ([ScoreRule.ShareOfSpan]).
 */
data class TimeItem(
    override val id: String,
    override val text: String,
    val duration: Boolean = false,
    override val showIf: Condition? = null,
) : Item {
    override val scorable: Boolean get() = true

    companion object {
        /** The minutes of a day: a time lies below. */
        const val DAY = 24 * 60

        /** The minutes of [typed], such as "7:30", "07:30", "7.30", "7,30" or "7" for 7:00; none for what is no time of 0:00 to 23:59. */
        fun parse(typed: String): Int? {
            val match = Regex("""(\d{1,2})(?:[:.,](\d{2}))?""").matchEntire(typed.trim()) ?: return null
            val hours = match.groupValues[1].toInt()
            val minutes = match.groupValues[2].ifEmpty { "0" }.toInt()
            return if (hours < 24 && minutes < 60) hours * 60 + minutes else null
        }

        /** [minutes] as hh:mm, such as "07:30". */
        fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

/**
 * Points by where a value lies: those of the first of [below] whose bound, a whole number, the value lies below; else
 * [otherwise]. Such as the PSQI's "≤ 15 Minuten 0, 16–30 Minuten 1, 31–60 Minuten 2, > 60 Minuten 3" as 16 to 0, 31 to 1,
 * 61 to 2, otherwise 3.
 */
data class Steps(val below: List<Pair<Int, Int>>, val otherwise: Int) {
    /** The fewest and the most points. */
    val range: IntRange get() = (below.map { it.second } + otherwise).let { it.min()..it.max() }

    init {
        require(below.isNotEmpty() && below.zipWithNext().all { (a, b) -> a.first < b.first }) { "steps must rise" }
    }
}

/** Something in words. */
data class TextItem(
    override val id: String,
    override val text: String,
    override val showIf: Condition? = null,
) : Item

/**
 * Goals named freely, as many as set for the patient, each rated at one of [levels], such as the GAS's "-2 Schlechter" to
 * "+2 Ziel deutlich übertroffen", their points the level; [goalLabel] names one, such as "Ziel". Only a [ScoreRule.GoalT]
 * counts them.
 */
data class GoalsItem(
    override val id: String,
    override val text: String,
    val goalLabel: String,
    val levels: List<Option>,
    override val showIf: Condition? = null,
) : Item {
    override val scorable: Boolean get() = true

    init {
        require(levels.isNotEmpty() && levels.map(Option::points).toSet().size == levels.size) { "$id: levels of points of their own" }
    }
}

/**
 * Where it hurts, as [BodyRegion]s to check, and in words; in place of "bitte einzeichnen". The [regions] to offer, in
 * their order: those of the body unless the form asks of others, such as those of the jaw.
 */
data class BodyRegionsItem(
    override val id: String,
    override val text: String,
    override val showIf: Condition? = null,
    val regions: List<BodyRegion> = BodyRegion.body,
) : Item {
    init {
        require(regions.isNotEmpty() && regions.size == regions.toSet().size) { "$id: regions to offer, each once" }
    }
}

/**
 * The regions to check, the limbs each left and right: those of the [body], and those of the [jaw] and the face, such as
 * the DC/TMD names them (#73), each side apart. [id] is what is stored; new ones come at the end, as answers sort by order.
 */
enum class BodyRegion(val id: String, val label: String) {
    Head("kopf", "Kopf"),
    CervicalSpine("hws", "HWS"),
    ThoracicSpine("bws", "BWS"),
    LumbarSpine("lws", "LWS"),
    Pelvis("becken", "Becken/ISG"),
    ShoulderLeft("schulter-l", "Schulter links"),
    ShoulderRight("schulter-r", "Schulter rechts"),
    ElbowLeft("ellenbogen-l", "Ellenbogen links"),
    ElbowRight("ellenbogen-r", "Ellenbogen rechts"),
    HandLeft("hand-l", "Hand links"),
    HandRight("hand-r", "Hand rechts"),
    HipLeft("huefte-l", "Hüfte links"),
    HipRight("huefte-r", "Hüfte rechts"),
    KneeLeft("knie-l", "Knie links"),
    KneeRight("knie-r", "Knie rechts"),
    FootLeft("fuss-l", "Fuß links"),
    FootRight("fuss-r", "Fuß rechts"),
    TemporalisRight("temporalis-r", "M. temporalis rechts"),
    TemporalisLeft("temporalis-l", "M. temporalis links"),
    MasseterRight("masseter-r", "M. masseter rechts"),
    MasseterLeft("masseter-l", "M. masseter links"),
    JawJointRight("kiefergelenk-r", "Kiefergelenk rechts"),
    JawJointLeft("kiefergelenk-l", "Kiefergelenk links"),
    OtherMasticatoryRight("kaumuskeln-r", "Andere Kaumuskeln rechts"),
    OtherMasticatoryLeft("kaumuskeln-l", "Andere Kaumuskeln links"),
    NonMasticatoryRight("nicht-mastik-r", "Nicht-mastikatorische Muskeln rechts"),
    NonMasticatoryLeft("nicht-mastik-l", "Nicht-mastikatorische Muskeln links"),
    FaceRight("gesicht-r", "Gesicht rechts"),
    FaceLeft("gesicht-l", "Gesicht links"),
    MouthAndTeeth("mund", "Mund und Zähne"),
    ;

    companion object {
        fun of(id: String): BodyRegion? = entries.find { it.id == id }

        /** Those of the jaw and the face: the mouth, each half of the face, the joints and the muscles, right before left. */
        val jaw: List<BodyRegion> = listOf(
            MouthAndTeeth, FaceRight, FaceLeft, JawJointRight, JawJointLeft, TemporalisRight, TemporalisLeft, MasseterRight, MasseterLeft,
            OtherMasticatoryRight, OtherMasticatoryLeft, NonMasticatoryRight, NonMasticatoryLeft,
        )

        /** Those of the body, from the head down: what a form asks of where it hurts, unless it asks of others. */
        val body: List<BodyRegion> = entries.filter { it !in jaw }
    }
}

/**
 * A result of a form, such as its sum or a subscale: by [rule] over the points of [items], divided by [divisor], in [unit],
 * better the [direction] way, its [bands] naming where it lies. Reckoned once [minAnswered] of its items are answered, all
 * of them unless the original allows fewer, or all but those [optional]. Or, with no items, the sum of the scores before it
 * that [sumOf] names, such as a total of pain and function, once each of them has a value, each by its weight and plus a
 * [constant] if given, such as the IES-R's formula; or the value of the first of [grades] whose bounds on the
 * scores before it hold, such as the GCPS's grade from its pain and its impairment, once each score it bounds has a value.
 */
data class Score(
    val id: String,
    val name: String,
    val items: List<String>,
    val rule: ScoreRule = ScoreRule.Sum,
    val unit: String = "Punkte",
    val direction: Direction,
    /** From the lowest up, each up to and including its bound. */
    val bands: List<Band> = emptyList(),
    val minAnswered: Int = items.size,
    /** Such as 2, for "geteilt durch 2". */
    val divisor: Int = 1,
    /** The ids of the scores before it whose values it adds up, in place of items. */
    val sumOf: List<String> = emptyList(),
    /** For each of [sumOf], in hundredths, what its value counts, such as -2 for "-0,02 x Intrusion"; each 100 if none. */
    val weights: List<Int> = emptyList(),
    /** In hundredths, added to the sum of [sumOf], such as -436 for "- 4,36". */
    val constant: Int = 0,
    /** Items that may stay unanswered, counting nothing, such as the PHQ-15's "(nur Frauen)": all the others needed. */
    val optional: List<String> = emptyList(),
    /** The grades it takes by the scores before it, in place of items: the first that holds, the last without bounds. */
    val grades: List<Grade> = emptyList(),
    /** Pairs of its items of which a sum counts only the one with fewer points, such as the Mini-BESTest's two sides. */
    val worseOf: List<List<String>> = emptyList(),
    /** What its value counts in points by where it lies, such as a PSQI component from 0 to 3; the value itself if none. */
    val steps: Steps? = null,
) {
    init {
        require(listOf(items, sumOf, grades).count(List<*>::isNotEmpty) == 1) { "$id: a score counts either items, other scores or grades" }
        require(items.isEmpty() || minAnswered in 1..items.size) { "$id: minAnswered must lie within 1 and the number of its items" }
        require(items.isNotEmpty() || rule == ScoreRule.Sum && divisor == 1) { "$id: other scores are only added up or graded, not divided" }
        // Else answers enough would reach no grade, the same as too few.
        require(grades.isEmpty() || grades.last().bounds.isEmpty()) { "$id: the last grade takes what the others leave" }
        require(divisor >= 1) { "$id: the divisor must be positive" }
        require(weights.isEmpty() || weights.size == sumOf.size) { "$id: one weight for each score added up" }
        require(constant == 0 || sumOf.isNotEmpty()) { "$id: only a score of others adds a constant" }
        require(items.containsAll(optional) && optional.size == optional.toSet().size && (optional.isEmpty() || minAnswered == items.size && rule == ScoreRule.Sum)) {
            "$id: optional items are among its items, of a sum that needs all the others"
        }
        require(bands.zipWithNext().all { (a, b) -> a.upTo < b.upTo }) { "$id: bands must rise" }
        require(
            worseOf.all { it.size == 2 && it.toSet().size == 2 && items.containsAll(it) } && worseOf.flatten().let { it.size == it.toSet().size } &&
                (worseOf.isEmpty() || rule == ScoreRule.Sum && minAnswered == items.size && optional.isEmpty()),
        ) { "$id: pairs of its own items, apart, of a sum that needs them all" }
        require(rule != ScoreRule.GoalT || items.size == 1 && divisor == 1) { "$id: a T-score counts one item of goals" }
        require(rule != ScoreRule.ShareOfSpan || items.size == 3 && divisor == 1 && minAnswered == 3 && optional.isEmpty()) {
            "$id: a share of a span counts its part and the span's ends, all three"
        }
        require(steps == null || items.isNotEmpty()) { "$id: only a score of items takes steps" }
    }
}

/** A grade of [value] that a score takes once all its [bounds] hold, such as the GCPS's grade II. */
data class Grade(val value: Int, val bounds: List<Bound>) {
    constructor(value: Int, vararg bounds: Bound) : this(value, bounds.toList())
}

/**
 * That the exact value of score [scoreId] lies [from] and [below] the bounds given, in hundredths, such as the GCPS's
 * "< 50 Schmerzintensität": from included, below not, without rounding first.
 */
data class Bound(val scoreId: String, val from: Long? = null, val below: Long? = null) {
    init {
        require(from != null || below != null) { "$scoreId: a bound needs a from or a below" }
        require(from == null || below == null || from < below) { "$scoreId: a bound must leave room" }
    }

    companion object {
        /** Below whole number [whole]. */
        fun below(scoreId: String, whole: Int) = Bound(scoreId, below = whole * 100L)
    }
}

/** How a score is reckoned from the points of its answered items. */
enum class ScoreRule {
    /** Their sum. */
    Sum,

    /** Their mean. */
    Mean,

    /** Their sum in percent of the most they could reach: the maximum shrinks with each item left out. */
    Percent,

    /** Their sum from its least to its most, as 0 to 100. */
    ZeroTo100,

    /** Their sum in percent of the most all its items could reach: an item left out counts its least. */
    PercentOfAll,

    /**
     * Their sum, each item left out counted as the mean of those answered in its section of the form, once half of each
     * section's items are answered: the PRTEE's "Mittelwert der entsprechenden Kategorie" (09.11.2026).
     */
    MeanFilled,

    /**
     * The T-score of the goals rated, each as much as the others: 50 + 10 · Σx / √(0,7 · n + 0,3 · n²), after Kiresuk and
     * Sherman with ρ = 0,3 (09.11.2026), once at least one goal is set and each is rated.
     */
    GoalT,

    /**
     * Its first item, a duration, in percent of the time from its second to its third, times of the day, over midnight if
     * the third is earlier: the PSQI's sleep efficiency, hours slept of those in bed (09.11.2026); none for no time in bed.
     */
    ShareOfSpan,
}

/** Where a score lies, up to and including [upTo], in hundredths, named as the original names it; unnamed without a [label]. */
data class Band(val upTo: Long, val label: String?) {
    companion object {
        /** Up to whole number [upTo]. */
        fun upTo(upTo: Int, label: String?) = Band(upTo * 100L, label)
    }
}
