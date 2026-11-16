package de.emilyshub.tracking

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The rules of the hints (SPEC.md §17, "Hinweise"), each one a hint at most. */
enum class HintRule {
    Basics,
    BreathlessThrombosis,
    MorningPulse,
    Medication,
    StandRise,
    StandDropEarly,
    StandDropLate,
    StandComplaints,
    Pem,
    SkinPersistent,
    SkinFingersToes,
    SkinPooling,
    Sleep,
    Mood,
    Fluid,
}

/**
 * What a hint rests on: [count] of [what] from [from] to [to]; [tiers] the tiers of Garmin's values among them, none if
 * only one's own entries; [supportedByB] if values of tier B back it up.
 */
data class DataBasis(val count: Int, val what: String, val from: LocalDate?, val to: LocalDate?, val tiers: Set<Tier> = emptySet(), val supportedByB: Boolean = false) {
    /** As the hint says it: "5 Morgeneinträge vom 02.11.2026 bis 15.11.2026; eigene Einträge". */
    fun describe(): String = buildString {
        append("$count $what")
        if (from != null && to != null) append(if (from == to) " am ${DATE.format(from)}" else " vom ${DATE.format(from)} bis ${DATE.format(to)}")
        append(if (Tier.A in tiers) "; eigene Einträge und Garmin-Werte der Stufe A, die wie eigene Messungen zählen" else "; eigene Einträge")
        if (supportedByB) append("; Garmin-Werte der Stufe B nur als Stütze")
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
    }
}

/**
 * A hint (SPEC.md §17): its [rule] and [title]; the [pattern] it saw, said with "passt zu" or "spricht für", never as a
 * diagnosis; its [basis]; the next step for the doctor's appointment, the [specialty] and the [question]; values of tier B
 * that back it up ([support]); its [sources], and the [ownValues] among its thresholds, the app's own working values. The
 * [evidence] is what it rests on, by key: marked done, it stays hidden until something is added there. [latest] is the
 * day of the newest of that, [urgent] if it is to be cleared soon.
 */
data class Hint(
    val rule: HintRule,
    val title: String,
    val pattern: String,
    val basis: DataBasis?,
    val specialty: String,
    val question: String,
    val sources: List<Source>,
    val evidence: Set<String>,
    val latest: LocalDate,
    val support: List<String> = emptyList(),
    val ownValues: String? = null,
    val urgent: Boolean = false,
) {
    /** All it says, for checking its words. */
    val texts: List<String> get() = listOfNotNull(title, pattern, basis?.describe(), specialty, question, ownValues) + support
}

/** A rule still short of data: "[title]: [missing]", as "noch 2 Stehtests". */
data class MissingData(val title: String, val missing: String)

/** The hints to show, the newest and most important first, and the rules still short of data. */
data class HintList(val shown: List<Hint>, val missing: List<MissingData>)

/**
 * The thresholds of the hints (SPEC.md §17, "Hinweise" and "Quellen der Schwellen"), in one place with their sources;
 * those marked "eigene Festlegung" or "eigener Arbeitswert" are the app's own, no guideline's.
 */
object HintRules {
    /** How many days back the rules on days look: four weeks, eigene Festlegung. */
    const val DAY_WINDOW = 28L

    /** How many days back the rules on Stehtests, events, exertions, episodes and medications look: twelve weeks, eigene Festlegung. */
    const val COUNT_WINDOW = 84L

    /** Mindestdaten, eigene Festlegung: days with entries. */
    const val MIN_DAYS = 14

    /** Mindestdaten, eigene Festlegung: Stehtests on different days. */
    const val MIN_STAND_TESTS = 3

    /** Mindestdaten, eigene Festlegung: events. */
    const val MIN_EVENTS = 5

    /** Mindestdaten, eigene Festlegung: exertions of strength 2–3 with the days after them. */
    const val MIN_EXERTIONS = 3

    /** A level of a symptom, of sleep's quality or of breathlessness from which it counts, on the scale 0–3: eigene Festlegung. */
    const val LEVEL_HIGH = WeekRules.LEVEL_HIGH

    /** Days with breathlessness under load or heat needed, with a medication raising the risk of thrombosis. [ESC-LE-2019] */
    const val BREATHLESS_DAYS = 3

    /** A morning resting pulse above this, per minute, at most of the days. [HRS-2015] */
    const val PULSE_HIGH = WeekRules.PULSE_HIGH

    /** How long after a medication's start or a higher dose a worsening counts: six weeks, eigener Arbeitswert. */
    const val MEDICATION_DAYS = 42L

    /** The days before a medication's start or change its mean is taken over, eigener Arbeitswert. */
    const val MEDICATION_BEFORE_DAYS = 14L

    /** The evenings needed before and after, eigener Arbeitswert. */
    const val MEDICATION_EVENINGS = 7

    /** By how much the mean of the symptoms must rise after, on the scale 0–3, eigener Arbeitswert. */
    const val MEDICATION_WORSE = 0.5

    /** How many Stehtests must show a pattern. [NIH-POTS-2021] [CMAJ-POTS-2022] */
    const val STAND_POSITIVE = 2

