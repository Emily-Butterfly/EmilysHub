package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

/** How a form reckons its results from the answers (SPEC.md §14): pure logic, without Android. */
class ScoringTest {

    private val zeroToThree = listOf(Option("0", "nie", 0), Option("1", "selten", 1), Option("2", "oft", 2), Option("3", "immer", 3))

    private fun choice(id: String, reversed: Boolean = false) = ChoiceItem(id, "Frage $id", zeroToThree, reversed)

    private fun form(vararg scores: Score, items: List<Item> = listOf(choice("a"), choice("b"), choice("c"))) =
        Assessment(id = "probe", name = "Probe", categories = listOf(AssessmentCategory.Miscellaneous), source = "Probe", sections = listOf(Section(items = items)), scores = scores.toList())

    private fun chosen(vararg answers: Pair<String, String>): Map<String, Answer> = answers.associate { (item, option) -> item to Answer.Chosen(option) }

    private fun value(form: Assessment, answers: Map<String, Answer>): Long? = Scoring.results(form, answers).single().value

    @Test
    fun `a sum adds the points of its items, in hundredths`() {
        val form = form(Score("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter))

        assertEquals(600L, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
    }

    @Test
    fun `without every item answered there is no result, unless the original allows fewer`() {
        val all = form(Score("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter))
        val two = form(Score("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, minAnswered = 2))

        assertEquals(null, value(all, chosen("a" to "1", "b" to "2")))
        assertEquals(300L, value(two, chosen("a" to "1", "b" to "2")))
        assertEquals(null, value(two, chosen("a" to "1")))
    }

    @Test
    fun `a reversed item counts its points the other way round`() {
        val items = listOf(choice("a"), choice("b", reversed = true), ScaleItem("s", "Wie stark?", min = 0, max = 10, reversed = true))
        val form = form(Score("sum", "Summe", listOf("a", "b", "s"), direction = Direction.HigherIsBetter), items = items)

        // 1 + (3 - 1) + (10 - 7)
        assertEquals(600L, value(form, chosen("a" to "1", "b" to "1") + ("s" to Answer.OnScale(7))))
    }

    @Test
    fun `a mean divides by the items answered, rounded to hundredths`() {
        val form = form(Score("mean", "Mittelwert", listOf("a", "b", "c"), rule = ScoreRule.Mean, direction = Direction.LowerIsBetter, minAnswered = 2))

        assertEquals(200L, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
        // 4 / 3 = 1,333…
        assertEquals(133L, value(form, chosen("a" to "1", "b" to "1", "c" to "2")))
        // 5 / 3 = 1,666…
        assertEquals(167L, value(form, chosen("a" to "1", "b" to "2", "c" to "2")))
        assertEquals(150L, value(form, chosen("a" to "1", "b" to "2")))
    }

    @Test
    fun `a percentage lowers the most possible by each item left out`() {
        val form = form(Score("pct", "Prozent", listOf("a", "b", "c"), rule = ScoreRule.Percent, unit = "%", direction = Direction.LowerIsBetter, minAnswered = 2))

        // 6 of 9
        assertEquals(6667L, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
        // 3 of 6, the third left out
        assertEquals(5000L, value(form, chosen("a" to "1", "b" to "2")))
    }

    @Test
    fun `a score from 0 to 100 runs from the least to the most possible`() {
        val one = Option("1", "eins", 1)
        val items = listOf(ChoiceItem("a", "A", listOf(one, Option("5", "fünf", 5))), ChoiceItem("b", "B", listOf(one, Option("5", "fünf", 5))))
        val form = form(Score("x", "0–100", listOf("a", "b"), rule = ScoreRule.ZeroTo100, unit = "", direction = Direction.HigherIsBetter), items = items)

        assertEquals(0L, value(form, chosen("a" to "1", "b" to "1")))
        // (6 - 2) / (10 - 2)
        assertEquals(5000L, value(form, chosen("a" to "1", "b" to "5")))
        assertEquals(10000L, value(form, chosen("a" to "5", "b" to "5")))
    }

    @Test
    fun `a band names where a result lies, up to and including its bound`() {
        val bands = listOf(Band.upTo(2, "wenig"), Band.upTo(5, "mittel"), Band.upTo(9, "viel"))
        val form = form(Score("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, bands = bands))
        fun band(vararg answers: Pair<String, String>) = Scoring.results(form, chosen(*answers)).single().band

        assertEquals("wenig", band("a" to "0", "b" to "0", "c" to "0"))
        assertEquals("wenig", band("a" to "1", "b" to "1", "c" to "0"))
        assertEquals("mittel", band("a" to "1", "b" to "1", "c" to "1"))
        assertEquals("mittel", band("a" to "3", "b" to "2", "c" to "0"))
        assertEquals("viel", band("a" to "3", "b" to "3", "c" to "0"))
        assertEquals(null, band("a" to "3", "b" to "3"))
    }

    @Test
    fun `a conditional item shows, and counts, only once its condition holds`() {
        val items = listOf(
            YesNoItem("hurts", "Haben Sie Schmerzen?"),
            ChoiceItem("how", "Wie oft?", zeroToThree, showIf = Condition.ifYes("hurts")),
            TextItem("where", "Wo?", showIf = Condition("how", setOf("2", "3"))),
        )
        val form = form(Score("sum", "Summe", listOf("how"), direction = Direction.LowerIsBetter), items = items)
        val shown = { answers: Map<String, Answer> -> Scoring.visible(form, answers).map(Item::id) }

        assertEquals(listOf("hurts"), shown(emptyMap()))
        assertEquals(listOf("hurts", "how"), shown(mapOf("hurts" to Answer.YesNo(true))))
        assertEquals(listOf("hurts", "how", "where"), shown(mapOf("hurts" to Answer.YesNo(true), "how" to Answer.Chosen("3"))))
        // Hidden with what it depends on.
        assertEquals(listOf("hurts"), shown(mapOf("hurts" to Answer.YesNo(false), "how" to Answer.Chosen("3"))))

        assertEquals(300L, value(form, mapOf("hurts" to Answer.YesNo(true), "how" to Answer.Chosen("3"))))
        assertEquals(null, value(form, mapOf("hurts" to Answer.YesNo(false), "how" to Answer.Chosen("3"))))
    }

    @Test
    fun `an answer to an item no longer in the form stays, and counts nowhere`() {
        val old = mapOf("a" to Answer.Chosen("1"), "gone" to Answer.Text("früher gefragt"))
        val form = form(Score("sum", "Summe", listOf("a"), direction = Direction.LowerIsBetter), items = listOf(choice("a")))

        assertEquals(mapOf("gone" to Answer.Text("früher gefragt")), Scoring.orphans(form, old))
        assertEquals(100L, value(form, old))
    }

    @Test
    fun `an answer that no longer fits its item, which changed its kind, stays apart as well`() {
        val form = form(items = listOf(choice("a"), TextItem("b", "Wo?")))

        assertEquals(mapOf("a" to Answer.YesNo(true)), Scoring.orphans(form, mapOf("a" to Answer.YesNo(true), "b" to Answer.Text("Knie"))))
    }

    @Test
    fun `a score's range follows from its rule and items`() {
        val form = form(
            Score("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter),
            Score("mean", "Mittelwert", listOf("a", "b"), rule = ScoreRule.Mean, direction = Direction.LowerIsBetter),
            Score("pct", "Prozent", listOf("a"), rule = ScoreRule.Percent, direction = Direction.LowerIsBetter),
            Score("x", "0–100", listOf("a"), rule = ScoreRule.ZeroTo100, direction = Direction.LowerIsBetter),
        )

        assertEquals(listOf(0L..900L, 0L..300L, 0L..10000L, 0L..10000L), form.scores.map { Scoring.range(form, it) })
    }

    @Test
    fun `checking an exclusive option such as nein unchecks the others, and the other way round`() {
        val item = ChecklistItem("k", "Haben Sie:", listOf(Option("ruhe", "Ruheschmerzen"), Option("nacht", "Nachtschmerzen"), Option("nein", "nein", exclusive = true)))
        val some = Answer.Checked(setOf("ruhe"), "abends")

        assertEquals(Answer.Checked(setOf("ruhe", "nacht"), "abends"), some.toggled(item, "nacht"))
        assertEquals(Answer.Checked(setOf("nein"), "abends"), some.toggled(item, "nein"))
        assertEquals(Answer.Checked(setOf("nacht")), Answer.Checked(setOf("nein")).toggled(item, "nacht"))
        assertEquals(Answer.Checked(emptySet(), "abends"), some.toggled(item, "ruhe"))
    }

    @Test
    fun `a band without a name leaves a range unnamed`() {
        val bands = listOf(Band.upTo(2, null), Band.upTo(9, "viel"))
        val form = form(Score("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, bands = bands))

        assertEquals(null, Scoring.results(form, chosen("a" to "1", "b" to "1", "c" to "0")).single().band)
        assertEquals("viel", Scoring.results(form, chosen("a" to "1", "b" to "1", "c" to "1")).single().band)
    }

    @Test
    fun `a scored checklist counts the points of what is ticked, nothing ticked being 0`() {
        val list = ChecklistItem("k", "Trifft zu:", listOf(Option("x", "x", 1), Option("y", "y", 1), Option("z", "z", 2)), scored = true)
        val form = form(Score("sum", "Summe", listOf("k"), direction = Direction.LowerIsBetter), items = listOf(list))

        assertEquals(0L, value(form, emptyMap()))
        assertEquals(300L, value(form, mapOf("k" to Answer.Checked(setOf("x", "z")))))
        assertEquals(0L..400L, Scoring.range(form, form.scores.single()))
    }

    @Test
    fun `a scored checklist under a condition counts only once shown`() {
        val items = listOf(
            YesNoItem("hurts", "Haben Sie Schmerzen?"),
            ChecklistItem("k", "Trifft zu:", listOf(Option("x", "x", 1)), scored = true, showIf = Condition.ifYes("hurts")),
        )
        val form = form(Score("sum", "Summe", listOf("k"), direction = Direction.LowerIsBetter), items = items)

        assertEquals(null, value(form, mapOf("hurts" to Answer.YesNo(false))))
        assertEquals(0L, value(form, mapOf("hurts" to Answer.YesNo(true))))
        assertEquals(false, Scoring.reachesResultUnanswered(form))
    }

    @Test
    fun `an option that does not apply counts as unanswered and lowers the most possible`() {
        val options = zeroToThree + Option("na", "Nicht zutreffend", notApplicable = true)
        val items = listOf("a", "b", "c").map { ChoiceItem(it, "Frage $it", options) }
        val form = form(Score("pct", "Prozent", listOf("a", "b", "c"), rule = ScoreRule.Percent, unit = "%", direction = Direction.HigherIsBetter, minAnswered = 1), items = items)

        // 2 + 3 of 6
        assertEquals(8333L, value(form, chosen("a" to "2", "b" to "3", "c" to "na")))
        assertEquals(null, value(form, chosen("a" to "na", "b" to "na", "c" to "na")))
        assertEquals(0L..10000L, Scoring.range(form, form.scores.single()))
    }

    @Test
    fun `a scale in steps takes only its steps`() {
        val scale = ScaleItem("s", "Wie zuversichtlich?", min = 0, max = 100, step = 10)
        val form = form(Score("sum", "Summe", listOf("s"), direction = Direction.HigherIsBetter), items = listOf(scale))

        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100), scale.steps)
        assertEquals(7000L, value(form, mapOf("s" to Answer.OnScale(70))))
        assertEquals(null, value(form, mapOf("s" to Answer.OnScale(75))))
    }

    @Test
    fun `items shown in place of each other count once in a score's range`() {
        val which = ChoiceItem("w", "Welche?", listOf(Option("x", "x"), Option("y", "y")))
        val items = listOf(
            choice("a"),
            which,
            ChoiceItem("x", "Frage x", zeroToThree, showIf = Condition("w", setOf("x"))),
            ChoiceItem("y", "Frage y", listOf(Option("0", "0", 0), Option("5", "5", 5)), showIf = Condition("w", setOf("y"))),
        )
        val form = form(Score("sum", "Summe", listOf("a", "x", "y"), direction = Direction.HigherIsBetter, minAnswered = 2), items = items)

        // a up to 3, and x up to 3 or y up to 5
        assertEquals(0L..800L, Scoring.range(form, form.scores.single()))
        assertEquals(700L, value(form, chosen("a" to "2", "w" to "y", "y" to "5")))
    }
}
