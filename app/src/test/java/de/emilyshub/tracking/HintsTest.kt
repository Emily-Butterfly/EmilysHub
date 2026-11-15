package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The hints of Tracking (SPEC.md §17, "Hinweise"), as pure logic, with made-up values only. */
class HintsTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    /** Sunday, 15 November 2026. */
    private val today = LocalDate.parse("2026-11-15")

    private fun day(back: Long) = today.minusDays(back)

    private val symptoms = TrackingSymptoms.START_LIST.mapIndexed { i, name -> Symptom(i + 1L, name, i) }

    private fun symptomId(name: String) = symptoms.single { it.name == name }.id

    private fun evaluate(records: TrackingRecords, done: Map<HintRule, Set<String>> = emptyMap()) = Hints.evaluate(records, today, done)

    private fun hint(records: TrackingRecords, rule: HintRule) = evaluate(records).shown.singleOrNull { it.rule == rule }

    private fun records(
        mornings: List<MorningEntry> = emptyList(),
        evenings: List<EveningEntry> = emptyList(),
        values: List<SymptomValue> = emptyList(),
        medications: List<Medication> = emptyList(),
        doseChanges: List<DoseChange> = emptyList(),
        standTests: List<StandRecord> = emptyList(),
        events: List<TrackingEvent> = emptyList(),
        exertions: List<Exertion> = emptyList(),
        followUps: List<FollowUp> = emptyList(),
        episodes: List<Episode> = emptyList(),
        triggers: List<EpisodeTrigger> = emptyList(),
        garmin: GarminDays = GarminDays(),
    ) = TrackingRecords(berlin, mornings, evenings, symptoms, values, emptyList(), medications, doseChanges, standTests, events, exertions, followUps, episodes, triggers, garmin)

    private fun mornings(count: Int, pulse: (Int) -> Int? = { null }, quality: (Int) -> Int? = { null }) =
        (0 until count).map { MorningEntry(date = day(it.toLong()), restingPulse = pulse(it), sleepQuality = quality(it), recordedOn = day(it.toLong())) }

    private fun evenings(count: Int, fluid: (Int) -> Int? = { null }) =
        (0 until count).map { EveningEntry(date = day(it.toLong()), fluidDecilitres = fluid(it), recordedOn = day(it.toLong())) }

    // The basics.

    @Test
    fun `the basic examinations always, until ticked`() {
        val basics = hint(records(), HintRule.Basics)!!
        assertTrue(basics.question.contains("EKG mit QTc"))
        assertTrue(basics.question.contains("Elektrolyte"))
        assertEquals(listOf("HRS-2015", "NIH-POTS-2021"), basics.sources.map { it.code })
        val ticked = evaluate(records(), mapOf(HintRule.Basics to basics.evidence))
        assertTrue(ticked.shown.none { it.rule == HintRule.Basics })
    }

    // The morning pulse.

    @Test
    fun `the morning pulse above 100 at most of the days, with 14 days at least`() {
        val above = records(mornings(14, pulse = { if (it < 8) 101 else 80 }))
        val hint = hint(above, HintRule.MorningPulse)!!
        assertEquals("14 Morgeneinträge mit Ruhepuls vom 02.11.2026 bis 15.11.2026; eigene Einträge", hint.basis!!.describe())
        assertEquals("Kardiologie", hint.specialty)
        assertTrue(hint.question.contains("Langzeit-EKG"))
        assertNull("half is no majority", hint(records(mornings(14, pulse = { if (it < 7) 101 else 80 })), HintRule.MorningPulse))
        assertNull("100 exactly is not above", hint(records(mornings(14, pulse = { 100 })), HintRule.MorningPulse))
    }

    @Test
    fun `too few mornings say how many are missing instead`() {
        val list = evaluate(records(mornings(12, pulse = { 120 })))
        assertNull(list.shown.firstOrNull { it.rule == HintRule.MorningPulse })
        assertTrue(MissingData("Ruhepuls am Morgen", "noch 2 Tage") in list.missing)
        assertTrue(MissingData("Ruhepuls am Morgen", "noch 1 Tag") in evaluate(records(mornings(13, pulse = { 120 }))).missing)
    }

    @Test
    fun `Garmin's resting pulse, tier B, backs up the hint, never sets one off`() {
        val garmin = GarminDays(restingHeartRate = (0L until 20L).associate { day(it) to 110.0 })
        assertNull("B alone sets off nothing", hint(records(mornings(14, pulse = { 70 }), garmin = garmin), HintRule.MorningPulse))
        assertNull(hint(records(garmin = garmin), HintRule.MorningPulse))
        val backed = hint(records(mornings(14, pulse = { 105 }), garmin = garmin), HintRule.MorningPulse)!!
        assertEquals(listOf("Auch der Ruhepuls von Garmin lag an 20 von 20 Tagen über 100/min (Stufe B, nur als Stütze)."), backed.support)
        assertTrue(backed.basis!!.describe().endsWith("Garmin-Werte der Stufe B nur als Stütze"))
    }

    @Test
    fun `tier C never changes a hint`() {
        val records = records(mornings(14, pulse = { 105 }, quality = { 3 }))
        val withC = records.copy(garmin = GarminDays(deepSleepMinutes = (0L until 20L).associate { day(it) to 5L }))
        assertEquals(evaluate(records), evaluate(withC))
    }

    // Breathlessness and the risk of thrombosis.

    private val risky = Medication(id = 1, name = "Medikament A", start = day(100), thrombosisRisk = true)

    private fun breathlessEvent(id: Long, back: Long, level: Int = 2) =
        TrackingEvent(id = id, kind = EventKind.Heat, at = day(back).atTime(12, 0).atZone(berlin).toInstant(), breathlessness = level)

    @Test
    fun `breathlessness on 3 days and a medication raising the risk of thrombosis, at once and urgent`() {
        val events = listOf(breathlessEvent(1, 0), breathlessEvent(2, 3), breathlessEvent(3, 5))
        val hint = hint(records(events = events, medications = listOf(risky)), HintRule.BreathlessThrombosis)!!
        assertTrue(hint.urgent)
        assertEquals(HintRule.BreathlessThrombosis, evaluate(records(events = events, medications = listOf(risky), mornings = mornings(14, pulse = { 120 }))).shown.first().rule)
        assertTrue(hint.question.contains("Lungenembolie"))
        assertEquals(listOf("ESC-LE-2019"), hint.sources.map { it.code })
        assertNull("2 days are not enough", hint(records(events = events.take(2), medications = listOf(risky)), HintRule.BreathlessThrombosis))
        assertNull("1 is not 2", hint(records(events = events.map { it.copy(breathlessness = 1) }, medications = listOf(risky)), HintRule.BreathlessThrombosis))
        assertNull("without the risk", hint(records(events = events, medications = listOf(risky.copy(thrombosisRisk = false))), HintRule.BreathlessThrombosis))
    }

    @Test
    fun `breathlessness counts from evenings with load or heat, and from Stehtests`() {
        val breath = symptomId("Atemnot")
        val evenings = listOf(
            EveningEntry(date = day(1), heatOrStanding = true, recordedOn = day(1)),
            EveningEntry(date = day(2), loadPhysical = 2, recordedOn = day(2)),
            // Breathless at rest: does not count.
            EveningEntry(date = day(4), recordedOn = day(4)),
        )
        val values = listOf(SymptomValue(day(1), breath, 2), SymptomValue(day(2), breath, 3), SymptomValue(day(4), breath, 3))
        val test = standRecord(1, day(6), rises = listOf(0), breathless = 2)
        assertTrue(hint(records(evenings = evenings, values = values, standTests = listOf(test), medications = listOf(risky)), HintRule.BreathlessThrombosis) != null)
        assertNull(hint(records(evenings = evenings, values = values, medications = listOf(risky)), HintRule.BreathlessThrombosis))
    }

    // Medications.

    @Test
    fun `a worsening within six weeks after a medication's start, to talk over with those treating`() {
        val start = day(20)
        val medication = Medication(id = 2, name = "Medikament B", start = start)
        // 14 evenings before at 1, 21 after at 2.
        val before = (21L..34L).map { SymptomValue(day(it), 1, 1) }
        val after = (0L..20L).map { SymptomValue(day(it), 1, 2) }
        val evenings = (before + after).map { EveningEntry(date = it.date, recordedOn = it.date) }
        val hint = hint(records(evenings = evenings, values = before + after, medications = listOf(medication)), HintRule.Medication)!!
        assertTrue(hint.pattern, hint.pattern.contains("Beginn von Medikament B"))
        assertTrue(hint.question.contains("mit den Behandelnden besprechen"))
        assertTrue(hint.ownValues!!.contains("eigene Arbeitswerte"))
        val slight = (0L..20L).map { SymptomValue(day(it), 1, 1) }
        assertNull("no worse", hint(records(evenings = evenings, values = before + slight, medications = listOf(medication)), HintRule.Medication))
    }

    @Test
    fun `a higher dose counts, a lower one not, too few evenings after say how many are missing`() {
        val medication = Medication(id = 2, name = "Medikament B", start = day(300))
        val before = (11L..24L).map { SymptomValue(day(it), 1, 0) }
        val after = (0L..10L).map { SymptomValue(day(it), 1, 2) }
        val evenings = (before + after).map { EveningEntry(date = it.date, recordedOn = it.date) }
        val higher = DoseChange(1, 2, day(10), "5 mg", "10 mg")
        assertTrue(hint(records(evenings = evenings, values = before + after, medications = listOf(medication), doseChanges = listOf(higher)), HintRule.Medication)!!.pattern.contains("5 mg → 10 mg"))
        assertNull(hint(records(evenings = evenings, values = before + after, medications = listOf(medication), doseChanges = listOf(higher.copy(from = "10 mg", to = "5 mg"))), HintRule.Medication))
        val early = (0L..3L).map { SymptomValue(day(it), 1, 2) }
        val list = evaluate(records(evenings = (before + early).map { EveningEntry(date = it.date, recordedOn = it.date) }, values = before + early, medications = listOf(medication), doseChanges = listOf(higher.copy(date = day(3)))))
        assertTrue(list.missing.toString(), MissingData("Medikamente", "noch 3 Tage") in list.missing)
        assertTrue(Hints.isIncrease("1,5 mg", "2 mg"))
        assertFalse(Hints.isIncrease("2", "1,5"))
        assertTrue("unclear counts", Hints.isIncrease("eine Tablette", "zwei Tabletten"))
    }

    // Stehtests.

    private fun standRecord(
        id: Long,
        on: LocalDate,
        rises: List<Int>,
        lying: Int = 70,
        dizzy: Int = 1,
        breathless: Int? = null,
        dropEarly: Int? = null,
        dropLate: Int? = null,
        variant: StandVariant = StandVariant.Standing,
        filled: Boolean = false,
    ): StandRecord {
        val at = on.atTime(8, 0).atZone(berlin).toInstant()
        val test = StandTest(id = id, variant = variant, startedAt = at, standingAt = at.plusSeconds(600), lyingPulse = lying, finished = true)
        val points = variant.minutes.mapIndexed { i, minute -> StandPoint(id, minute, pulse = lying + rises.getOrElse(i) { 0 }, dizziness = dizzy, breathlessness = breathless) }
        val result = StandTestEvaluation.evaluate(test, points, emptyList())
            .let { it.copy(systolicDropEarly = dropEarly, systolicDropLate = dropLate, lyingFilled = filled) }
        return StandRecord(test, points, result)
    }

    private val pots = listOf(30, 35, 40, 40, 40)

    @Test
    fun `the POTS pattern in 2 of 3 Stehtests on different days`() {
        val tests = listOf(standRecord(1, day(1), pots), standRecord(2, day(2), pots), standRecord(3, day(3), listOf(5)))
        val hint = hint(records(standTests = tests), HintRule.StandRise)!!
        assertTrue(hint.pattern.contains("passt zu einem POTS-Muster"))
        assertEquals("Kardiologie oder autonome Ambulanz", hint.specialty)
        assertEquals(listOf("NIH-POTS-2021", "CMAJ-POTS-2022", "DGN-SYNK-2020", "LEE-2020"), hint.sources.map { it.code })
        assertEquals(setOf("test:1", "test:2"), hint.evidence)
        assertTrue(hint.basis!!.describe().endsWith("; eigene Einträge"))
    }

    @Test
    fun `no POTS pattern with one rise only, a drop, a low lying pulse or no complaints`() {
        fun with(vararg tests: StandRecord) = hint(records(standTests = tests.toList() + standRecord(9, day(9), listOf(0))), HintRule.StandRise)
        assertNull(with(standRecord(1, day(1), listOf(30)), standRecord(2, day(2), listOf(30))))
        assertNull(with(standRecord(1, day(1), pots, dropEarly = 20), standRecord(2, day(2), pots, dropEarly = 20)))
        assertNull(with(standRecord(1, day(1), pots, lying = 59), standRecord(2, day(2), pots, lying = 59)))
        assertNull(with(standRecord(1, day(1), pots, dizzy = 0), standRecord(2, day(2), pots, dizzy = 0)))
        assertTrue(with(standRecord(1, day(1), pots, lying = 60), standRecord(2, day(2), pots, lying = 60)) != null)
    }

    @Test
    fun `leaning counts by the mean of the last three minutes, above 30 over lying or above 120`() {
        val leaning = { id: Long, back: Long, rise: Int, lying: Int -> standRecord(id, day(back), List(10) { rise }, lying = lying, variant = StandVariant.Leaning) }
        assertTrue(hint(records(standTests = listOf(leaning(1, 1, 31, 70), leaning(2, 2, 31, 70), leaning(3, 3, 0, 70))), HintRule.StandRise) != null)
        assertNull("30 exactly is not above", hint(records(standTests = listOf(leaning(1, 1, 30, 70), leaning(2, 2, 30, 70), leaning(3, 3, 0, 70))), HintRule.StandRise))
        assertTrue(hint(records(standTests = listOf(leaning(1, 1, 25, 96), leaning(2, 2, 25, 96), leaning(3, 3, 0, 70))), HintRule.StandRise) != null)
    }

    @Test
    fun `Stehtests need 3 on different days, with tier A from Garmin's curve counted as own`() {
        val twoDays = listOf(standRecord(1, day(1), pots), standRecord(2, day(1), pots), standRecord(3, day(2), pots))
        val list = evaluate(records(standTests = twoDays))
        assertTrue(list.shown.none { it.rule == HintRule.StandRise })
        assertTrue(MissingData("Stehtest", "noch 1 Stehtest") in list.missing)
        assertTrue(MissingData("Stehtest", "noch 3 Stehtests") in evaluate(records()).missing)
        val filled = listOf(standRecord(1, day(1), pots, filled = true), standRecord(2, day(2), pots), standRecord(3, day(3), pots))
        val hint = hint(records(standTests = filled), HintRule.StandRise)!!
        assertTrue(hint.basis!!.describe(), hint.basis!!.describe().contains("Garmin-Werte der Stufe A, die wie eigene Messungen zählen"))
    }

    @Test
    fun `a drop within 3 minutes and one only after, each in 2 tests`() {
        val early = listOf(standRecord(1, day(1), listOf(0), dropEarly = 20), standRecord(2, day(2), listOf(0), dropEarly = 25), standRecord(3, day(3), listOf(0), dropEarly = 5))
        val hint = hint(records(standTests = early), HintRule.StandDropEarly)!!
        assertTrue(hint.pattern.contains("orthostatischen Hypotonie"))
        assertTrue(hint.question.contains("mit den Behandelnden besprechen"))
        assertNull(hint(records(standTests = early), HintRule.StandDropLate))
        assertNull("19 is not 20", hint(records(standTests = early.map { it.copy(result = it.result.copy(systolicDropEarly = 19)) }), HintRule.StandDropEarly))
        val late = listOf(standRecord(1, day(1), listOf(0), dropLate = 20), standRecord(2, day(2), listOf(0), dropLate = 20), standRecord(3, day(3), listOf(0)))
        assertTrue(hint(records(standTests = late), HintRule.StandDropLate)!!.question.contains("Kipptisch"))
        assertNull(hint(records(standTests = late), HintRule.StandDropEarly))
    }

    @Test
    fun `complaints standing without anything measured`() {
        val calm = listOf(standRecord(1, day(1), listOf(10), dizzy = 2), standRecord(2, day(2), listOf(10), dizzy = 1), standRecord(3, day(3), listOf(10), dizzy = 0))
        assertTrue(hint(records(standTests = calm), HintRule.StandComplaints)!!.pattern.contains("morgens zu wiederholen"))
        assertNull(hint(records(standTests = calm.map { it.copy(points = it.points.map { p -> p.copy(dizziness = 0) }) }), HintRule.StandComplaints))
    }

    // PEM.

    private fun strong(id: Long, back: Long, origin: ExertionOrigin = ExertionOrigin.Manual) =
        Exertion(id = id, date = day(back), time = LocalTime.of(10, 0), kind = ExertionKind.Physical, strength = 2, origin = origin)

    @Test
    fun `a worsening the day or two after 2 of 3 strong exertions`() {
        val exertions = listOf(strong(1, 20), strong(2, 10), strong(3, 5))
        val answers = listOf(FollowUp(day(19), 1, FollowUpAnswer.Yes), FollowUp(day(8), 2, FollowUpAnswer.Yes), FollowUp(day(4), 3, FollowUpAnswer.No))
        val hint = hint(records(exertions = exertions, followUps = answers), HintRule.Pem)!!
        assertTrue(hint.pattern.contains("passt zu einer Verschlechterung nach Belastung (PEM)"))
        assertTrue(hint.question.contains("nicht austesten"))
        assertEquals(listOf("IOM-2015", "NICE-NG206"), hint.sources.map { it.code })
        assertEquals(setOf("exertion:1", "exertion:2"), hint.evidence)
        assertNull("one of three", hint(records(exertions = exertions, followUps = answers.take(1) + answers.drop(2)), HintRule.Pem))
        assertNull("on the third day does not count", hint(records(exertions = exertions, followUps = listOf(FollowUp(day(17), 1, FollowUpAnswer.Yes)) + answers.drop(1)), HintRule.Pem))
    }

    @Test
    fun `too few strong exertions with the days after say how many are missing`() {
        val list = evaluate(records(exertions = listOf(strong(1, 10), strong(2, 0)), followUps = listOf(FollowUp(day(9), 1, FollowUpAnswer.Yes))))
        assertTrue(list.missing.toString(), MissingData("Verschlechterung nach Belastung", "noch 2 Anstrengungen") in list.missing)
        val withEvenings = evaluate(records(exertions = listOf(strong(1, 10)), evenings = listOf(9L, 8L).map { EveningEntry(date = day(it), recordedOn = day(it)) }))
        assertTrue(MissingData("Verschlechterung nach Belastung", "noch 2 Anstrengungen") in withEvenings.missing)
    }

    @Test
    fun `2 episodes beginning 12 to 72 hours after a sure or likely trigger`() {
        val exertions = listOf(strong(1, 20), strong(2, 10))
        // 12 and 72 hours after, both counting.
        val episodes = listOf(
            Episode(id = 1, kind = EpisodeKind.Pem, startDate = day(20), startTime = LocalTime.of(22, 0)),
            Episode(id = 2, kind = EpisodeKind.Crash, startDate = day(7), startTime = LocalTime.of(10, 0)),
        )
        val triggers = listOf(EpisodeTrigger(1, 1, Certainty.Sure), EpisodeTrigger(2, 2, Certainty.Likely))
        assertEquals(setOf("episode:1", "episode:2"), hint(records(exertions = exertions, episodes = episodes, triggers = triggers), HintRule.Pem)!!.evidence)
        assertNull("unclear does not count", hint(records(exertions = exertions, episodes = episodes, triggers = triggers.map { it.copy(certainty = Certainty.Unclear) }), HintRule.Pem))
        val tooSoon = episodes.first().copy(startTime = LocalTime.of(21, 0))
        assertNull("11 hours", hint(records(exertions = exertions, episodes = listOf(tooSoon, episodes[1]), triggers = triggers), HintRule.Pem))
    }

    @Test
    fun `steps confirmed are tier A, Garmin's pulse after is tier B support`() {
        val exertions = listOf(strong(1, 20, ExertionOrigin.Steps), strong(2, 10), strong(3, 5))
        val answers = listOf(FollowUp(day(19), 1, FollowUpAnswer.Yes), FollowUp(day(9), 2, FollowUpAnswer.Yes), FollowUp(day(4), 3, FollowUpAnswer.Yes))
        val pulse = mapOf(day(20) to 60.0, day(19) to 70.0, day(10) to 60.0, day(9) to 68.0)
        val hint = hint(records(exertions = exertions, followUps = answers, garmin = GarminDays(restingHeartRate = pulse)), HintRule.Pem)!!
        assertTrue(hint.basis!!.describe().contains("Stufe A"))
        assertEquals(listOf("Auch der Ruhepuls von Garmin war an den Tagen danach höher (im Mittel 69 statt 60/min, Stufe B, nur als Stütze)."), hint.support)
    }

    // Skin.

    private fun skinEvent(id: Long, back: Long, areas: Set<SkinArea> = setOf(SkinArea.Arms), minutes: Int? = 10, pattern: SkinPattern? = SkinPattern.Even, painful: Boolean? = false, fades: Boolean? = false) =
        TrackingEvent(
            id = id,
            kind = EventKind.Shower,
            at = day(back).atTime(9, 0).atZone(berlin).toInstant(),
            skinAreas = areas,
            skinFadeMinutes = minutes,
            skinPattern = pattern,
            skinPainful = painful,
            skinFadesLying = fades,
        )

    private val plainEvents = (10L..12L).map { skinEvent(it, it, areas = emptySet()) }

    @Test
    fun `skin lasting over 30 minutes, net-like or painful in 2 events`() {
        val events = plainEvents + listOf(skinEvent(1, 1, minutes = 31), skinEvent(2, 2, pattern = SkinPattern.Reticular))
        val hint = hint(records(events = events), HintRule.SkinPersistent)!!
        assertEquals("Dermatologie oder Rheumatologie", hint.specialty)
        assertTrue(hint.question.contains("Antiphospholipid-Antikörper"))
        assertEquals(listOf("SAJJAN-2015"), hint.sources.map { it.code })
        assertNull("30 exactly", hint(records(events = plainEvents + listOf(skinEvent(1, 1, minutes = 30), skinEvent(2, 2, minutes = 30))), HintRule.SkinPersistent))
        assertTrue(hint(records(events = plainEvents + listOf(skinEvent(1, 1, painful = true), skinEvent(2, 2, painful = true))), HintRule.SkinPersistent) != null)
    }

    @Test
    fun `fingers or toes, and legs fading quickly lying down`() {
        val fingers = plainEvents + listOf(skinEvent(1, 1, areas = setOf(SkinArea.FingersToes)), skinEvent(2, 2, areas = setOf(SkinArea.FingersToes, SkinArea.Arms)))
        assertTrue(hint(records(events = fingers), HintRule.SkinFingersToes)!!.question.contains("Raynaud"))
        val legs = plainEvents + listOf(skinEvent(1, 1, areas = setOf(SkinArea.Legs), fades = true), skinEvent(2, 2, areas = setOf(SkinArea.Legs), fades = true))
        assertTrue(hint(records(events = legs), HintRule.SkinPooling)!!.pattern.contains("stützt die Stehtests"))
        assertNull(hint(records(events = legs.map { it.copy(skinFadesLying = false) }), HintRule.SkinPooling))
    }

    @Test
    fun `skin needs 5 events`() {
        val list = evaluate(records(events = listOf(skinEvent(1, 1, minutes = 60), skinEvent(2, 2, minutes = 60))))
        assertTrue(list.shown.none { it.rule == HintRule.SkinPersistent })
        assertTrue(MissingData("Haut", "noch 3 Ereignisse") in list.missing)
    }

    // Sleep, mood, fluid.

    @Test
    fun `poor sleep at most nights, backed by Garmin's shorter nights`() {
        val nights = mornings(14, quality = { if (it < 8) 2 else 0 })
        val garmin = GarminDays(sleepMinutes = (0L until 14L).associate { day(it) to if (it < 8) 300L else 480L })
        val hint = hint(records(nights, garmin = garmin), HintRule.Sleep)!!
        assertTrue(hint.question.contains("Schnarchen oder Atempausen"))
        assertEquals(listOf("DEGAM-MUEDIGKEIT"), hint.sources.map { it.code })
        assertEquals(listOf("Auch die Schlafdauer laut Garmin war in diesen Nächten kürzer (im Mittel 5,0 statt 8,0 Stunden, Stufe B, nur als Stütze)."), hint.support)
        assertNull(hint(records(mornings(14, quality = { if (it < 7) 3 else 1 })), HintRule.Sleep))
        assertTrue(MissingData("Schlaf", "noch 4 Tage") in evaluate(records(mornings(10, quality = { 3 }))).missing)
        assertNull("Garmin alone", hint(records(garmin = garmin), HintRule.Sleep))
    }

    @Test
    fun `low mood or anxiety at most days`() {
        val low = symptomId("Stimmung gedrückt")
        val anxious = symptomId("Angst/innere Unruhe")
        val values = (0L until 14L).map { SymptomValue(day(it), if (it % 2 == 0L) low else anxious, if (it < 8) 2 else 1) }
        val hint = hint(records(values = values), HintRule.Mood)!!
        assertTrue(hint.pattern.contains("parallel zur körperlichen Abklärung"))
        assertNull(hint(records(values = values.map { it.copy(level = if (it.date.isAfter(day(7))) 2 else 1) }), HintRule.Mood))
        assertTrue(MissingData("Stimmung", "noch 14 Tage") in evaluate(records()).missing)
    }

    @Test
    fun `less than 1,5 l drunk at most days, an own working value`() {
        val hint = hint(records(evenings = evenings(14, fluid = { if (it < 8) 14 else 15 })), HintRule.Fluid)!!
        assertEquals("1,5 l ist ein eigener Arbeitswert, keine Leitlinie.", hint.ownValues)
        assertNull("1,5 l exactly", hint(records(evenings = evenings(14, fluid = { 15 })), HintRule.Fluid))
        assertTrue(MissingData("Trinkmenge", "noch 14 Tage") in evaluate(records(evenings = evenings(14))).missing)
    }

    // Done, order, words.

    @Test
    fun `marked done, a hint stays hidden until something is added to what it rests on`() {
        val first = records(mornings(14, pulse = { if (it < 8) 105 else 80 }))
        val hint = hint(first, HintRule.MorningPulse)!!
        val done = mapOf(HintRule.MorningPulse to hint.evidence)
        assertNull(evaluate(first, done).shown.firstOrNull { it.rule == HintRule.MorningPulse })
        // Another morning above 100: back again.
        val more = records(mornings(14, pulse = { if (it < 9) 105 else 80 }))
        assertTrue(evaluate(more, done).shown.any { it.rule == HintRule.MorningPulse })
    }

    @Test
    fun `the urgent first, then the newest, the basics last`() {
        val events = listOf(breathlessEvent(1, 20), breathlessEvent(2, 21), breathlessEvent(3, 22))
        val mornings = mornings(14, pulse = { if (it in 3..11) 105 else 80 }, quality = { 3 })
        val list = evaluate(records(mornings = mornings, events = events, medications = listOf(risky)))
        assertEquals(listOf(HintRule.BreathlessThrombosis, HintRule.Sleep, HintRule.MorningPulse, HintRule.Basics), list.shown.map { it.rule })
    }

    private val forbidden = listOf(
        Regex("""\b[Dd]u hast\b"""), Regex("""\bhast du\b"""), Regex("""\bSie haben\b"""), Regex("""\bDiagnose\b"""),
        Regex("""absetzen|abzusetzen|einnehmen|weglassen|\bnimm\b|[Nn]ehmen Sie|erhöhen Sie|reduzieren|Dosis (erhöhen|senken|ändern)"""),
    )

    @Test
    fun `no hint says Du hast, diagnoses or tells what to do with medications other than talking it over`() {
        val all = allHints()
        assertEquals("every rule is checked", HintRule.entries.toSet(), all.map { it.rule }.toSet())
        all.forEach { hint ->
            hint.texts.forEach { text -> forbidden.forEach { assertFalse("${hint.rule}: $text", it.containsMatchIn(text)) } }
            assertTrue("${hint.rule}: ${hint.pattern}", Regex("passt zu|spricht (für|dafür)|sprechen für").containsMatchIn(hint.pattern) || hint.pattern.contains("passt zeitlich"))
            if (hint.texts.any { it.contains("Medikament") }) assertTrue("${hint.rule}", hint.texts.any { it.contains("mit den Behandelnden besprechen") })
            assertTrue("${hint.rule}: a source or an own working value", hint.sources.isNotEmpty() || hint.ownValues != null)
        }
    }

    /** One hint of each rule, from records made up to set them all off. */
    private fun allHints(): List<Hint> {
        val values = (0L until 14L).map { SymptomValue(day(it), symptomId("Stimmung gedrückt"), 3) } +
            (21L..34L).map { SymptomValue(day(it), symptomId("Erschöpfung"), 0) } + (0L..20L).map { SymptomValue(day(it), symptomId("Erschöpfung"), 3) }
        val evenings = (0L..34L).map { EveningEntry(date = day(it), fluidDecilitres = 10, recordedOn = day(it)) }
        val tests = listOf(standRecord(1, day(1), pots), standRecord(2, day(2), pots)) +
            listOf(standRecord(3, day(3), listOf(0), dropEarly = 30), standRecord(4, day(4), listOf(0), dropEarly = 30)) +
            listOf(standRecord(5, day(5), listOf(0), dropLate = 30), standRecord(6, day(6), listOf(0), dropLate = 30)) +
            listOf(standRecord(7, day(7), listOf(5), dizzy = 2), standRecord(8, day(8), listOf(5), dizzy = 2))
        val events = listOf(breathlessEvent(1, 0), breathlessEvent(2, 3), breathlessEvent(3, 5)) + listOf(
            skinEvent(4, 1, minutes = 60), skinEvent(5, 2, minutes = 60),
            skinEvent(6, 3, areas = setOf(SkinArea.FingersToes)), skinEvent(7, 4, areas = setOf(SkinArea.FingersToes)),
            skinEvent(8, 5, areas = setOf(SkinArea.Legs), fades = true), skinEvent(9, 6, areas = setOf(SkinArea.Legs), fades = true),
        )
        val exertions = listOf(strong(11, 20), strong(12, 10), strong(13, 5))
        val answers = listOf(FollowUp(day(19), 11, FollowUpAnswer.Yes), FollowUp(day(9), 12, FollowUpAnswer.Yes), FollowUp(day(4), 13, FollowUpAnswer.Yes))
        val list = evaluate(
            records(
                mornings = mornings(14, pulse = { 110 }, quality = { 3 }),
                evenings = evenings,
                values = values,
                medications = listOf(risky, Medication(id = 2, name = "Medikament B", start = day(20))),
                standTests = tests,
                events = events,
                exertions = exertions,
                followUps = answers,
            ),
        )
        return list.shown
    }

    @Test
    fun `the full citation of each source of a hint`() {
        allHints().flatMap { it.sources }.forEach { assertTrue(it.code, it.citation.length > it.code.length) }
        assertEquals(
            "Sajjan VV et al.: Livedo reticularis: A review of the literature. Indian Dermatol Online J 2015; 6: 315–321. doi:10.4103/2229-5178.164493",
            Source.Sajjan2015.citation,
        )
    }

    @Test
    fun `instants count by the wall clock`() {
        // 00:30 in Berlin on the 15th, 23:30 UTC on the 14th.
        val event = breathlessEvent(1, 0).copy(at = Instant.parse("2026-11-14T23:30:00Z"))
        assertEquals(today, records().dayOf(event.at))
    }
}