    /** How many measurements of a Stehtest must rise by [StandTestRules.RISE]. [NIH-POTS-2021] [CMAJ-POTS-2022] */
    const val RISES_NEEDED = WeekRules.RISES_NEEDED

    /** The least lying pulse for the POTS pattern, per minute. [NIH-POTS-2021] */
    const val LYING_PULSE_MIN = 60

    /** A mean of the last three minutes of the lean test above this, per minute, counts too. [LEE-2020] */
    const val LEAN_MEAN_HIGH = 120

    /** How many exertions of strength 2–3 of the last [PEM_OF] must have been followed by a worsening. [IOM-2015] */
    const val PEM_WORSENED = 2

    /** Of how many exertions, the last ones with the days after them. [IOM-2015] */
    const val PEM_OF = 3

    /** The days after an exertion a worsening counts on: the next and the one after. [IOM-2015] [NICE-NG206] */
    val PEM_DAYS_AFTER = 1L..2L

    /** The hours from a trigger to an episode's start that count. [IOM-2015] [NICE-NG206] */
    val PEM_GAP_HOURS = 12L..72L

    /** How many episodes with such a trigger, sure or likely, are needed. [IOM-2015] */
    const val PEM_EPISODES = 2

    /** A change of the skin lasting longer than this, in minutes, counts: eigener Arbeitswert. [SAJJAN-2015] */
    const val SKIN_MINUTES = 30

    /** How many events must show a change of the skin, eigene Festlegung. */
    const val SKIN_EVENTS = 2

    /** Drunk less than this, in tenths of a litre: 1.5 l, eigener Arbeitswert, no guideline's. */
    const val FLUID_LOW_DECILITRES = WeekRules.FLUID_LOW_DECILITRES

    /** "Atemnot" of the start list, by its name: renamed, the rule no longer sees it. */
    const val BREATHLESSNESS = "Atemnot"

    /** "Stimmung gedrückt" of the start list, by its name. */
    const val LOW_MOOD = "Stimmung gedrückt"

    /** "Angst/innere Unruhe" of the start list, by its name. */
    const val ANXIETY = "Angst/innere Unruhe"
}

/** The hints (SPEC.md §17), pure logic: from what was recorded up to [today], those not marked done. */
object Hints {

    /**
     * The hints of [records] on [today], each one shown unless [done] holds all of its evidence: the urgent first,
     * then the newest, the basics last; and the rules still short of data, one line each.
     */
    fun evaluate(records: TrackingRecords, today: LocalDate, done: Map<HintRule, Set<String>> = emptyMap()): HintList {
        val context = Context(records, today)
        val missing = mutableListOf<MissingData>()
        val hints = listOfNotNull(
            basics(today),
            breathless(context),
            morningPulse(context, missing),
            medication(context, missing),
        ) + stand(context, missing) + listOfNotNull(pem(context, missing)) + skin(context, missing) +
            listOfNotNull(sleep(context, missing), mood(context, missing), fluid(context, missing))
        val shown = hints
            .filter { hint -> done[hint.rule]?.containsAll(hint.evidence) != true }
            .sortedWith(compareByDescending<Hint> { it.urgent }.thenBy { it.rule == HintRule.Basics }.thenByDescending { it.latest })
        return HintList(shown, missing.distinct())
    }

    /** What every rule looks at: the [records], [today], and the days each kind of rule looks back to. */
    private class Context(val records: TrackingRecords, val today: LocalDate) {
        val dayFrom: LocalDate = today.minusDays(HintRules.DAY_WINDOW - 1)
        val countFrom: LocalDate = today.minusDays(HintRules.COUNT_WINDOW - 1)

        fun inDays(day: LocalDate) = !day.isBefore(dayFrom) && !day.isAfter(today)
        fun inCount(day: LocalDate) = !day.isBefore(countFrom) && !day.isAfter(today)

        fun symptomIds(vararg names: String): Set<Long> = records.symptoms.filter { it.name in names }.map { it.id }.toSet()

        val standTests: List<StandRecord> by lazy { records.standTests.filter { it.stood && inCount(it.dayIn(records.zone)) } }
        val standDays: Int by lazy { standTests.map { it.dayIn(records.zone) }.distinct().size }
        val events: List<TrackingEvent> by lazy { records.events.filter { inCount(records.dayOf(it.at)) } }
    }

    /** "noch 1 Stehtest", "noch 2 Stehtests". */
    private fun noch(count: Int, one: String, many: String) = "noch $count ${if (count == 1) one else many}"

    /** "noch 1 Tag", "noch 2 Tage". */
    private fun days(count: Int) = noch(count, "Tag", "Tage")

    /** Whether [part] is the greater part of [whole]: more than half. */
    private fun majority(part: Int, whole: Int) = part * 2 > whole

    /** Tenths of a litre as litres, as Germans write them: 1,5. */
    private fun litres(decilitres: Int) = number(decilitres / 10.0)

