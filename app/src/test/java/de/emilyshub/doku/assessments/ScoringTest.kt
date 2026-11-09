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
        val form = form(ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter))

        assertEquals(600L, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
    }

    @Test
    fun `without every item answered there is no result, unless the original allows fewer`() {
        val all = form(ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter))
        val two = form(ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, minAnswered = 2))

        assertEquals(null, value(all, chosen("a" to "1", "b" to "2")))
        assertEquals(300L, value(two, chosen("a" to "1", "b" to "2")))
        assertEquals(null, value(two, chosen("a" to "1")))
    }

    @Test
    fun `a reversed item counts its points the other way round`() {
        val items = listOf(choice("a"), choice("b", reversed = true), ScaleItem("s", "Wie stark?", min = 0, max = 10, reversed = true))
        val form = form(ItemScore("sum", "Summe", listOf("a", "b", "s"), direction = Direction.HigherIsBetter), items = items)

        // 1 + (3 - 1) + (10 - 7)
        assertEquals(600L, value(form, chosen("a" to "1", "b" to "1") + ("s" to Answer.OnScale(7))))
    }

    @Test
    fun `a mean divides by the items answered, rounded to hundredths`() {
        val form = form(ItemScore("mean", "Mittelwert", listOf("a", "b", "c"), rule = ScoreRule.Mean, direction = Direction.LowerIsBetter, minAnswered = 2))

        assertEquals(200L, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
        // 4 / 3 = 1,333…
        assertEquals(133L, value(form, chosen("a" to "1", "b" to "1", "c" to "2")))
        // 5 / 3 = 1,666…
        assertEquals(167L, value(form, chosen("a" to "1", "b" to "2", "c" to "2")))
        assertEquals(150L, value(form, chosen("a" to "1", "b" to "2")))
    }

    @Test
    fun `a percentage lowers the most possible by each item left out`() {
        val form = form(ItemScore("pct", "Prozent", listOf("a", "b", "c"), rule = ScoreRule.Percent, unit = "%", direction = Direction.LowerIsBetter, minAnswered = 2))

        // 6 of 9
        assertEquals(6667L, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
        // 3 of 6, the third left out
        assertEquals(5000L, value(form, chosen("a" to "1", "b" to "2")))
    }

    @Test
    fun `a score from 0 to 100 runs from the least to the most possible`() {
        val one = Option("1", "eins", 1)
        val items = listOf(ChoiceItem("a", "A", listOf(one, Option("5", "fünf", 5))), ChoiceItem("b", "B", listOf(one, Option("5", "fünf", 5))))
        val form = form(ItemScore("x", "0–100", listOf("a", "b"), rule = ScoreRule.ZeroTo100, unit = "", direction = Direction.HigherIsBetter), items = items)

        assertEquals(0L, value(form, chosen("a" to "1", "b" to "1")))
        // (6 - 2) / (10 - 2)
        assertEquals(5000L, value(form, chosen("a" to "1", "b" to "5")))
        assertEquals(10000L, value(form, chosen("a" to "5", "b" to "5")))
    }

    @Test
    fun `a band names where a result lies, up to and including its bound`() {
        val bands = listOf(Band.upTo(2, "wenig"), Band.upTo(5, "mittel"), Band.upTo(9, "viel"))
        val form = form(ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, bands = bands))
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
        val form = form(ItemScore("sum", "Summe", listOf("how"), direction = Direction.LowerIsBetter), items = items)
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
        val form = form(ItemScore("sum", "Summe", listOf("a"), direction = Direction.LowerIsBetter), items = listOf(choice("a")))

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
            ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter),
            ItemScore("mean", "Mittelwert", listOf("a", "b"), rule = ScoreRule.Mean, direction = Direction.LowerIsBetter),
            ItemScore("pct", "Prozent", listOf("a"), rule = ScoreRule.Percent, direction = Direction.LowerIsBetter),
            ItemScore("x", "0–100", listOf("a"), rule = ScoreRule.ZeroTo100, direction = Direction.LowerIsBetter),
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
        val form = form(ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, bands = bands))

        assertEquals(null, Scoring.results(form, chosen("a" to "1", "b" to "1", "c" to "0")).single().band)
        assertEquals("viel", Scoring.results(form, chosen("a" to "1", "b" to "1", "c" to "1")).single().band)
    }

    @Test
    fun `a scored checklist counts the points of what is ticked, nothing ticked being 0`() {
        val list = ChecklistItem("k", "Trifft zu:", listOf(Option("x", "x", 1), Option("y", "y", 1), Option("z", "z", 2)), scored = true)
        val form = form(ItemScore("sum", "Summe", listOf("k"), direction = Direction.LowerIsBetter), items = listOf(list))

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
        val form = form(ItemScore("sum", "Summe", listOf("k"), direction = Direction.LowerIsBetter), items = items)

        assertEquals(null, value(form, mapOf("hurts" to Answer.YesNo(false))))
        assertEquals(0L, value(form, mapOf("hurts" to Answer.YesNo(true))))
        assertEquals(false, Scoring.reachesResultUnanswered(form))
    }

    @Test
    fun `an option that does not apply counts as unanswered and lowers the most possible`() {
        val options = zeroToThree + Option("na", "Nicht zutreffend", notApplicable = true)
        val items = listOf("a", "b", "c").map { ChoiceItem(it, "Frage $it", options) }
        val form = form(ItemScore("pct", "Prozent", listOf("a", "b", "c"), rule = ScoreRule.Percent, unit = "%", direction = Direction.HigherIsBetter, minAnswered = 1), items = items)

        // 2 + 3 of 6
        assertEquals(8333L, value(form, chosen("a" to "2", "b" to "3", "c" to "na")))
        assertEquals(null, value(form, chosen("a" to "na", "b" to "na", "c" to "na")))
        assertEquals(0L..10000L, Scoring.range(form, form.scores.single()))
    }

    @Test
    fun `a scale in steps takes only its steps`() {
        val scale = ScaleItem("s", "Wie zuversichtlich?", min = 0, max = 100, step = 10)
        val form = form(ItemScore("sum", "Summe", listOf("s"), direction = Direction.HigherIsBetter), items = listOf(scale))

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
        val form = form(ItemScore("sum", "Summe", listOf("a", "x", "y"), direction = Direction.HigherIsBetter, minAnswered = 2), items = items)

        // a up to 3, and x up to 3 or y up to 5
        assertEquals(0L..800L, Scoring.range(form, form.scores.single()))
        assertEquals(700L, value(form, chosen("a" to "2", "w" to "y", "y" to "5")))
    }

    @Test
    fun `a number between the steps of a scale is kept apart, as answered to another scale`() {
        val scale = ScaleItem("s", "Wie zuversichtlich?", min = 0, max = 100, step = 10)
        val form = form(items = listOf(scale))

        assertEquals(mapOf("s" to Answer.OnScale(75)), Scoring.orphans(form, mapOf("s" to Answer.OnScale(75))))
        assertEquals(emptyMap<String, Answer>(), Scoring.orphans(form, mapOf("s" to Answer.OnScale(70))))
    }

    @Test
    fun `items shown by different ticks of a checklist may show together, and count each in a score's range`() {
        val list = ChecklistItem("w", "Welche?", listOf(Option("x", "x"), Option("y", "y")))
        val items = listOf(
            list,
            ChoiceItem("x", "Frage x", zeroToThree, showIf = Condition("w", setOf("x"))),
            ChoiceItem("y", "Frage y", zeroToThree, showIf = Condition("w", setOf("y"))),
        )
        val form = form(ItemScore("sum", "Summe", listOf("x", "y"), direction = Direction.HigherIsBetter), items = items)

        assertEquals(0L..600L, Scoring.range(form, form.scores.single()))
    }

    @Test
    fun `a percentage of all keeps the most possible, an item left out counting its least`() {
        val form = form(ItemScore("all", "Prozent", listOf("a", "b", "c"), rule = ScoreRule.PercentOfAll, unit = "%", direction = Direction.LowerIsBetter, minAnswered = 1))

        // 3 + 2 of 9
        assertEquals(5556L, value(form, chosen("a" to "3", "b" to "2")))
        assertEquals(10000L, value(form, chosen("a" to "3", "b" to "3", "c" to "3")))
        assertEquals(null, value(form, emptyMap()))
        assertEquals(0L..10000L, Scoring.range(form, form.scores.single()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a percentage of all counts no item shown only under a condition`() {
        val items = listOf(YesNoItem("w", "Sport?"), ChoiceItem("x", "Frage x", zeroToThree, showIf = Condition.ifYes("w")))
        form(ItemScore("all", "Prozent", listOf("x"), rule = ScoreRule.PercentOfAll, unit = "%", direction = Direction.LowerIsBetter), items = items)
    }

    @Test
    fun `a sum filled by the mean counts each item left out as the mean of those answered of its section, once half of each is answered`() {
        val sections = listOf(Section(items = listOf(choice("a"), choice("b"), choice("c"))), Section(items = listOf(choice("d"), choice("e"))))
        val score = ItemScore("filled", "Gefüllt", listOf("a", "b", "c", "d", "e"), rule = ScoreRule.MeanFilled, direction = Direction.LowerIsBetter)
        val form = Assessment(id = "probe", name = "Probe", categories = listOf(AssessmentCategory.Miscellaneous), source = "Probe", sections = sections, scores = listOf(score))

        assertEquals(1500L, value(form, chosen("a" to "3", "b" to "3", "c" to "3", "d" to "3", "e" to "3")))
        // a, b: 1 + 2 filled up to three by 1,5 = 4,5; d alone: 3 twice = 6.
        assertEquals(1050L, value(form, chosen("a" to "1", "b" to "2", "d" to "3")))
        assertEquals(null, value(form, chosen("a" to "1", "d" to "3", "e" to "3")))
        assertEquals(null, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
        assertEquals(0L..1500L, Scoring.range(form, score))
    }

    @Test
    fun `a score with a divisor is divided by it, and so is its range`() {
        val form = form(ItemScore("half", "Hälfte", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, divisor = 2))

        assertEquals(350L, value(form, chosen("a" to "1", "b" to "3", "c" to "3")))
        assertEquals(0L..450L, Scoring.range(form, form.scores.single()))
    }

    @Test
    fun `a score of others adds up their values, none while any of them has none`() {
        val first = ItemScore("ab", "a und b", listOf("a", "b"), direction = Direction.LowerIsBetter)
        val second = ItemScore("c", "c halb", listOf("c"), direction = Direction.LowerIsBetter, divisor = 2)
        val total = WeightedScore("gesamt", "Gesamt", parts = listOf("ab", "c"), direction = Direction.LowerIsBetter)
        val form = form(first, second, total)

        assertEquals(listOf(300L, 150L, 450L), Scoring.results(form, chosen("a" to "1", "b" to "2", "c" to "3")).map(ScoreResult::value))
        assertEquals(listOf(300L, null, null), Scoring.results(form, chosen("a" to "1", "b" to "2")).map(ScoreResult::value))
        assertEquals(0L..750L, Scoring.range(form, total))
    }

    @Test
    fun `a score of others adds up their exact values, rounded once`() {
        val third = ItemScore("a", "a durch 3", listOf("a"), direction = Direction.LowerIsBetter, divisor = 3)
        val b = ItemScore("b", "b durch 3", listOf("b"), direction = Direction.LowerIsBetter, divisor = 3)
        val form = form(third, b, WeightedScore("gesamt", "Gesamt", parts = listOf("a", "b"), direction = Direction.LowerIsBetter))

        // 1/3 and 1/3 are 0,33 each, but 2/3 together.
        assertEquals(listOf(33L, 33L, 67L), Scoring.results(form, chosen("a" to "1", "b" to "1")).map(ScoreResult::value))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a score of others counts only scores before it`() {
        form(WeightedScore("gesamt", "Gesamt", parts = listOf("ab"), direction = Direction.LowerIsBetter), ItemScore("ab", "a und b", listOf("a", "b"), direction = Direction.LowerIsBetter))
    }

    @Test
    fun `a number with a most counts as its points, whole and within 0 and that most`() {
        val days = NumberItem("t", "Tage", max = 90)
        val form = form(ItemScore("tage", "Tage", listOf("t"), direction = Direction.LowerIsBetter), items = listOf(days, NumberItem("x", "Sonst")))

        assertEquals(1200L, value(form, mapOf("t" to Answer.Number(1200))))
        assertEquals(null, value(form, mapOf("t" to Answer.Number(9100))))
        assertEquals(null, value(form, mapOf("t" to Answer.Number(-100))))
        assertEquals(null, value(form, mapOf("t" to Answer.Number(150))))
        assertEquals(0L..9000L, Scoring.range(form, form.scores.single()))
        assertEquals(true, days.accepts(9000) && !days.accepts(9100) && !days.accepts(-100))
        assertEquals(true, NumberItem("x", "Sonst").accepts(-12345))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a number without a most counts in no score`() {
        form(ItemScore("x", "x", listOf("x"), direction = Direction.LowerIsBetter), items = listOf(NumberItem("x", "Sonst")))
    }

    private val sum = ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter)
    private val ab = ItemScore("ab", "a und b", listOf("a", "b"), direction = Direction.LowerIsBetter, divisor = 3)

    @Test
    fun `a grade is the first whose bounds on the scores before it hold`() {
        val grade = GradedScore(
            "grad", "Grad", direction = Direction.LowerIsBetter,
            grades = listOf(Grade(1, Bound("sum", below = 400)), Grade(2, Bound("ab", below = 150)), Grade(3)),
        )
        val form = form(sum, ab, grade)

        assertEquals(100L, Scoring.results(form, chosen("a" to "1", "b" to "1", "c" to "1")).last().value)
        // 1 + 2 + 3 = 6, not below 4; (1 + 2) / 3 = 1, below 1,5.
        assertEquals(200L, Scoring.results(form, chosen("a" to "1", "b" to "2", "c" to "3")).last().value)
        assertEquals(300L, Scoring.results(form, chosen("a" to "3", "b" to "3", "c" to "3")).last().value)
        assertEquals(null, Scoring.results(form, chosen("a" to "1", "b" to "1")).last().value)
        assertEquals(100L..300L, Scoring.range(form, grade))
    }

    @Test
    fun `a grade compares the exact value, not the rounded one`() {
        // (1 + 1) / 3 is 0,666…, below 0,67, though shown rounded as 0,67.
        val grade = GradedScore("grad", "Grad", direction = Direction.LowerIsBetter, grades = listOf(Grade(0, Bound("ab", below = 67)), Grade(1)))
        val form = form(ab, grade)

        assertEquals(listOf(67L, 0L), Scoring.results(form, chosen("a" to "1", "b" to "1")).map(ScoreResult::value))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a grade counts only scores before it`() {
        form(GradedScore("grad", "Grad", direction = Direction.LowerIsBetter, grades = listOf(Grade(1, Bound("sum", below = 1)), Grade(2))), sum)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a grade's last takes what the others leave, without bounds`() {
        GradedScore("grad", "Grad", direction = Direction.LowerIsBetter, grades = listOf(Grade(1, Bound.below("sum", 4))))
    }

    @Test
    fun `an optional item may stay unanswered, counting nothing, while the others are all needed`() {
        val form = form(ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.LowerIsBetter, optional = listOf("c")))

        assertEquals(300L, value(form, chosen("a" to "1", "b" to "2")))
        assertEquals(600L, value(form, chosen("a" to "1", "b" to "2", "c" to "3")))
        assertEquals(null, value(form, chosen("a" to "1", "c" to "3")))
        assertEquals(0L..900L, Scoring.range(form, form.scores.single()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an optional item is one of the score's`() {
        ItemScore("sum", "Summe", listOf("a", "b"), direction = Direction.LowerIsBetter, optional = listOf("c"))
    }

    @Test
    fun `a score of others weighs each, adds a constant, and may fall below 0`() {
        val a = ItemScore("a", "a", listOf("a"), direction = Direction.LowerIsBetter)
        val b = ItemScore("b", "b", listOf("b"), direction = Direction.LowerIsBetter)
        // -0,02 a + 0,15 b - 0,36.
        val x = WeightedScore("x", "X", direction = Direction.LowerIsBetter, parts = listOf("a", "b"), weights = listOf(-2, 15), constant = -36)
        val form = form(a, b, x)

        assertEquals(listOf(300L, 200L, -12L), Scoring.results(form, chosen("a" to "3", "b" to "2")).map(ScoreResult::value))
        assertEquals(listOf(0L, 300L, 9L), Scoring.results(form, chosen("a" to "0", "b" to "3")).map(ScoreResult::value))
        assertEquals(-42L..9L, Scoring.range(form, x))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `weights go one to each score added up`() {
        WeightedScore("x", "X", direction = Direction.LowerIsBetter, parts = listOf("a", "b"), weights = listOf(1))
    }

    @Test
    fun `a number may count several points for each`() {
        val form = form(ItemScore("n", "n", listOf("n"), direction = Direction.LowerIsBetter), items = listOf(NumberItem("n", "Wie viele?", max = 10, pointsEach = 3)))

        assertEquals(1200L, value(form, mapOf("n" to Answer.Number(400))))
        assertEquals(0L..3000L, Scoring.range(form, form.scores.single()))
    }

    @Test
    fun `an item may show once either of two conditions holds`() {
        val items = listOf(YesNoItem("a", "A?"), YesNoItem("b", "B?"), TextItem("c", "Welche?", showIf = Condition.ifYes("a") or Condition.ifYes("b")))
        val form = form(items = items)
        fun shown(vararg yes: Pair<String, Boolean>) = Scoring.visible(form, yes.associate { (id, v) -> id to Answer.YesNo(v) }).map(Item::id)

        assertEquals(listOf("a", "b"), shown())
        assertEquals(listOf("a", "b"), shown("a" to false, "b" to false))
        assertEquals(listOf("a", "b", "c"), shown("a" to true))
        assertEquals(listOf("a", "b", "c"), shown("a" to false, "b" to true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `either condition must name an item of the form`() {
        form(items = listOf(YesNoItem("a", "A?"), TextItem("c", "Welche?", showIf = Condition.ifYes("a") or Condition.ifYes("x"))))
    }

    @Test
    fun `a sum may count of each pair only the worse, such as the Mini-BESTest's sides`() {
        val score = ItemScore("sum", "Summe", listOf("a", "b", "c"), direction = Direction.HigherIsBetter, worseOf = listOf(listOf("b", "c")))
        val form = form(score)

        // 2 + the worse of 3 and 1.
        assertEquals(300L, value(form, chosen("a" to "2", "b" to "3", "c" to "1")))
        assertEquals(500L, value(form, chosen("a" to "2", "b" to "3", "c" to "3")))
        assertEquals(null, value(form, chosen("a" to "2", "b" to "3")))
        assertEquals(0L..600L, Scoring.range(form, score))
    }

    @Test
    fun `of a pair a scored checklist shown with nothing ticked counts 0, as elsewhere`() {
        val items = listOf(choice("a"), ChecklistItem("k", "Was trifft zu?", listOf(Option("x", "x", 2)), scored = true), choice("c"))
        val score = ItemScore("sum", "Summe", listOf("a", "k", "c"), direction = Direction.HigherIsBetter, worseOf = listOf(listOf("k", "c")))
        val form = form(score, items = items)

        assertEquals(300L, value(form, chosen("a" to "3", "c" to "2")))
    }

    @Test
    fun `items shown by one choice or another are not taken as alternatives of the first`() {
        val items = listOf(
            choice("a"), choice("b"),
            choice("x", reversed = false).copy(showIf = Condition("a", setOf("1")) or Condition("b", setOf("1"))),
            choice("y").copy(showIf = Condition("a", setOf("2"))),
        )
        val score = ItemScore("sum", "Summe", listOf("x", "y"), direction = Direction.LowerIsBetter, minAnswered = 1)
        val form = form(score, items = items)

        // Both may show at once: x by b, y by a.
        assertEquals(0L..600L, Scoring.range(form, score))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the worse of a pair is of the score's own items`() {
        ItemScore("sum", "Summe", listOf("a", "b"), direction = Direction.HigherIsBetter, worseOf = listOf(listOf("b", "c")))
    }

    @Test
    fun `goals rated -2 to +2 make a T-score of 50 plus 10 times their sum over the root of 0,7 n plus 0,3 n squared`() {
        val goals = GoalsItem("ziele", "Ziele", "Ziel", numbered("-2", "-1", "0", "+1", "+2").map { it.copy(points = it.points - 2) })
        val score = ItemScore("t", "T-Wert", listOf("ziele"), rule = ScoreRule.GoalT, direction = Direction.HigherIsBetter)
        val form = form(score, items = listOf(goals))
        fun t(vararg levels: Int?) = value(form, mapOf("ziele" to Answer.Goals(levels.mapIndexed { i, level -> Goal("Ziel $i", level) })))

        assertEquals(5000L, t(0))
        assertEquals(7000L, t(2))
        assertEquals(3000L, t(-2))
        // 50 + 10 * 3 / sqrt(0,7 * 3 + 0,3 * 9) = 50 + 30 / sqrt(4,8) = 63,69…
        assertEquals(6369L, t(2, 1, 0))
        assertEquals(5000L, t(2, -2))
        assertEquals(null, t(2, null))
        assertEquals(7000L, value(form, mapOf("ziele" to Answer.Goals(listOf(Goal("Gehen", 2), Goal(" ", null))))))
        // A level the item does not have, such as from a stored answer gone wrong, is none.
        assertEquals(null, t(7))
        assertEquals(null, t())
        assertEquals(null, value(form, emptyMap()))
        assertEquals(1349L..8651L, Scoring.range(form, score))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a T-score counts goals, nothing else`() {
        form(ItemScore("t", "T-Wert", listOf("a"), rule = ScoreRule.GoalT, direction = Direction.HigherIsBetter))
    }

    @Test
    fun `a time counts its minutes since midnight, or of a duration, and only a time of the day fits it`() {
        val items = listOf(TimeItem("zeit", "Wann?"), TimeItem("dauer", "Wie lange?", duration = true))
        val form = form(ItemScore("sum", "Summe", listOf("dauer"), direction = Direction.HigherIsBetter), items = items)

        assertEquals(45000L, value(form, mapOf("dauer" to Answer.Time(7 * 60 + 30))))
        assertEquals(true, items[0].fits(Answer.Time(23 * 60 + 59)))
        assertEquals(false, items[0].fits(Answer.Time(24 * 60)))
        assertEquals(false, items[0].fits(Answer.Time(-1)))
        assertEquals(false, items[0].fits(Answer.Number(100)))
        assertEquals(0L..143900L, Scoring.range(form, form.scores.single()))
    }

    @Test
    fun `steps turn a number into points, the first bound it lies below, else the last`() {
        val minutes = NumberItem("min", "Minuten", unit = "min", steps = Steps(listOf(16 to 0, 31 to 1, 61 to 2), otherwise = 3))
        val form = form(ItemScore("sum", "Summe", listOf("min", "a"), direction = Direction.LowerIsBetter), items = listOf(minutes, choice("a")))

        fun at(min: Int) = value(form, mapOf("min" to Answer.Number(min * 100L), "a" to Answer.Chosen("0")))
        assertEquals(listOf(0L, 0L, 100L, 100L, 200L, 200L, 300L), listOf(0, 15, 16, 30, 31, 60, 61).map(::at))
        assertEquals(0L..600L, Scoring.range(form, form.scores.single()))
        // A part of a minute lies within the minute's step; below 0 is no answer.
        assertEquals(0L, value(form, mapOf("min" to Answer.Number(1550), "a" to Answer.Chosen("0"))))
        assertEquals(null, value(form, mapOf("min" to Answer.Number(-500), "a" to Answer.Chosen("0"))))
    }

    @Test
    fun `a score's steps turn its value into points, such as a PSQI component`() {
        val score = ItemScore("c", "Komponente", listOf("a", "b"), direction = Direction.LowerIsBetter, steps = Steps(listOf(1 to 0, 3 to 1, 5 to 2), otherwise = 3))
        val form = form(score)

        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2, 3 to 3).map { (a, b) -> value(form, chosen("a" to "$a", "b" to "$b")) })
        assertEquals(null, value(form, chosen("a" to "1")))
        assertEquals(0L..300L, Scoring.range(form, score))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `steps must rise`() {
        Steps(listOf(5 to 0, 5 to 1), otherwise = 2)
    }

    @Test
    fun `a share of a span is the part in percent of the time from one clock time to the other, over midnight`() {
        val items = listOf(TimeItem("bett", "Zu Bett"), TimeItem("auf", "Aufgestanden"), TimeItem("schlaf", "Geschlafen", duration = true))
        val score = ItemScore("eff", "Effizienz", listOf("schlaf", "bett", "auf"), rule = ScoreRule.ShareOfSpan, unit = "%", direction = Direction.HigherIsBetter)
        val form = form(score, items = items)

        fun at(bed: Int, up: Int, slept: Int) = value(form, mapOf("bett" to Answer.Time(bed), "auf" to Answer.Time(up), "schlaf" to Answer.Time(slept)))
        // 7 h of 8 h, from 23:00 to 7:00.
        assertEquals(8750L, at(23 * 60, 7 * 60, 7 * 60))
        // 6 h of 7 h, from 0:30 to 7:30: 85,71…
        assertEquals(8571L, at(30, 7 * 60 + 30, 6 * 60))
        // No time in bed: no share.
        assertEquals(null, at(7 * 60, 7 * 60, 6 * 60))
        assertEquals(null, value(form, mapOf("bett" to Answer.Time(0), "auf" to Answer.Time(60))))
        // A time stored that is none, as of a later version, counts nowhere.
        assertEquals(null, at(23 * 60, 5000, 7 * 60))
    }

    @Test
    fun `a share of a span takes steps, as the PSQI's efficiency`() {
        val items = listOf(TimeItem("bett", "Zu Bett"), TimeItem("auf", "Aufgestanden"), TimeItem("schlaf", "Geschlafen", duration = true))
        val score = ItemScore(
            "eff", "Effizienz", listOf("schlaf", "bett", "auf"), rule = ScoreRule.ShareOfSpan, direction = Direction.LowerIsBetter,
            steps = Steps(listOf(65 to 3, 75 to 2, 85 to 1), otherwise = 0),
        )
        val form = form(score, items = items)

        fun at(slept: Int) = value(form, mapOf("bett" to Answer.Time(22 * 60), "auf" to Answer.Time(6 * 60), "schlaf" to Answer.Time(slept)))
        // Of 480 minutes: 85 % is 408, 75 % 360, 65 % 312.
        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), listOf(408, 407, 360, 359, 312, 311).map(::at))
        assertEquals(0L..300L, Scoring.range(form, score))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a share of a span counts three times, its part and the span's ends`() {
        form(ItemScore("eff", "Effizienz", listOf("a", "b", "c"), rule = ScoreRule.ShareOfSpan, direction = Direction.HigherIsBetter))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a share of a span counts a duration of two times of the day, in that order`() {
        val items = listOf(TimeItem("bett", "Zu Bett"), TimeItem("auf", "Aufgestanden"), TimeItem("schlaf", "Geschlafen", duration = true))
        form(ItemScore("eff", "Effizienz", listOf("bett", "schlaf", "auf"), rule = ScoreRule.ShareOfSpan, direction = Direction.HigherIsBetter), items = items)
    }

    @Test
    fun `a time is typed as hh mm, or in whole hours, from 0 00 to 23 59`() {
        assertEquals(listOf(450, 450, 450, 420, 0, 1439), listOf("7:30", "07:30", " 7:30 ", "7", "0:00", "23:59").map(TimeItem::parse))
        assertEquals(List(7) { null }, listOf("24:00", "7:60", "7:3", "abends", "", "7,30", "7.30").map(TimeItem::parse))
        assertEquals(listOf("07:30", "00:00", "23:59"), listOf(450, 0, 1439).map(TimeItem::format))
    }
}
