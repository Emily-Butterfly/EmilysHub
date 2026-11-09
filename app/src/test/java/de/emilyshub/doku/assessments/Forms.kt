package de.emilyshub.doku.assessments

/** The items of a score of items, for the forms' tests, which know which of their scores count items. */
internal val Score.items: List<String> get() = (this as ItemScore).items

/** Answers and results of the built-in forms, by id, for their tests. */
internal object Forms {

    fun form(id: String) = checkNotNull(BuiltInAssessments.byId(id)) { id }

    /** The values of the results of [id] with [answers], in the order of its scores. */
    fun values(id: String, answers: Map<String, Answer>) = Scoring.results(form(id), answers).map(ScoreResult::value)

    /** The value of result [score] of [id] with [answers]. */
    fun value(id: String, answers: Map<String, Answer>, score: String) =
        Scoring.results(form(id), answers).single { it.score.id == score }.value

    /** The items of [id] with options among [items] (all if none), answered in order by the option of these [points], those left out unanswered. */
    fun chosen(id: String, vararg points: Int?, items: List<String>? = null): Map<String, Answer> =
        form(id).items.filterIsInstance<ChoiceItem>().filter { items == null || it.id in items }.zip(points.toList()).mapNotNull { (item, p) ->
            p?.let { item.id to Answer.Chosen(item.options.single { o -> o.points == p && !o.notApplicable }.id) }
        }.toMap()

    /** Each item of [id] with options among [items] (all if none) answered by the option of [points]. */
    fun all(id: String, points: Int, items: List<String>? = null): Map<String, Answer> {
        val count = form(id).items.filterIsInstance<ChoiceItem>().count { items == null || it.id in items }
        return chosen(id, *Array<Int?>(count) { points }, items = items)
    }

    /** The ids of the items of score [score] of [id]. */
    fun itemsOf(id: String, score: String) = form(id).scores.single { it.id == score }.items

    /** Each scale of [id] answered by [values], in order. */
    fun scales(id: String, vararg values: Int): Map<String, Answer> =
        form(id).items.filterIsInstance<ScaleItem>().zip(values.toList()).associate { (item, v) -> item.id to Answer.OnScale(v) }

    /** Each scale of [id] among [items] (all if none) answered by [value]. */
    fun allScales(id: String, value: Int, items: List<String>? = null): Map<String, Answer> =
        form(id).items.filterIsInstance<ScaleItem>().filter { items == null || it.id in items }.associate { it.id to Answer.OnScale(value) }

    /** The scales [ids] answered by [values], in order. */
    fun on(ids: List<String>, vararg values: Int): Map<String, Answer> = ids.zip(values.toList()).associate { (id, v) -> id to Answer.OnScale(v) }

    /** The band of score [score] of [id] at [hundredths]. */
    fun bandAt(id: String, hundredths: Long, score: Int = 0) = ScoreResult(form(id).scores[score], hundredths).band
}