    /** [value] with one place, as Germans write it: 1,5. */
    private fun number(value: Double) = String.format(Locale.GERMANY, "%.1f", value)

    /** Immer: the basic examinations. [HRS-2015] [NIH-POTS-2021] */
    private fun basics(today: LocalDate) = Hint(
        rule = HintRule.Basics,
        title = "Basisuntersuchungen",
        pattern = "Beschwerden wie Herzrasen, Schwindel oder Erschöpfung sprechen für eine Grundabklärung, bevor es weitergeht.",
        basis = null,
        specialty = "Hausarztpraxis",
        question = "Sind ein EKG mit QTc-Zeit und die Elektrolyte schon bestimmt?",
        sources = listOf(Source.Hrs2015, Source.NihPots2021),
        evidence = setOf("basics"),
        latest = today,
    )

    /** Atemnot bei Belastung oder Wärme an mindestens 3 Tagen und ein Medikament mit Thromboserisiko. [ESC-LE-2019] Without
     * Mindestdaten: it is to be cleared soon, the three days are its own threshold. */
    private fun breathless(context: Context): Hint? {
        val records = context.records
        val ids = context.symptomIds(HintRules.BREATHLESSNESS)
        val fromEvents = records.events.filter { (it.breathlessness ?: 0) >= HintRules.LEVEL_HIGH }.map { records.dayOf(it.at) }
        val fromTests = records.standTests.filter { test -> test.points.any { (it.breathlessness ?: 0) >= HintRules.LEVEL_HIGH } }.map { it.dayIn(records.zone) }
        val fromEvenings = records.evenings.filter { evening ->
            val loaded = evening.heatOrStanding == true || listOfNotNull(evening.loadPhysical, evening.loadCognitive, evening.loadEmotional, evening.loadSocial).any { it >= HintRules.LEVEL_HIGH }
            loaded && records.levelsByDay[evening.date].orEmpty().any { (id, level) -> id in ids && level >= HintRules.LEVEL_HIGH }
        }.map { it.date }
        val days = (fromEvents + fromTests + fromEvenings).filter(context::inDays).distinct().sorted()
        if (days.size < HintRules.BREATHLESS_DAYS) return null
        val risky = records.medications.filter { medication -> medication.thrombosisRisk && (days.any(medication::isActiveOn) || medication.isActiveOn(context.today)) }
        if (risky.isEmpty()) return null
        return Hint(
            rule = HintRule.BreathlessThrombosis,
            title = "Atemnot und Thromboserisiko",
            pattern = "Atemnot bei Belastung oder Wärme an ${days.size} Tagen, dazu ein Medikament, das laut Beipackzettel das Thromboserisiko " +
                "erhöht (${risky.joinToString { it.name }}) – spricht dafür, das zeitnah ärztlich klären zu lassen.",
            basis = DataBasis(days.size, "Tage mit Atemnot 2 oder mehr bei Belastung oder Wärme", days.first(), days.last()),
            specialty = "zeitnah ärztlich, bei plötzlicher oder rasch zunehmender Atemnot sofort 112",
            question = "Wie wahrscheinlich ist eine Lungenembolie, und ist eine Abklärung nötig? Am Medikament nichts selbst ändern, sondern mit den Behandelnden besprechen.",
            sources = listOf(Source.EscPulmonaryEmbolism2019),
            evidence = days.map { "day:$it" }.toSet() + risky.map { "medication:${it.id}" },
            latest = days.last(),
            urgent = true,
        )
    }

    /** Ruhepuls am Morgen an der Mehrzahl der Tage über 100/min. [HRS-2015] Garmin's resting pulse, tier B, only backs it up. */
    private fun morningPulse(context: Context, missing: MutableList<MissingData>): Hint? {
        val pulses = context.records.mornings.filter { context.inDays(it.date) && it.restingPulse != null }.sortedBy { it.date }
        if (pulses.size < HintRules.MIN_DAYS) {
            missing += MissingData("Ruhepuls am Morgen", days(HintRules.MIN_DAYS - pulses.size))
            return null
        }
        val high = pulses.filter { it.restingPulse!! > HintRules.PULSE_HIGH }
        if (!majority(high.size, pulses.size)) return null
        val garmin = context.records.garmin.restingHeartRate.filterKeys(context::inDays)
        val garminHigh = garmin.count { it.value > HintRules.PULSE_HIGH }
        val support = if (garminHigh > 0) listOf("Auch der Ruhepuls von Garmin lag an $garminHigh von ${garmin.size} Tagen über ${HintRules.PULSE_HIGH}/min (Stufe B, nur als Stütze).") else emptyList()
        return Hint(
            rule = HintRule.MorningPulse,
            title = "Ruhepuls am Morgen",
            pattern = "Der Ruhepuls am Morgen lag an ${high.size} von ${pulses.size} Tagen über ${HintRules.PULSE_HIGH}/min – passt zu einem dauerhaft erhöhten Ruhepuls, " +
                "wie er etwa bei einer inadäquaten Sinustachykardie vorkommt.",
            basis = DataBasis(pulses.size, "Morgeneinträge mit Ruhepuls", pulses.first().date, pulses.last().date, supportedByB = support.isNotEmpty()),
            specialty = "Kardiologie",
            question = "Ist ein Langzeit-EKG sinnvoll, um eine inadäquate Sinustachykardie abzuklären?",
            sources = listOf(Source.Hrs2015),
            evidence = high.map { "day:${it.date}" }.toSet(),
            latest = high.last().date,
            support = support,
        )
    }

    /** Whether the dose changed [from] → [to] is higher: by the first numbers in each, and when they cannot tell, as if it were. */
    fun isIncrease(from: String, to: String): Boolean {
        val before = firstNumber(from) ?: return true
        val after = firstNumber(to) ?: return true
        return after > before
    }

    private fun firstNumber(text: String): Double? = Regex("""\d+(?:[.,]\d+)?""").find(text)?.value?.replace(',', '.')?.toDoubleOrNull()

    /** Verschlechterung innerhalb von 6 Wochen nach Beginn oder Erhöhung eines Medikaments (eigener Arbeitswert). */
    /** A medication begun or its dose raised on [date]: [name], and [what] the hint calls it. */
    private data class MedicationChange(val date: LocalDate, val name: String, val what: String)

    /** [change] with the mean of the symptoms [before] it and [after] it. */
    private data class Worsening(val change: MedicationChange, val before: Double, val after: Double)

    private fun medication(context: Context, missing: MutableList<MissingData>): Hint? {
        val records = context.records
        val names = records.medications.associate { it.id to it.name }
        val changes = records.medications.map { MedicationChange(it.start, it.name, "Beginn von ${it.name}") } +
            records.doseChanges.filter { isIncrease(it.from, it.to) }.mapNotNull { change ->
                names[change.medicationId]?.let { MedicationChange(change.date, it, "der Dosisänderung von $it (${change.from} → ${change.to})") }
            }
        val burden = records.levelsByDay.filterValues { it.isNotEmpty() }.mapValues { (_, levels) -> levels.values.average() }
        var shortest: Int? = null
        val worse = changes.filter { context.inCount(it.date) }.mapNotNull { change ->
            val date = change.date
            val before = burden.filterKeys { !it.isBefore(date.minusDays(HintRules.MEDICATION_BEFORE_DAYS)) && it.isBefore(date) }.values
            val windowEnd = date.plusDays(HintRules.MEDICATION_DAYS - 1)
            val after = burden.filterKeys { !it.isBefore(date) && !it.isAfter(windowEnd) && !it.isAfter(context.today) }.values
            when {
                before.size < HintRules.MEDICATION_EVENINGS -> null
                after.size < HintRules.MEDICATION_EVENINGS -> {
                    if (!windowEnd.isBefore(context.today)) shortest = minOf(shortest ?: Int.MAX_VALUE, HintRules.MEDICATION_EVENINGS - after.size)
                    null
                }
                after.average() - before.average() >= HintRules.MEDICATION_WORSE -> Worsening(change, before.average(), after.average())
                else -> null
            }
        }
        if (worse.isEmpty()) {
            shortest?.let { missing += MissingData("Medikamente", days(it)) }
            return null
        }
        val latest = worse.maxOf { it.change.date }
        val weeksBefore = HintRules.MEDICATION_BEFORE_DAYS / 7
        return Hint(
            rule = HintRule.Medication,
            title = "Medikamente",
            pattern = worse.joinToString(" ") { (change, before, after) ->
                "In den Wochen nach ${change.what} waren die Beschwerden im Mittel stärker als in den $weeksBefore Wochen davor " +
                    "(${number(after)} statt ${number(before)} von ${EntryLimits.LEVEL.last})."
            } + " Das passt zeitlich zu der Änderung, ohne dass sie die Ursache sein muss.",
            basis = DataBasis(
                worse.size,
                if (worse.size == 1) "Änderung, mit den Abendeinträgen davor und danach" else "Änderungen, mit den Abendeinträgen davor und danach",
                worse.minOf { it.change.date }.minusDays(HintRules.MEDICATION_BEFORE_DAYS),
                minOf(latest.plusDays(HintRules.MEDICATION_DAYS - 1), context.today),
            ),
            specialty = "die Verordnenden",
            question = "Kann die Verschlechterung mit ${worse.map { it.change.name }.distinct().joinToString(" oder ")} zusammenhängen? " +
                "Nichts selbst ändern, sondern mit den Behandelnden besprechen.",
            sources = emptyList(),
            evidence = worse.map { "change:${it.change.date}:${it.change.name}" }.toSet(),
            latest = latest,
            ownValues = "${HintRules.MEDICATION_DAYS / 7} Wochen danach, $weeksBefore Wochen davor, je mindestens " +
                "${HintRules.MEDICATION_EVENINGS} Abendeinträge und ein Anstieg um ${number(HintRules.MEDICATION_WORSE)} sind eigene Arbeitswerte, keine Leitlinie.",
        )
    }

    /** Whether [record]'s pulse rose as the POTS pattern asks: twice by 30 standing, the last three minutes leaning. */
    private fun rose(record: StandRecord): Boolean = when (record.test.variant) {
        StandVariant.Standing -> record.result.risesAtLeast30.size >= HintRules.RISES_NEEDED
        StandVariant.Leaning -> (record.result.lastThreeRise ?: 0.0) > StandTestRules.RISE || (record.result.lastThreeMean ?: 0.0) > HintRules.LEAN_MEAN_HIGH
    }

    /** Whether [record]'s pressure dropped at all, also systolic below 90: no finding to call it calm. */
    private fun dropped(record: StandRecord) = WeekRules.droppedEarly(record.result) || WeekRules.droppedLate(record.result)

    /** Stehtest: Pulsanstieg, Blutdruckabfall früh und verzögert, Beschwerden ohne Messauffälligkeit. Mindestdaten: 3 Stehtests
     * an verschiedenen Tagen; each pattern in at least 2 of them. */
    private fun stand(context: Context, missing: MutableList<MissingData>): List<Hint> {
        if (context.standDays < HintRules.MIN_STAND_TESTS) {
            missing += MissingData("Stehtest", noch(HintRules.MIN_STAND_TESTS - context.standDays, "Stehtest", "Stehtests"))
            return emptyList()
        }
        val zone = context.records.zone
        val tests = context.standTests.sortedBy { it.test.startedAt }
        fun hint(rule: HintRule, matching: List<StandRecord>, of: List<StandRecord>, build: (Int, Int) -> Triple<String, String, String>, title: String, specialty: String, sources: List<Source>): Hint? {
            if (matching.size < HintRules.STAND_POSITIVE) return null
            val (pattern, question, what) = build(matching.size, of.size)
            return Hint(
                rule = rule,
                title = title,
                pattern = pattern,
                basis = DataBasis(
                    of.size,
                    what,
                    of.first().dayIn(zone),
                    of.last().dayIn(zone),
                    tiers = if (of.any { it.filledFromGarmin }) setOf(Tier.A) else emptySet(),
                ),
                specialty = specialty,
                question = question,
                sources = sources,
                evidence = matching.map { "test:${it.test.id}" }.toSet(),
                latest = matching.last().dayIn(zone),
            )
        }
        val pots = tests.filter { rose(it) && !WeekRules.droppedBy2010(it.result) && (it.result.lyingPulse ?: 0) >= HintRules.LYING_PULSE_MIN && it.complaints }
        val pressured = tests.filter { WeekRules.hasPressure(it.result) }
        val calm = tests.filter { it.complaints && it.result.risesAtLeast30.isEmpty() && !rose(it) && !dropped(it) }
        return listOfNotNull(
            hint(
                HintRule.StandRise,
                pots,
                tests,
                { n, of ->
                    Triple(
                        "In $n von $of Stehtests stieg der Puls im Stehen um ${StandTestRules.RISE}/min oder mehr, ohne Blutdruckabfall und mit Beschwerden im Stehen – passt zu einem POTS-Muster.",
                        "Ist ein ärztlicher Stehtest über 10 Minuten sinnvoll, bei negativem Ergebnis eine Kipptischuntersuchung? Medikamente können die Ursache sein; das mit den Behandelnden besprechen.",
                        "Stehtests",
                    )
                },
                "Stehtest: Pulsanstieg",
                "Kardiologie oder autonome Ambulanz",
                listOf(Source.NihPots2021, Source.CmajPots2022, Source.DgnSyncope2020, Source.Lee2020),
            ),
            hint(
                HintRule.StandDropEarly,
                pressured.filter { WeekRules.droppedEarly(it.result) },
                pressured,
                { n, of ->
                    Triple(
                        "In $n von $of Stehtests mit Blutdruck fiel er innerhalb von ${StandTestRules.EARLY_MINUTES} Minuten um mindestens ${WeekRules.SYSTOLIC_DROP} mmHg systolisch, ${WeekRules.DIASTOLIC_DROP} mmHg diastolisch oder systolisch unter ${StandTestRules.SYSTOLIC_LOW} mmHg – passt zu einer orthostatischen Hypotonie.",
                        "Können Medikamente den Blutdruckabfall erklären? Das mit den Behandelnden besprechen.",
                        "Stehtests mit Blutdruck",
                    )
                },
                "Stehtest: Blutdruckabfall",
                "Hausarztpraxis oder Kardiologie",
                listOf(Source.EscSyncope2018, Source.DgnSyncope2020),
            ),
            hint(
                HintRule.StandDropLate,
                pressured.filter { WeekRules.droppedLate(it.result) },
                pressured,
                { n, of ->
                    Triple(
                        "In $n von $of Stehtests mit Blutdruck fiel er erst nach den ersten ${StandTestRules.EARLY_MINUTES} Minuten deutlich ab – passt zu einer verzögerten orthostatischen Hypotonie.",
                        "Ist eine Kipptischuntersuchung sinnvoll?",
                        "Stehtests mit Blutdruck",
                    )
                },
                "Stehtest: verzögerter Blutdruckabfall",
                "Kardiologie oder autonome Ambulanz",
                listOf(Source.EscSyncope2018, Source.DgnSyncope2020),
            ),
            hint(
                HintRule.StandComplaints,
                calm,
                tests,
                { n, of ->
                    Triple(
                        "In $n von $of Stehtests gab es Beschwerden im Stehen, ohne dass Puls oder Blutdruck auffällig waren – spricht dafür, den Test morgens zu wiederholen.",
                        "Ist ein ärztlicher Stehtest sinnvoll?",
                        "Stehtests",
                    )
                },
                "Stehtest: Beschwerden ohne Messauffälligkeit",
                "Hausarztpraxis, Kardiologie oder autonome Ambulanz",
                listOf(Source.CmajPots2022),
            ),
        )
    }

    /** PEM: Verschlechterung an Tag +1 oder +2 nach mindestens 2 von 3 Anstrengungen der Stärke 2–3, oder mindestens 2
     * Episoden mit einem Auslöser "sicher" oder "wahrscheinlich" und Beginn 12–72 Stunden danach. [IOM-2015] [NICE-NG206] */
    private fun pem(context: Context, missing: MutableList<MissingData>): Hint? {
        val records = context.records
        val eveningDays = records.evenings.map { it.date }.toSet()
        // A confirmed suggestion from the minutes above the pacing threshold never sets off a hint (SPEC.md §17).
        val strong = records.exertions.filter {
            it.origin != ExertionOrigin.HeartRate && it.strength in ExertionRules.FOLLOW_UP_STRENGTH && context.inCount(it.date) && !it.date.plusDays(HintRules.PEM_DAYS_AFTER.last).isAfter(context.today)
        }
        val answersAfter = { exertion: Exertion ->
            records.followUps.filter { answer -> answer.exertionId == exertion.id && (answer.date.toEpochDay() - exertion.date.toEpochDay()) in HintRules.PEM_DAYS_AFTER }
        }
        val withDaysAfter = strong.filter { exertion ->
            answersAfter(exertion).isNotEmpty() || HintRules.PEM_DAYS_AFTER.all { exertion.date.plusDays(it) in eveningDays }
        }.sortedWith(compareBy({ it.date }, { it.id }))
        val last = withDaysAfter.takeLast(HintRules.PEM_OF)
        val worsened = last.filter { exertion -> answersAfter(exertion).any { it.answer == FollowUpAnswer.Yes } }
        val byExertions = withDaysAfter.size >= HintRules.MIN_EXERTIONS && worsened.size >= HintRules.PEM_WORSENED

        val exertions = records.exertions.associateBy { it.id }
        val episodes = records.episodes.filter { context.inCount(it.startDate) }.filter { episode ->
            records.triggers.any { trigger ->
                trigger.episodeId == episode.id && trigger.certainty != Certainty.Unclear &&
                    exertions[trigger.exertionId]?.takeIf { it.origin != ExertionOrigin.HeartRate }
                        ?.let { ExertionRules.hoursAfter(it, episode).hours in HintRules.PEM_GAP_HOURS } == true
            }
        }.sortedBy { it.startDate }
        val byEpisodes = episodes.size >= HintRules.PEM_EPISODES
        if (!byExertions && !byEpisodes) {
            if (withDaysAfter.size < HintRules.MIN_EXERTIONS) {
                missing += MissingData("Verschlechterung nach Belastung", noch(HintRules.MIN_EXERTIONS - withDaysAfter.size, "Anstrengung", "Anstrengungen"))
            }
            return null
        }
        val counted = (if (byExertions) last else emptyList())
        val support = garminPulseAfter(records, if (byExertions) worsened else emptyList())
        val days = counted.map { it.date } + episodes.map { it.startDate }
        val patterns = listOfNotNull(
            if (byExertions) "Nach ${worsened.size} der letzten ${last.size} Anstrengungen der Stärke ${ExertionRules.FOLLOW_UP_STRENGTH.first}–${ExertionRules.FOLLOW_UP_STRENGTH.last} ging es am Tag danach oder am zweiten Tag schlechter." else null,
            if (byEpisodes) "${episodes.size} Episoden begannen ${HintRules.PEM_GAP_HOURS.first} bis ${HintRules.PEM_GAP_HOURS.last} Stunden nach einer Anstrengung, die als sicherer oder wahrscheinlicher Auslöser vermerkt ist." else null,
        )
        return Hint(
            rule = HintRule.Pem,
            title = "Verschlechterung nach Belastung",
            pattern = patterns.joinToString(" ") + " Das passt zu einer Verschlechterung nach Belastung (PEM).",
            basis = DataBasis(
                counted.size + episodes.size,
                listOfNotNull(if (byExertions) "Anstrengungen mit den Tagen danach" else null, if (byEpisodes) "Episoden" else null).joinToString(" und "),
                days.min(),
                days.max(),
                tiers = if (counted.any { it.origin == ExertionOrigin.Steps }) setOf(Tier.A) else emptySet(),
                supportedByB = support.isNotEmpty(),
            ),
            specialty = "Hausarztpraxis oder eine ME/CFS-Ambulanz",
            question = "Ist eine Abklärung auf ME/CFS sinnvoll? Belastung dafür nicht austesten.",
            sources = listOf(Source.Iom2015, Source.NiceNg206),
            evidence = (if (byExertions) worsened.map { "exertion:${it.id}" } else emptyList()).toSet() + episodes.map { "episode:${it.id}" },
            latest = days.max(),
            support = support,
        )
    }

    /** "Auch der Ruhepuls von Garmin war an den Tagen danach höher", tier B, if it was for the [worsened] exertions. */
    private fun garminPulseAfter(records: TrackingRecords, worsened: List<Exertion>): List<String> {
        val pulse = records.garmin.restingHeartRate
        val before = worsened.mapNotNull { pulse[it.date] }
        val after = worsened.flatMap { exertion -> HintRules.PEM_DAYS_AFTER.mapNotNull { pulse[exertion.date.plusDays(it)] } }
        if (before.isEmpty() || after.isEmpty() || after.average() <= before.average()) return emptyList()
        return listOf(
            "Auch der Ruhepuls von Garmin war an den Tagen danach höher (im Mittel ${after.average().roundToInt()} statt " +
                "${before.average().roundToInt()}/min, Stufe B, nur als Stütze).",
        )
    }

    /** Haut. [SAJJAN-2015] Mindestdaten: 5 Ereignisse; each pattern in at least 2 of them (eigene Festlegung). */
    private fun skin(context: Context, missing: MutableList<MissingData>): List<Hint> {
        val events = context.events.sortedBy { it.at }
        if (events.size < HintRules.MIN_EVENTS) {
            missing += MissingData("Haut", noch(HintRules.MIN_EVENTS - events.size, "Ereignis", "Ereignisse"))
            return emptyList()
        }
        val records = context.records
        fun hint(rule: HintRule, matching: List<TrackingEvent>, title: String, pattern: (Int) -> String, specialty: String, question: String): Hint? {
            if (matching.size < HintRules.SKIN_EVENTS) return null
            return Hint(
                rule = rule,
                title = title,
                pattern = pattern(matching.size),
                basis = DataBasis(events.size, "Ereignisse", records.dayOf(events.first().at), records.dayOf(events.last().at)),
                specialty = specialty,
                question = question,
                sources = listOf(Source.Sajjan2015),
                evidence = matching.map { "event:${it.id}" }.toSet(),
                latest = records.dayOf(matching.last().at),
                ownValues = "Mehr als ${HintRules.SKIN_MINUTES} Minuten und mindestens ${HintRules.SKIN_EVENTS} Ereignisse sind eigene Arbeitswerte.",
            )
        }
        val changed = events.filter { it.skinAreas.isNotEmpty() }
        return listOfNotNull(
            hint(
                HintRule.SkinPersistent,
                // Minutes not recorded count neither as long nor as short (SPEC.md §17, "Leer … zählt nie als 0").
                changed.filter { (it.skinFadeMinutes?.let { m -> m > HintRules.SKIN_MINUTES } == true) || it.skinPattern == SkinPattern.Reticular || it.skinPainful == true },
                "Haut: bleibt, netzartig oder schmerzhaft",
                { n -> "Bei $n von ${events.size} Ereignissen blieb die Hautveränderung über ${HintRules.SKIN_MINUTES} Minuten, war unterbrochen-netzartig oder schmerzte – spricht dafür, die Haut ärztlich ansehen zu lassen." },
                "Dermatologie oder Rheumatologie",
                "Ist eine Bestimmung der Antiphospholipid-Antikörper sinnvoll?",
            ),
            hint(
                HintRule.SkinFingersToes,
                changed.filter { SkinArea.FingersToes in it.skinAreas },
                "Haut: Finger oder Zehen",
                { n -> "Bei $n von ${events.size} Ereignissen waren Finger oder Zehen betroffen – passt zu einem Raynaud-Phänomen." },
                "Hausarztpraxis oder Rheumatologie",
                "Kann ein Raynaud-Phänomen dahinterstecken?",
            ),
            hint(
                HintRule.SkinPooling,
                changed.filter { SkinArea.Legs in it.skinAreas && it.skinFadesLying == true && it.skinFadeMinutes?.let { m -> m <= HintRules.SKIN_MINUTES } == true },
                "Haut: Beine, im Liegen rasch weg",
                { n -> "Bei $n von ${events.size} Ereignissen betraf die Hautveränderung die Beine und verschwand im Liegen rasch – passt zu Blutansammlung in den Beinen und stützt die Stehtests." },
                "Kardiologie oder autonome Ambulanz",
                "Passt das zu den Stehtests, und ist ein ärztlicher Stehtest sinnvoll?",
            ),
        )
    }

    /** Schlaf: Schlafqualität ≥ 2 an der Mehrzahl der Nächte. [DEGAM-MUEDIGKEIT] Garmin's length of sleep, tier B, backs it up. */
    private fun sleep(context: Context, missing: MutableList<MissingData>): Hint? {
        val nights = context.records.mornings.filter { context.inDays(it.date) && it.sleepQuality != null }.sortedBy { it.date }
        if (nights.size < HintRules.MIN_DAYS) {
            missing += MissingData("Schlaf", days(HintRules.MIN_DAYS - nights.size))
            return null
        }
        val poor = nights.filter { it.sleepQuality!! >= HintRules.LEVEL_HIGH }
        if (!majority(poor.size, nights.size)) return null
        val garmin = context.records.garmin.sleepMinutes
        val poorMinutes = poor.mapNotNull { garmin[it.date] }
        val otherMinutes = (nights - poor.toSet()).mapNotNull { garmin[it.date] }
        val support = if (poorMinutes.isNotEmpty() && otherMinutes.isNotEmpty() && poorMinutes.average() < otherMinutes.average()) {
            listOf("Auch die Schlafdauer laut Garmin war in diesen Nächten kürzer (im Mittel ${number(poorMinutes.average() / 60)} statt ${number(otherMinutes.average() / 60)} Stunden, Stufe B, nur als Stütze).")
        } else {
            emptyList()
        }
        return Hint(
            rule = HintRule.Sleep,
            title = "Schlaf",
            pattern = "Die Schlafqualität war in ${poor.size} von ${nights.size} Nächten schlecht (2 oder 3) – spricht dafür, den Schlaf anzusprechen.",
            basis = DataBasis(nights.size, "Morgeneinträge mit Schlafqualität", nights.first().date, nights.last().date, supportedByB = support.isNotEmpty()),
            specialty = "Hausarztpraxis",
            question = "Kann der Schlaf gestört sein, auch durch Schnarchen oder Atempausen?",
            sources = listOf(Source.DegamFatigue),
            evidence = poor.map { "night:${it.date}" }.toSet(),
            latest = poor.last().date,
            support = support,
        )
    }

    /** Stimmung: gedrückt oder Angst ≥ 2 an der Mehrzahl der Tage. [DEGAM-MUEDIGKEIT] */
    private fun mood(context: Context, missing: MutableList<MissingData>): Hint? {
        val ids = context.symptomIds(HintRules.LOW_MOOD, HintRules.ANXIETY)
        val days = context.records.levelsByDay.filterKeys(context::inDays)
            .mapValues { (_, levels) -> levels.filterKeys { it in ids }.values }
            .filterValues { it.isNotEmpty() }
            .toSortedMap()
        if (days.size < HintRules.MIN_DAYS) {
            missing += MissingData("Stimmung", days(HintRules.MIN_DAYS - days.size))
            return null
        }
        val high = days.filterValues { levels -> levels.any { it >= HintRules.LEVEL_HIGH } }.keys.toList()
        if (!majority(high.size, days.size)) return null
        return Hint(
            rule = HintRule.Mood,
            title = "Stimmung",
            pattern = "Stimmung gedrückt oder Angst/innere Unruhe lag an ${high.size} von ${days.size} Tagen bei 2 oder mehr – spricht dafür, das anzusprechen, " +
                "parallel zur körperlichen Abklärung und nicht erst danach.",
            basis = DataBasis(days.size, "Abendeinträge mit Stimmung oder Angst", days.firstKey(), days.lastKey()),
            specialty = "Hausarztpraxis, Psychotherapie oder Psychiatrie",
            question = "Wie lässt sich die seelische Belastung parallel zur körperlichen Abklärung angehen?",
            sources = listOf(Source.DegamFatigue),
            evidence = high.map { "day:$it" }.toSet(),
            latest = high.last(),
        )
    }

    /** Trinkmenge an der Mehrzahl der Tage unter 1,5 l (eigener Arbeitswert). */
    private fun fluid(context: Context, missing: MutableList<MissingData>): Hint? {
        val evenings = context.records.evenings.filter { context.inDays(it.date) && it.fluidDecilitres != null }.sortedBy { it.date }
        if (evenings.size < HintRules.MIN_DAYS) {
            missing += MissingData("Trinkmenge", days(HintRules.MIN_DAYS - evenings.size))
            return null
        }
        val low = evenings.filter { it.fluidDecilitres!! < HintRules.FLUID_LOW_DECILITRES }
        if (!majority(low.size, evenings.size)) return null
        return Hint(
            rule = HintRule.Fluid,
            title = "Trinkmenge",
            pattern = "An ${low.size} von ${evenings.size} Tagen waren es weniger als ${litres(HintRules.FLUID_LOW_DECILITRES)} l – spricht dafür, die Trinkmenge anzusprechen.",
            basis = DataBasis(evenings.size, "Abendeinträge mit Trinkmenge", evenings.first().date, evenings.last().date),
            specialty = "Hausarztpraxis",
            question = "Welche Trinkmenge ist bei diesen Beschwerden sinnvoll?",
            sources = emptyList(),
            evidence = low.map { "day:${it.date}" }.toSet(),
            latest = low.last().date,
            ownValues = "${litres(HintRules.FLUID_LOW_DECILITRES)} l ist ein eigener Arbeitswert, keine Leitlinie.",
        )
    }
}
