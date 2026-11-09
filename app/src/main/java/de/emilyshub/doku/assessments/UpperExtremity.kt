package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/** The forms of DIGOTOR's subcategory "Obere Extremität" (#70), each as in its PDF of the date in its source. */
internal val UPPER_EXTREMITY: List<Assessment> by lazy { listOf(DASH, ISIS, KTS, NISIS, PRTEE, PRWE_G, SPADI, SST, WALCH_DUPLAY, WOSI) }

/** A score of [items], better the lower, all of them needed. */
private fun lowerBetter(id: String, name: String, items: List<Item>, rule: ScoreRule = ScoreRule.Sum, unit: String = "Punkte", bands: List<Band> = emptyList(), divisor: Int = 1) =
    Score(id, name, ids(items), rule = rule, unit = unit, direction = Direction.LowerIsBetter, bands = bands, divisor = divisor)

/** The sum of the scores [parts] before it, better the lower. */
private fun lowerBetterTotal(id: String, name: String, vararg parts: String) =
    Score(id, name, emptyList(), direction = Direction.LowerIsBetter, sumOf = parts.toList())

/** A choice of [labels] that no score counts, such as the side affected. */
private fun choose(id: String, text: String, vararg labels: String) = ChoiceItem(id, text, unscored(*labels))

/** Scales of 0 to 10 lettered a), b) … as the PRTEE letters them, their ids [prefix]-a, [prefix]-b …, their ends [least] and [most]. */
private fun lettered(prefix: String, least: String, most: String, vararg texts: String): List<Item> =
    texts.mapIndexed { i, text -> ('a' + i).let { letter -> ScaleItem("$prefix-$letter", "$letter) $text", 0, 10, least, most) } }

/** Instability Severity Index Score (Balg et al. 2007), as in "Instability-Severity-Score_ISIS_10-2026.pdf". */
private val ISIS: Assessment = run {
    val items = listOf(
        ChoiceItem("1", "1. Alter", scored("≤20" to 2, ">20" to 0)),
        ChoiceItem("2", "2. Sportpartizipation (präoperativ)", scored("Wettkampfniveau" to 2, "Freizeitniveau bzw. kein Sport" to 0)),
        ChoiceItem("3", "3. Sportart", scored("Kontaktsport und/oder Überkopfsport" to 1, "jede andere Sportart" to 0)),
        // The original gives no points for "negativ": 0, as the best is 0.
        ChoiceItem(
            "4", "4. Schulter-Hyperlaxität",
            scored("positiv (ARO in 0° ABD >85°, ABD >20° im Seitenvergleich größer)" to 1, "negativ (ARO in 0° ABD ≤85, ABD ≤ 20° im Seitenvergleich größer)" to 0),
        ),
        ChoiceItem("5", "5. Hill-Sachs-Läsion (Röntgen)", scored("sichtbar in AP-Aufnahme in Außenrotation" to 2, "nicht sichtbar in AP-Aufnahme in Außenrotation" to 0)),
        ChoiceItem("6", "6. Verlust der Glenoid-Kontur (knöcherner Schaden, Röntgen)", scored("sichtbar in AP-Aufnahme" to 2, "nicht sichtbar in AP-Aufnahme" to 0)),
    )
    Assessment(
        id = "isis",
        name = "Instability Severity Index Score",
        abbreviation = "ISIS",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "ISIS, Stand 10/2026",
        hint = "Balg et al. 2007.\n\n" +
            "Der Score wird vom Arzt/Ärztin bzw. vom Therapeut/-in ausgefüllt. Die Einzelwerte werden zu einem Gesamtscore " +
            "addiert (bester Wert 0, schlechtester Wert 10). Ein höherer Score geht mit einem höheren Rezidivrisiko nach einer " +
            "arthroskopischen Bankart-Stabilisation einher.\n\n" +
            "Nach Balg et al. 2007 steht ein hoher Score 6/10 mit einer höheren Rezidivrate nach einer arthroskopischen " +
            "Bankart-Stabilisation Im Zusammenhang. Das ursprüngliche Ziel war es dementsprechend einen Prädiktor für das " +
            "Rezidiv und auch die Indikation für eine andere Operationsmethode zu evaluieren (offene Bankart-Stabilisation oder " +
            "Stabilisation nach Latarjet). Der ursprüngliche Grenzwert (6/10) wurde in diversen Arbeiten mittlerweile nach " +
            "unten korrigiert. So besteht ein höheres Rezidivrisiko schon bei Werten >3 (Cavalier et al 2021, Verweij et al. " +
            "2021). Der Score wurde insbesondere für Patienten/-innen mit einer rezidivierenden Instabilität entwickelt. Der " +
            "prädiktive Wert bei einer konservativen Nachbehandlung ist momentan unklar (Longo et al. 2021, Tokish et al. 2020).",
        sections = listOf(Section(items = items)),
        // Both bounds of the original, the later > 3 and Balg's 6 of 10 (09.11.2026).
        scores = listOf(
            lowerBetter(
                "gesamt", "Gesamtscore", items,
                bands = listOf(
                    Band.upTo(3, null),
                    Band.upTo(5, "Höheres Rezidivrisiko schon bei Werten >3"),
                    Band.upTo(10, "Hoher Score (6/10 nach Balg et al. 2007): höhere Rezidivrate nach einer arthroskopischen Bankart-Stabilisation"),
                ),
            ),
        ),
    )
}

/** Nonoperative Instability Severity Index Score (Tokish et al. 2020), as in "Nonoperative-Instability-Severity-Score_NISIS_10-2026.pdf". */
private val NISIS: Assessment = run {
    val items = listOf(
        ChoiceItem("1", "1. Alter", scored(">15" to 2, "≤15" to 0)),
        ChoiceItem("2", "2. Geschlecht", scored("männlich" to 1, "weiblich" to 0)),
        ChoiceItem("3", "3. Sportart", scored("Kontaktsport" to 3, "jede andere Sportart" to 0)),
        ChoiceItem("4", "4. betroffener Arm", scored("dominant" to 1, "nicht dominant" to 0)),
        ChoiceItem("5", "5. Schweregrad der Instabilität", scored("Luxation" to 1, "Subluxation" to 0)),
        ChoiceItem("6", "6. knöcherner Schaden (Röntgen)", scored("sichtbar/vorhanden" to 2, "nicht sichtbar/nicht vorhanden" to 0)),
    )
    Assessment(
        id = "nisis",
        name = "Nonoperative Instability Severity Score",
        abbreviation = "NISIS",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "NISIS, Stand 10/2026",
        hint = "Tokish et al. 2020.\n\n" +
            "Der Score wird vom Arzt/Ärztin bzw. vom Therapeut/-in ausgefüllt. Die Einzelwerte werden zu einem Gesamtscore " +
            "addiert (bester Wert 0, schlechtester Wert 10). Ein höherer Score geht mit einem fehlgeschlagenen Return to Sport " +
            "im Rahmen einer konservativen Therapie einher.\n\n" +
            "Nach Tokish et al. gelingt bei einem höheren Score ≥7/10 seltener ein Return to Sport. Das ursprüngliche Ziel war " +
            "es dementsprechend einen Prädiktor für den Return to Sport zu evaluieren. Nach Magari et al. (2022) weist der " +
            "Score keinen prädiktiven Wert für eine Rezidivinstabilität (Luxation, Subluxation) auf.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            lowerBetter("gesamt", "Gesamtscore", items, bands = listOf(Band.upTo(6, null), Band.upTo(10, "Bei einem höheren Score ≥7/10 gelingt seltener ein Return to Sport"))),
        ),
    )
}

/** The clinical questionnaire for carpal tunnel syndrome (Kamath & Stothard 2003), as in "Karpaltunnel-Scoring_10-2026.pdf". */
private val KTS: Assessment = run {
    fun yesNo(id: String, text: String, yes: Int = 1, no: Int = 0) = ChoiceItem(id, text, scored("JA" to yes, "NEIN" to no))
    fun withNv(id: String, text: String, yes: Int, no: Int) = ChoiceItem(id, text, scored("JA" to yes, "NEIN" to no, "NV" to 0))
    val items = listOf(
        yesNo("1", "Haben sie Schmerzen im Handgelenk, die Sie nachts aufwachen lassen?"),
        yesNo("2", "Haben Sie Kribbeln oder Taubheit in der Hand, die Sie nachts aufwachen lassen?"),
        yesNo("3", "Verspüren Sie unmittelbar nach dem Aufwachen am Morgen Kribbeln und Taubheit in der Hand?"),
        yesNo("4", "Machen Sie irgendwelche speziellen Bewegungen, um Kribbeln oder Taubheit in der Hand zu beseitigen?"),
        yesNo("5", "Hatten sie zu irgendeiner Zeit Kribbeln oder Taubheit im kleinen Finger?", yes = 0, no = 3),
        yesNo("6", "Waren Kribbeln oder Taubheit vorhanden, während sie eine Zeitung gelesen, gestrickt haben oder Auto gefahren sind?"),
        yesNo("7", "Haben Sie Nackenschmerzen?"),
        // The original prints NEIN (1) as well, a slip for 0 (09.11.2026).
        withNv("8", "Waren Kribbeln oder Taubheit während der Schwangerschaft in der Hand stärker vorhanden?", yes = 1, no = 0),
        withNv("9", "Hat das Tragen einer Schiene Kribbeln oder Taubheit verbessert?", yes = 2, no = 0),
    )
    Assessment(
        id = "karpaltunnel",
        name = "Karpaltunnel-Scoring",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "Karpaltunnel-Scoring, Stand 10/2026",
        hint = "Kamath V, Stothard J. A clinical questionnaire for the diagnosis of carpal tunnel syndrome. 2003.\n\n" +
            "Bei einer Punktzahl von 3-4 sollte mittels Nervenleitgeschwindigkeitsmessung eine Diagnosestellung durchgeführt " +
            "werden. Ab 5 Punkten und mehr kann dieser Test eine Nervenleitgeschwindigkeitsmessung ersetzen und direkt eine " +
            "Dekompression des Nervus medianus empfohlen werden.\n\n" +
            "Ob immer gleich eine Operation durchgeführt werden muss oder vorher noch ein konservativer Ansatz gewählt werden " +
            "kann (Infiltration, Schiene, etc.), wurde nicht untersucht.",
        sections = listOf(
            Section("Klinischer Fragebogen zur Diagnose eines Karpaltunnel-Syndroms", "Bitte kreisen Sie JA, NEIN oder NICHT ZUTREFFEND (NV) an.", items),
        ),
        scores = listOf(
            lowerBetter(
                "gesamt", "Gesamtsumme", items,
                bands = listOf(
                    Band.upTo(2, null),
                    Band.upTo(4, "Bei einer Punktzahl von 3-4 sollte mittels Nervenleitgeschwindigkeitsmessung eine Diagnosestellung durchgeführt werden."),
                    Band.upTo(12, "Ab 5 Punkten und mehr kann dieser Test eine Nervenleitgeschwindigkeitsmessung ersetzen und direkt eine Dekompression des Nervus medianus empfohlen werden."),
                ),
            ),
        ),
    )
}

/** Patient-Rated Tennis Elbow Evaluation, German version (Marks et al. 2020), as in "…_PRTEE_10-2026.pdf". */
private val PRTEE: Assessment = run {
    val pain = lettered(
        "schmerz", "kein Schmerz", "stärkster vorstellbarer Schmerz",
        "In Ruhe", "Bei Tätigkeiten mit sich wiederholenden Armbewegungen", "Beim Tragen einer schweren Tragetasche",
        "Wenn der Schmerz am schwächsten ist", "Wenn der Schmerz am stärksten ist",
    )
    val specific = lettered(
        "taetigkeit", "keine Schwierigkeiten", "unmöglich",
        "Drehen eines Schlüssels", "Tragen einer Tragetasche", "Heben eines vollen Glases zum Mund", "Öffnen eines Konfitürenglases",
        "Anziehen einer Hose", "Auswringen eines Waschlappens",
    )
    val usual = lettered(
        "alltag", "keine Schwierigkeiten", "unmöglich",
        "Körperpflege (Anziehen, Waschen)", "Haushalt (z.B. Putzen, Kochen)", "Bei der Ausübung der Arbeit (Beruf oder tägliche Beschäftigung)",
        "In der Freizeit/beim Sport",
    )
    Assessment(
        id = "prtee",
        name = "Patient-Rated Tennis Elbow Evaluation",
        abbreviation = "PRTEE",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "PRTEE, Stand 10/2026",
        hint = "Validierte deutsche Version (Marks et al. 2020).\n\n" +
            "Die folgenden Fragen werden uns helfen, zu verstehen, wie stark Sie in der letzten Woche aufgrund Ihrer " +
            "Armbeschwerden beeinträchtigt waren. Bitte beschreiben Sie Ihre durchschnittlichen Beschwerden über die gesamte " +
            "letzte Woche gesehen auf einer Skala von 0-10. Bitte beantworten Sie jede Frage. Falls Sie eine Aktivität in der " +
            "letzten Woche nicht durchgeführt haben, schätzen Sie einfach das Ausmaß der Einschränkung, als ob Sie diese " +
            "Tätigkeit durchgeführt hätten. Sofern Sie eine Aktivität gar nie durchführen konnten oder eine Schätzung nicht " +
            "möglich ist, streichen Sie bitte die betroffene Frage durch.\n\n" +
            "Wenn irgendeine Frage unbeantwortet bleibt, kann sie durch den Mittelwert der entsprechenden Kategorie ersetzt " +
            "werden.\n\n" +
            "Schmerz-Score: Die Summe der fünf Fragen (0=kein Schmerz; 50=stärkster vorstellbarer Schmerz). " +
            "Funktionsbewertung: Die Summe aller Fragen in den Subskalen „Ausgewählte Tätigkeiten“ und „Alltagsaktivitäten“ " +
            "geteilt durch 2 (0=keine Schwierigkeit; 50=unmöglich). Gesamtpunktzahl: Schmerzpunktzahl + Funktionspunktzahl.\n\n" +
            "Ein kleinerer Punktwert steht für eine geringere Symptomatik. Hohe Werte entsprechen einer großen " +
            "Schmerzsymptomatik und funktionellen Einschränkung.",
        sections = listOf(
            Section(
                "Schmerz im betroffenen Arm",
                "Bewerten Sie den durchschnittlichen Ellbogenschmerz bei den unten genannten Tätigkeiten im betroffenen Arm in der " +
                    "vergangenen Woche, indem Sie die entsprechende Zahl ankreuzen. Wir bitten Sie, Ihre Schmerzen auf einer " +
                    "Skala von 0-10 anzugeben. 0 entspricht keinem Schmerz, 10 entspricht dem stärksten vorstellbaren Schmerz.",
                pain,
            ),
            Section(
                "Ausgewählte Tätigkeiten",
                "Bitte beurteilen Sie, wie schwer es für Sie war, die unten stehenden Aktivitäten während der letzten Woche mit " +
                    "Ihrem betroffenen Arm auszuüben. Kreuzen Sie auf der Skala von 0-10 die Zahl an, die Ihre Schwierigkeiten bei " +
                    "der Ausübung der folgenden Tätigkeiten am besten beschreibt. 0 bedeutet, dass Sie keine Probleme damit " +
                    "hatten, 10 bedeutet, dass es unmöglich war, diese Tätigkeit auszuführen.",
                specific,
            ),
            Section(
                "Alltagsaktivitäten",
                // "durschnittlichen" and "enstprechende" as in the original, slips mended.
                "Bewerten Sie die durchschnittlichen Schwierigkeiten bei den unten genannten alltäglichen Tätigkeiten während " +
                    "der letzten Woche und kreuzen Sie die entsprechende Zahl an. Mit alltäglichen Tätigkeiten sind Aktivitäten " +
                    "gemeint, welche Sie schon früher, das heißt schon vor dem Armproblem, durchgeführt hatten. 0 bedeutet, dass " +
                    "Sie keine Probleme damit hatten, 10 bedeutet, dass es unmöglich war, diese Tätigkeit auszuführen.",
                usual,
            ),
        ),
        // Each left out filled by the mean of its section, once half of each is answered (09.11.2026).
        scores = listOf(
            lowerBetter("schmerz", "Schmerz-Score", pain, rule = ScoreRule.MeanFilled),
            lowerBetter("funktion", "Funktionsbewertung", specific + usual, rule = ScoreRule.MeanFilled, divisor = 2),
            lowerBetterTotal("gesamt", "Gesamtpunktzahl", "schmerz", "funktion"),
        ),
    )
}

/** Patient-Rated Wrist Evaluation, German version (John et al. 2008), as in "…_PRWE-G_10-2026.pdf". */
private val PRWE_G: Assessment = run {
    val pain = listOf(
        "In Ruhe", "Bei Tätigkeiten mit wiederholter Bewegung des Handgelenks", "Beim Heben eines schweren Gegenstandes", "Wenn sie am stärksten sind",
    ).mapIndexed { i, text -> ScaleItem("${i + 1}", "${i + 1}. $text", 0, 10, "kein Schmerz", "größtmöglicher Schmerz") } +
        ScaleItem("5", "5. Wie häufig haben Sie Schmerzen?", 0, 10, "nie", "immer")
    // "Trifft nicht zu" is not counted in the sum of its subscale, so as 0; it answers the question, which all should be (09.11.2026).
    val difficulty = (0..10).map { Option("$it", if (it == 0) "0 keine Schwierigkeiten" else if (it == 10) "10 nicht möglich" else "$it", it) } +
        Option("na", "trifft nicht zu", 0)
    fun function(from: Int, vararg texts: String) = texts.mapIndexed { i, text -> ChoiceItem("${from + i}", "${from + i}. $text", difficulty) }
    val specific = function(
        6, "Mit der betroffenen Hand einen Türknauf drehen", "Mit der betroffenen Hand Fleisch mit dem Messer schneiden",
        "Ein Hemd oder eine Bluse zuknöpfen", "Die betroffene Hand nutzen, um von einem Stuhl aufzustehen",
        "Mit der betroffenen Hand einen 5 kg schweren Gegenstand tragen", "Mit der betroffenen Hand das Toilettenpapier benutzen",
    )
    val usual = function(
        12, "Persönliche Körperpflege (Anziehen, Waschen)", "Hausarbeit (Putzen, Aufräumen, kleine Reparaturen)",
        "Arbeit (Beruf oder Alltagstätigkeiten)", "Freizeitaktivitäten",
    )
    Assessment(
        id = "prwe-g",
        name = "Patient-Rated Wrist Evaluation Score",
        abbreviation = "PRWE-G",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "PRWE-G, Stand 10/2026",
        hint = "Validierte deutsche Version - M. John et al. 2008.\n\n" +
            "Die unten aufgeführten Fragen sollten uns helfen, das Ausmaß der Schwierigkeiten, die Sie wegen Ihres Handgelenks " +
            "in der letzten Woche hatten, zu verstehen. Sie werden gebeten, auf einer Skala von 0 bis 10 anzugeben, wie stark " +
            "Ihre Handgelenksbeschwerden bei den folgenden Tätigkeiten in der letzten Woche durchschnittlich waren. Bitte " +
            "beantworten Sie alle Fragen. Wenn Sie eine (oder mehrere) der Tätigkeiten in der letzten Woche nicht ausgeführt " +
            "haben, schätzen Sie bitte das Ausmaß der Schmerzen oder Schwierigkeiten ein, die Sie erwarten würden. Wenn Sie " +
            "eine Tätigkeit niemals ausgeführt haben, kreuzen Sie bitte jeweils „trifft nicht zu“ an.\n\n" +
            "Der PRWE-Score setzt sich aus zwei Subskalen zusammen: einer Schmerz-Subskala und einer Funktions-Subskala. " +
            "Schmerz-Subskala: Summe der Punktwerte aus Frage 1–5 (je 0–10 Punkte) = 0–50 Punkte. Funktions-Subskala: Summe " +
            "der Punktwerte aus Frage 6–15 (je 0–10 Punkte, Summe max. 100), geteilt durch 2 = 0–50 Punkte. Gesamtscore " +
            "(PRWE-Score): Schmerz-Subskala + Funktions-Subskala = 0–100 Punkte.\n\n" +
            "Ein niedrigerer Wert entspricht weniger Schmerz bzw. weniger Beeinträchtigung, ein höherer Wert entspricht " +
            "stärkerer Beeinträchtigung. Antworten mit „trifft nicht zu“ werden bei der Berechnung der jeweiligen Subskala " +
            "nicht mitgezählt.\n\n" +
            "Klinisch relevante Veränderung: Als minimal klinisch bedeutsamer Unterschied (MCID) werden in der Literatur ca. " +
            "11–12 Punkte im Gesamtscore angegeben.",
        sections = listOf(
            Section(
                "1. Schmerzen",
                "Bitte geben Sie die durchschnittliche Stärke der Schmerzen in Ihrem Handgelenk in der letzten Woche an, indem " +
                    "Sie die Zahl auf der Skala von 0 bis 10 ausfüllen, die Ihre Schmerzen am besten beschreibt. Null (0) bedeutet, " +
                    "dass Sie keinerlei Schmerzen hatten und Zehn (10) bedeutet, dass Sie die schlimmsten Schmerzen hatten, die Sie " +
                    "je erlebt haben oder, dass Sie die Tätigkeit aufgrund der Schmerzen nicht ausführen konnten.",
                pain,
            ),
            Section(
                "2. Funktion – Funktionsfähigkeit - Bestimmte Tätigkeiten",
                "Bitte geben Sie an, wie viele Schwierigkeiten Sie in der letzten Woche bei jeder der unten aufgeführten " +
                    "Tätigkeiten hatten. Bitte kreuzen Sie die Zahl von 0 bis 10 an, die das Ausmaß Ihrer Schwierigkeiten am besten " +
                    "beschreibt. Null (0) bedeutet, dass Sie keinerlei Schwierigkeiten hatten und Zehn (10) bedeutet, dass die " +
                    "Schwierigkeiten so groß waren, dass Sie die Tätigkeit nicht ausführen konnten. Wenn Sie eine Tätigkeit " +
                    "niemals ausgeführt haben, kreuzen Sie bitte den Punkt „trifft nicht zu“ an.",
                specific,
            ),
            Section(
                "Funktionsfähigkeit - Gewöhnliche Tätigkeiten",
                "Bitte geben Sie an, wie viele Schwierigkeiten Sie in der letzten Woche bei Ihren gewöhnlichen Tätigkeiten in " +
                    "jedem der unten genannten Bereiche hatten. Bitte füllen Sie die Zahl von 0 bis 10 aus, die das Ausmaß Ihrer " +
                    "Schwierigkeiten am besten beschreibt. Unter „gewöhnliche Tätigkeiten“ verstehen wir die Aktivitäten, die Sie " +
                    "ausführten, bevor die Probleme mit Ihrem Handgelenk begannen. Null (0) bedeutet, dass Sie keinerlei " +
                    "Schwierigkeiten hatten und Zehn (10) bedeutet, dass Sie so große Schwierigkeiten hatten, dass Sie keine dieser " +
                    "gewöhnlichen Tätigkeit ausführen konnten. Wenn Sie eine Tätigkeit niemals ausgeführt haben, kreuzen Sie bitte " +
                    "den Punkt „trifft nicht zu“ an.",
                usual,
            ),
        ),
        scores = listOf(
            lowerBetter("schmerz", "Schmerz-Subskala", pain),
            lowerBetter("funktion", "Funktions-Subskala", specific + usual, divisor = 2),
            lowerBetterTotal("gesamt", "Gesamtscore (PRWE-Score)", "schmerz", "funktion"),
        ),
    )
}

/** Shoulder Pain and Disability Index, German version (Angst et al. 2007), as in "…_SPADI_10-2026.pdf". */
private val SPADI: Assessment = run {
    val pain = listOf(
        "wenn sie am stärksten sind?", "wenn Sie auf der betroffenen Seite liegen?", "wenn Sie nach etwas auf einem hohen Gestell greifen?",
        "wenn Sie Ihren Nacken berühren?", "wenn Sie mit dem betroffenen Arm etwas stoßen/gegen etwas drücken?",
    ).mapIndexed { i, text -> ScaleItem("a${i + 1}", "${i + 1}. $text", 0, 10, "Keine Schmerzen", "Schlimmste Schmerzen") }
    val function = listOf(
        "wenn Sie sich die Haare waschen?", "wenn Sie sich Ihren Rücken waschen?", "wenn Sie ein Unterhemd oder einen Pullover anziehen?",
        "wenn Sie sich ein Hemd oder eine Bluse anziehen, die vorne zugeknöpft wird?", "wenn Sie Ihre Hosen anziehen?",
        "wenn Sie einen Gegenstand auf ein hohes Gestell legen?", "wenn Sie einen Gegenstand von etwa 5kg tragen?",
        "wenn Sie etwas aus Ihrer hinteren (Hosen-) Tasche nehmen?",
    ).mapIndexed { i, text -> ScaleItem("b${i + 1}", "${i + 1}. $text", 0, 10, "Keine Schwierigkeiten", "Tätigkeit nicht ausführbar") }
    Assessment(
        id = "spadi",
        name = "Shoulder Pain and Disability Index",
        abbreviation = "SPADI",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "SPADI, Stand 10/2026",
        hint = "Fragebogen zu Schulterschmerzen, validierte deutsche Version (Angst et al. 2007).\n\n" +
            "Der SPADI wurde 1990 entwickelt, um Schmerz und Funktion in Zusammenhang mit Schulterpathologien zu messen. Es " +
            "soll den jetzigen Status und die Veränderung über eine bestimmte Zeit messen (Roach et al. 1991). Es ist ein PRO " +
            "Assessment und besteht aus 13 Fragen mit zwei Subskalen. Fünf Fragen beziehen sich auf Schmerz, acht auf Funktion " +
            "und werden mit 0 = kein Schmerz bis 10 = schlimmster Schmerz bzw. für die Funktion mit 0 = nicht schwierig bis 10 = " +
            "brauche Hilfe bewertet. Die Auswertung erfolgt mit dem Zusammenzählen der Punkte. 0 ist das bestmögliche, 130 das " +
            "schlechteste Ergebnis.\n\n" +
            "Eine deutsche kulturelle Validierung ist vorhanden (Angst et al. 2007). Sie beschreiben die interne Konsistenz mit " +
            "Cronbachs Alpha von 0.95, die Reliabilität mit r = 0.94, der MDC wird mit 18 Punkten angegeben. Es bestehen leichte " +
            "Boden und Deckeneffekte.\n\n" +
            "Der Patient gibt sich selbst Punkte zwischen 0 (keine Schmerzen/keine Schwierigkeiten) und 10 (schlimmste " +
            "Schmerzen/Tätigkeit nicht ausführbar). Achtung: Alle Fragen müssen beantwortet werden!\n\n" +
            "Total Score: Summe der Antwortpunkte / 130 x 100. 100 Punkte = maximale Schmerzen, keine der Tätigkeiten ist " +
            "durchführbar; 0 Punkte = keine Schmerzen, keine Schwierigkeiten.",
        sections = listOf(
            Section(
                "A SCHMERZFRAGEN",
                "Die folgenden Fragen beziehen sich auf die Stärke der Schmerzen, die Sie in Ihrer Schulter haben. Bitte geben " +
                    "Sie für jede Frage die Stärke der Schmerzen an. (Bitte kreuzen Sie die zutreffenden Kästchen an.)\n\n" +
                    "Wie stark sind Ihre Schmerzen,",
                pain,
            ),
            Section(
                "B FRAGEN ZUR KÖRPERLICHEN TÄTIGKEIT",
                "Die folgenden Fragen beziehen sich auf Ihre körperliche Tätigkeit. Damit ist Ihre Fähigkeit gemeint, sich im " +
                    "Alltag, um sich selbst zu kümmern. Bitte geben Sie für jede der folgenden Aktivitäten den Schwierigkeitsgrad " +
                    "an. (Bitte kreuzen Sie die zutreffenden Kästchen an)\n\n" +
                    "Wie viele Schwierigkeiten haben Sie,",
                function,
            ),
        ),
        // The original reckons only the total; each part in percent of its most, 50 and 80, as asked on 09.11.2026.
        scores = listOf(
            lowerBetter("schmerz", "Schmerz (A)", pain, rule = ScoreRule.Percent, unit = "%"),
            lowerBetter("funktion", "Körperliche Aktivität (B)", function, rule = ScoreRule.Percent, unit = "%"),
            lowerBetter("gesamt", "Total SPADI", pain + function, rule = ScoreRule.Percent, unit = "%"),
        ),
    )
}

/** Simple Shoulder Test, not validated German version, as in "Simple-Shoulder-Test_SST_10-2026.pdf". */
private val SST: Assessment = run {
    val items = yesNoItems(
        "Ist der betroffene Arm in Ruhe schmerzfrei?",
        "Können Sie von Seiten der Schulter her problemlos schlafen?",
        "Können Sie mit dem betroffenen Arm Ihr Hemd auf dem Rücken in die Hose stecken?",
        "Können Sie Ihre Hände mit zur Seite gestreckten Ellenbogen auf den Hinterkopf legen?",
        "Können Sie den gestreckten Arm auf Schulterhöhe heben?",
        "Können Sie mit gestrecktem Arm 500g (z.B. 2 Stück Butter) auf Schulterhöhe heben?",
        "Können Sie mit gestrecktem Arm 4kg (z.B. 4 1l-Milchpackungen) auf Schulterhöhe heben?",
        "Können Sie seitlich am Körper mit dem betroffenen Arm 10 kg (z.B. 10 1l-Milchpackungen) tragen?",
        "Glauben Sie, dass Sie einen Tennisball mit dem betroffenen Arm seitlich vom Körper 10m weit werfen können?",
        "Glauben Sie, dass Sie einen Tennisball mit dem betroffenen Arm über Kopf 20m weit werfen können?",
        "Können Sie mit dem betroffenen Arm die Rückseite der Gegenschulter waschen?",
        "Sind Sie von Seiten der Schulter her im erlernten Beruf voll arbeitsfähig?",
    )
    Assessment(
        id = "sst",
        name = "Simple Shoulder Test",
        abbreviation = "SST",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "SST, Stand 10/2026",
        hint = "Nicht validierte deutsche Version.\n\n" +
            "Sehr geehrter Patient/sehr geehrte Patientin, der folgende Fragebogen dient der Erfassung von Beschwerden und " +
            "Problemen, die durch Ihr Schultergelenk verursacht werden. Die dadurch gewonnenen Informationen werden uns helfen " +
            "einzuschätzen, wie es Ihnen mit Ihrem Schultergelenk geht und wie gut Sie in der Lage sind, Ihre alltäglichen " +
            "Aktivitäten zu verrichten. Bitte beantworten Sie jede Frage mit der Antwort, die Ihren Zustand während der " +
            "vergangenen Woche am besten beschreibt.\n\n" +
            "Die einzelnen Fragen werden mit „Ja“ oder „Nein“ beantwortet. Je mehr Fragen mit „Nein“ beantwortet werden, desto " +
            "größer ist die Symptomatik und/oder die Funktionseinschränkung. Für die Auswertung werden die mit „Ja“ " +
            "beantworteten Fragen durch die Gesamtanzahl der Fragen dividiert und mit 100% multipliziert. Ein höherer " +
            "Prozentwert ist daher ein Indikator für eine bessere Schulterfunktion (Angst et al. 2011).\n\n" +
            "Der SST wird am häufigsten bei Patienten mit Rotatorenmanschettenpathologien verwendet. Darüber hinaus ist er ein " +
            "valides Messinstrument bei Arthrosepatienten oder auch nach der Implantation einer Totalendoprothese (Hsu et al. " +
            "2017). Die Inter-Tester Reliabilität ist mit einem ICC von 0,97-0,99 als sehr gut zu bezeichnen (Angst et al. " +
            "2011). Die minimal messbare Veränderung (MDC 95%) beträgt 32,3% (Angst et al. 2011). Die minimal klinisch " +
            "relevante Veränderung beträgt 17,1-25% (Angst et al. 2011), bzw. 20% (Tashjian et al. 2016). Die beschriebene " +
            "deutsche Version ist eine nicht validierte, in vielen Promotionsarbeiten verwendete, Version (z.B. Seppel 2013).",
        sections = listOf(Section(items = listOf(choose("schulter", "Betroffene Schulter", "rechts", "links")) + items)),
        // Always of all twelve, one left out counting as a no, from one answer on (09.11.2026).
        scores = listOf(
            Score("ergebnis", "Ergebnis", ids(items), rule = ScoreRule.PercentOfAll, unit = "%", direction = Direction.HigherIsBetter, minAnswered = 1),
        ),
    )
}

/** The Walch-Duplay score of shoulder instability, as in "Walch-Duplay_10-2026.pdf". */
private val WALCH_DUPLAY: Assessment = run {
    val daily = ChoiceItem(
        "a", "A.) Alltagsaktivität",
        scored(
            "Rückkehr auf dasselbe Niveau in derselben Sportart / keine Beschwerden" to 25,
            "niedrigeres Niveau in derselben Sportart / leichte Beschwerden bei Bewegungen mit großer Anforderung an die Schulter" to 15,
            "Veränderung der Sportart / leichte Beschwerden bei einfachen Bewegungen" to 10,
            "Niveau reduziert und Sportart gewechselt oder Sportpartizipation beendet / starke Beschwerden" to 0,
        ),
    )
    val stability = ChoiceItem(
        "b", "B.) Stabilität",
        scored("keine Bedenken" to 25, "geringe Bedenken" to 15, "Gefühl der Instabilität" to 0, "Rezidiv - erneute (Sub-) Luxation" to -25),
    )
    val pain = ChoiceItem(
        "c", "C.) Schmerzen",
        scored(
            "kein Schmerz oder Schmerzen unter bestimmten klimatischen Bedingungen" to 25,
            "Schmerzen bei Bewegungen mit großer Anforderung an die Schulter oder bei Ermüdung" to 15,
            "Schmerzen im alltäglichen Leben" to 0,
        ),
    )
    val mobility = ChoiceItem(
        "d", "D.) Beweglichkeit",
        scored(
            "reine frontale Abduktion symmetrisch gegen eine Wand; Innenrotation eingeschränkt auf weniger als drei Wirbel; " +
                "Außenrotation (in 90° Abduktion) eingeschränkt auf weniger als 10% der gegenüberliegenden Seite" to 25,
            "reine frontale Abduktion gegen eine Wand < 150°; Innenrotation eingeschränkt auf weniger als drei Wirbel; " +
                "Außenrotation eingeschränkt auf weniger als 30% der gegenüberliegenden Seite" to 15,
            "reine frontale Abduktion gegen eine Wand < 120°; Innenrotation eingeschränkt auf weniger als sechs Wirbel; " +
                "Außenrotation eingeschränkt auf weniger als 50% der gegenüberliegenden Seite" to 5,
            "reine frontale Abduktion gegen eine Wand < 90°; Innenrotation eingeschränkt auf mehr als 6 Wirbel; " +
                "Außenrotation eingeschränkt auf mehr als 60% der gegenüberliegenden Seite" to 0,
        ),
    )
    val parts = listOf(daily, stability, pain, mobility)
    Assessment(
        id = "walch-duplay",
        name = "Walch-Duplay Score für Schulterinstabilität",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "Walch-Duplay, Stand 10/2026",
        hint = "Bitte vervollständigen Sie die Angaben und kreisen Zutreffendes ein.\n\nGesamt (/100): A + B + C + D",
        sections = listOf(
            Section(
                items = listOf(
                    TextItem("diagnose", "OP/Diagnose"),
                    choose(
                        "sportart", "Sportart",
                        "0: kein Sport",
                        "1: risikofrei – Leichtathletik, Rudern, Schwimmen, Brustschwimmen, Tauchen, Turnen, Skilanglauf, Schießen, Segeln",
                        "2: mit Kontakt – Kampfsport, Radfahren, Motorradfahren oder Radfahren, Klettern, Fußball, Rugby, Wasserski, " +
                            "Skifahren, Fallschirmspringen, Reiten",
                        "3: mit Armneigung – Klettern, Gewichtheben, Kugelstoßen, Überarm- und Schmetterlingsschwimmen, Stabhochsprung, " +
                            "Eiskunstlauf, Kanufahren, Golf, Hockey, Tennis, Baseball",
                        "4: hohes Risiko – Basketball, Handball, Volleyball, Drachenfliegen, Kajakfahren, Wasserpolo",
                    ),
                    choose("sportniveau", "Sportniveau", "Wettkampf", "Freizeit", "kein Sport"),
                    choose("seite", "Betroffene Seite: rechts/links", "rechts", "links"),
                    choose("dominanz", "Betroffene Seite: dominant/nicht dominant", "dominant", "nicht dominant"),
                    TextItem("abduktion", "Bewegungsausmaß: Abduktion"),
                    TextItem("innenrotation", "Bewegungsausmaß: Innenrotation"),
                    TextItem("aussenrotation", "Bewegungsausmaß: Außenrotation"),
                    TextItem("ar-90-abd", "Bewegungsausmaß: AR aus 90° Abd"),
                ),
            ),
            Section(items = parts),
        ),
        scores = listOf(
            Score(
                "gesamt", "Gesamt", ids(parts), direction = Direction.HigherIsBetter,
                bands = listOf(Band.upTo(50, "Schlecht"), Band.upTo(75, "Mittel"), Band.upTo(90, "Gut"), Band.upTo(100, "Exzellent")),
            ),
        ),
    )
}

/** Western Ontario Shoulder Instability Index, German version (Drerup et al. 2010), as in "…_WOSI_10-2026.pdf". */
private val WOSI: Assessment = run {
    fun scale(n: Int, text: String, least: String, most: String) = ScaleItem("$n", "$n. $text", 0, 10, least, most)
    val symptoms = listOf(
        scale(1, "Wie stark empfinden Sie Schmerzen in Ihrer Schulter bei Überkopftätigkeiten?", "Keine Schmerzen", "Extreme Schmerzen"),
        scale(2, "Wie stark empfinden Sie Schmerzen oder Klopfen in Ihrer Schulter?", "Keine Schmerzen/Kein Klopfen", "Extreme Schmerzen/Extremes Klopfen"),
        scale(3, "Wie stark empfinden Sie Schwäche oder Kraftverlust in Ihrer Schulter?", "Keine Schwäche", "Extreme Schwäche"),
        scale(4, "Wie stark empfinden Sie Ermüdung oder Mangel an Ausdauer in Ihrer Schulter?", "Keine Ermüdung", "Extreme Ermüdung"),
        // "Klein „Klicken“" in the original, a slip for "Kein".
        scale(5, "Wie stark empfinden Sie „Klicken“, Knacken oder Schnappen in Ihrer Schulter?", "Kein „Klicken“", "Extremes „Klicken“"),
        scale(6, "Wie stark empfinden Sie Steifheit in Ihrer Schulter?", "Keine Steifheit", "Extreme Steifheit"),
        scale(7, "Wie stark empfinden Sie Beschwerden in Ihrer Nackenmuskulatur aufgrund Ihrer Schulter?", "Keine Beschwerden", "Extreme Beschwerden"),
        scale(8, "Wie stark empfinden Sie Instabilität oder Lockerung in Ihrer Schulter?", "Keine Instabilität", "Extreme Instabilität"),
        scale(9, "Wie stark gleichen Sie mit anderen Muskeln Ihre Schulterproblematik aus?", "Gar nicht", "Extrem"),
        scale(10, "Wie stark ist der Verlust an Bewegungsumfang in Ihrer Schulter?", "Kein Verlust", "Extremer Verlust"),
    )
    val sport = listOf(
        scale(11, "In welchem Ausmaß hat Ihre Schulter die Teilnahme an Sport oder Freizeitaktivitäten eingeschränkt?", "Keine Einschränkung", "Extreme Einschränkung"),
        scale(
            12,
            "Wie stark hat Ihre Schulter die Ausführung bestimmter Bewegungen beeinflusst, die für Sie beim Sport oder bei der " +
                "Arbeit notwendig sind? (Wenn Ihre Schulter beide Bereiche, Sport und Arbeit, betrifft, beziehen Sie sich bitte auf " +
                "den am meisten betroffenen Bereich.)",
            "Kein Einfluss", "Extremer Einfluss",
        ),
        scale(13, "Wie stark empfinden Sie das Bedürfnis, Ihren Arm bei Tätigkeiten zu schützen?", "Gar nicht", "Extrem"),
        scale(14, "Wie stark empfinden Sie Schwierigkeiten beim Heben von schweren Gegenständen unter Schulterhöhe?", "Keine Schwierigkeiten", "Extreme Schwierigkeiten"),
    )
    val lifestyle = listOf(
        scale(15, "Wie stark ist Ihre Angst, auf Ihre Schulter zu stürzen?", "Keine Angst", "Extreme Angst"),
        scale(16, "Wie viel Schwierigkeiten haben Sie, Ihr gewünschtes Fitnessniveau aufrecht zu erhalten?", "Keine Schwierigkeiten", "Extreme Schwierigkeiten"),
        scale(17, "Wie viele Schwierigkeiten haben Sie beim „Toben oder Herumtollen“ mit der Familie oder Freunden?", "Keine Schwierigkeiten", "Extreme Schwierigkeiten"),
        scale(18, "Wie viele Schwierigkeiten haben Sie beim Schlafen aufgrund Ihrer Schulter?", "Keine Schwierigkeiten", "Extreme Schwierigkeiten"),
    )
    val emotions = listOf(
        scale(19, "Wie stark nehmen Sie Ihre Schulter wahr?", "Keine Wahrnehmung", "Extreme Wahrnehmung"),
        scale(20, "Wie besorgt sind Sie, dass sich Ihre Schulterproblematik verschlimmern könnte?", "Keine Sorge", "Extreme Sorge"),
        scale(21, "Wie stark ist das Gefühl der Frustration aufgrund Ihrer Schulter?", "Keine Frustration", "Extreme Frustration"),
    )
    Assessment(
        id = "wosi",
        name = "Western Ontario Shoulder Instability Index",
        abbreviation = "WOSI",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "WOSI, Stand 10/2026",
        hint = "Deutsche Fassung, nach Drerup et al. 2010.",
        sections = listOf(
            Section(
                "Abschnitt A: Körperliche Symptome",
                "Die folgenden Fragen beziehen sich auf körperliche Symptome, die aufgrund Ihrer Schulterproblematik aufgetreten " +
                    "sind. Geben Sie für alle Fragen die Stärke Ihrer Symptome in der vergangenen Woche an. Bitte markieren Sie die " +
                    "jeweils zutreffende Antwort auf der Skala von 0 – 10.",
                symptoms,
            ),
            Section(
                "Abschnitt B: Sport/Freizeit/Arbeit",
                "Der folgende Abschnitt bezieht sich darauf, wie Ihre Schulterproblematik Ihre Arbeit, Sport oder " +
                    "Freizeitaktivitäten in der vergangenen Woche beeinflusst hat. Bitte markieren Sie die Antwort für jede Frage " +
                    "auf der Skala von 0 bis 10.",
                sport,
            ),
            Section(
                "Abschnitt C: Lebensgewohnheiten",
                "Der folgende Abschnitt bezieht sich darauf, wie sehr Ihre Schulter Ihre Lebensgewohnheiten beeinflusst oder " +
                    "verändert hat. Bitte markieren Sie wieder die entsprechende Antwort für die vergangene Woche auf der Skala von 0 – 10.",
                lifestyle,
            ),
            Section(
                "Abschnitt D: Empfinden",
                "Die folgenden Fragen beziehen sich auf Ihr Empfinden in der vergangenen Woche mit Bezug auf Ihre " +
                    "Schulterproblematik. Bitte markieren Sie die entsprechende Antwort auf der Skala von 0 – 10.",
                emotions,
            ),
        ),
        // The original reckons nothing; a sum and one of each part, all needed, as asked on 09.11.2026.
        scores = listOf(
            lowerBetter("gesamt", "Gesamt", symptoms + sport + lifestyle + emotions),
            lowerBetter("a", "A: Körperliche Symptome", symptoms),
            lowerBetter("b", "B: Sport/Freizeit/Arbeit", sport),
            lowerBetter("c", "C: Lebensgewohnheiten", lifestyle),
            lowerBetter("d", "D: Empfinden", emotions),
        ),
    )
}

/**
 * The DASH, whose PDF "The-DASH-Outcome-Measure_10-2026.pdf" only links to the translations of the Institute for Work &
 * Health: taken from its German version, "DASH_German_2012.pdf", and its scoring, "Scoring_DASH_German.pdf" (09.11.2026).
 */
private val DASH: Assessment = run {
    fun answers(vararg labels: String) = labels.mapIndexed { i, label -> Option("${i + 1}", label, i + 1) }
    val difficulty = answers("Keine Schwierigkeiten", "Geringe Schwierigkeiten", "Mäßige Schwierigkeiten", "Erhebliche Schwierigkeiten", "Nicht möglich")
    val severity = answers("Keine", "Leichte", "Mäßige", "Starke", "Sehr starke")
    fun asked(from: Int, options: List<Option>, vararg texts: String) =
        texts.mapIndexed { i, text -> ChoiceItem("${from + i}", "${from + i}. $text", options) }
    val activities = asked(
        1, difficulty,
        "Ein neues oder festverschlossenes Glas öffnen", "Schreiben", "Einen Schlüssel umdrehen", "Eine Mahlzeit zubereiten",
        "Eine schwere Tür aufstoßen", "Einen Gegenstand über Kopfhöhe auf ein Regal stellen", "Schwere Hausarbeit (z. B Wände abwaschen, Boden putzen)",
        "Garten- oder Hofarbeit", "Betten machen", "Eine Einkaufstasche oder einen Aktenkoffer tragen", "Einen schweren Gegenstand tragen (über 5kg)",
        "Eine Glühbirne über Ihrem Kopf auswechseln", "Ihre Haare waschen oder fönen", "Ihren Rücken waschen", "Einen Pullover anziehen",
        "Ein Messer benutzen, um Lebensmittel zu schneiden", "Freizeitaktivitäten, die wenig körperliche Anstrengung verlangen (z. B. Karten spielen, Stricken, usw.)",
        "Freizeitaktivitäten, bei denen auf Ihren Arm, Schulter oder Hand Druck oder Stoß ausgeübt wird (z.B. Golf, Hämmern, Tennis, usw.)",
        "Freizeitaktivitäten, bei denen Sie Ihren Arm frei bewegen (z. B. Badminton, Frisbee)",
        "Mit Fortbewegungsmitteln zurecht zukommen (um von einem Platz zum anderen zu gelangen)", "Sexuelle Aktivität",
    )
    val social = ChoiceItem(
        "22",
        "22. In welchem Ausmaß haben Ihre Schulter-, Arm- oder Handprobleme Ihre normalen sozialen Aktivitäten mit Familie, " +
            "Freunden, Nachbarn oder anderen Gruppen während der vergangenen Woche beeinträchtigt? (Bitte kreuzen Sie die " +
            "entsprechende Zahl an)",
        answers("Überhaupt nicht", "Ein wenig", "Mäßig", "Ziemlich", "Sehr"),
    )
    val work = ChoiceItem(
        "23",
        "23. Waren Sie in der vergangenen Woche durch Ihre Schulter-, Arm- oder Handprobleme in Ihrer Arbeit oder anderen " +
            "alltäglichen Aktivitäten eingeschränkt? (Bitte kreuzen Sie die entsprechende Zahl an)",
        answers("Überhaupt nicht eingeschränkt", "Ein wenig eingeschränkt", "Mäßig eingeschränkt", "Sehr eingeschränkt", "Nicht möglich"),
    )
    val symptoms = asked(
        24, severity,
        "Schmerzen in Schulter, Arm oder Hand", "Schmerzen in Schulter, Arm oder Hand während der Ausführung einer bestimmten Tätigkeit",
        "Kribbeln (Nadelstiche) in Schulter, Arm oder Hand", "Schwächegefühl in Schulter, Arm oder Hand", "Steifheit in Schulter, Arm oder Hand",
    )
    val sleep = ChoiceItem(
        "29",
        "29. Wie groß waren Ihre Schlafstörungen in der letzten Woche aufgrund von Schmerzen im Schulter-, Arm- oder Handbereich? " +
            "(Bitte kreuzen Sie die entsprechende Zahl an)",
        difficulty,
    )
    val confidence = ChoiceItem(
        "30",
        "30. Aufgrund meiner Probleme im Schulter-, Arm- oder Handbereich empfinde ich meine Fähigkeiten als eingeschränkt, ich " +
            "habe weniger Selbstvertrauen oder ich fühle, dass ich mich weniger nützlich machen kann. (Bitte kreuzen Sie die " +
            "entsprechende Zahl an)",
        answers("Stimme überhaupt nicht zu", "Stimme nicht zu", "Weder Zustimmung noch Ablehnung", "Stimme zu", "Stimme sehr zu"),
    )
    fun module(prefix: String, vararg texts: String) =
        texts.mapIndexed { i, text -> ChoiceItem("$prefix${i + 1}", "${i + 1}. $text", difficulty) }
    val sportMusic = module(
        "sport",
        "In der üblichen Art und Weise Ihr Musikinstrument zu spielen oder Sport zu treiben?",
        "Aufgrund der Schmerzen in Schulter, Arm oder Hand Ihr Musikinstrument zu spielen oder Sport zu treiben?",
        "So gut Ihr Musikinstrument zu spielen oder Sport zu treiben wie Sie es möchten?",
        "Die bisher gewohnte Zeit mit dem Spielen Ihres Musikinstrumentes oder mit Sporttreiben zu verbringen?",
    )
    val job = module(
        "arbeit",
        "In der üblichen Art und Weise zu arbeiten?",
        "Aufgrund der Schmerzen in Schulter, Arm oder Hand Ihre übliche Arbeit zu erledigen?",
        "So gut zu arbeiten wie Sie es möchten?",
        "Die bisher gewohnte Zeit mit Ihrer Arbeit zu verbringen?",
    )
    val disability = activities + social + work + symptoms + sleep + confidence
    fun none(id: String, statement: String) = YesNoItem(id, statement)
    val ask = "Bitte kreuzen Sie die Zahl an, die Ihre körperlichen Fähigkeiten in der vergangenen Woche am besten beschreibt. " +
        "Hatten Sie irgendwelche Schwierigkeiten:"
    Assessment(
        id = "dash",
        name = "The DASH Outcome Measure",
        abbreviation = "DASH",
        categories = listOf(AssessmentCategory.UpperExtremity),
        source = "The DASH Outcome Measure, Stand 10/2026: DASH-Fragebogen, deutsche Version (DASH_German_2012) mit Auswertung (Scoring_DASH_German)",
        hint = "Disabilities of the Arm, Shoulder and Hand. © Institute for Work & Health 2006. All rights reserved. Deutsche " +
            "Version: Günter Germann, Angela Harth, Gerhard Wind, Erhan Demir. University of Heidelberg.\n\n" +
            "Dieser Fragebogen beschäftigt sich sowohl mit Ihren Beschwerden als auch mit Ihren Fähigkeiten, bestimmte " +
            "Tätigkeiten auszuführen. Bitte beantworten Sie alle Fragen gemäß Ihrem Zustand in der vergangenen Woche, indem Sie " +
            "einfach die entsprechende Zahl ankreuzen. Wenn Sie in der vergangenen Woche keine Gelegenheit gehabt haben, eine der " +
            "unten aufgeführten Tätigkeiten durchzuführen, so wählen Sie die Antwort aus, die Ihrer Meinung nach am ehesten " +
            "zutreffen würde. Es ist nicht entscheidend, mit welchem Arm oder welcher Hand Sie diese Tätigkeiten ausüben. " +
            "Antworten Sie Ihrer Fähigkeit entsprechend, ungeachtet, wie Sie die Aufgaben durchführen konnten.\n\n" +
            "DASH Wert für Behinderung/Symptome = [(Summe der n Antwortpunkte / n) - 1] x 25, wobei n der Anzahl der " +
            "beantworteten Fragen entspricht. Wurden mehr als 3 Fragen nicht beantwortet, so darf ein DASH Wert nicht berechnet " +
            "werden.\n\n" +
            "Auswertung der optionalen Module: Die Antwortpunkte der Fragen werden summiert; durch 4 (Anzahl der Fragen) " +
            "dividiert; 1 wird subtrahiert und danach mit 25 multipliziert. Für die Auswertung eines optionalen Moduls dürfen " +
            "keine Antworten fehlen.\n\n" +
            "Die Werte reichen von 0, keine Einschränkung (sehr gute Funktion), bis 100, hohe Einschränkung.",
        sections = listOf(
            Section(
                hint = "Bitte schätzen Sie Ihre Fähigkeit ein, wie Sie folgende Tätigkeiten in der vergangenen Woche durchgeführt " +
                    "haben, indem Sie die entsprechende Zahl ankreuzen.",
                items = activities + social + work,
            ),
            Section(
                hint = "Bitte schätzen Sie die Schwere der folgenden Symptome während der letzten Woche ein. (Bitte kreuzen Sie in " +
                    "jeder Zeile die entsprechende Zahl an)",
                items = symptoms + sleep + confidence,
            ),
            Section(
                "SPORT- UND MUSIK-MODUL (OPTIONAL)",
                "Die folgenden Fragen beziehen sich auf den Einfluss Ihres Schulter-, Arm- oder Handproblems auf das Spielen Ihres " +
                    "Musikinstrumentes oder auf das Ausüben Ihres Sports oder auf beides. Wenn Sie mehr als ein Instrument spielen " +
                    "oder mehr als eine Sportart ausüben (oder beides), so beantworten Sie bitte die Fragen in bezug auf das " +
                    "Instrument oder die Sportart, die für Sie am wichtigsten ist.\n\n$ask",
                listOf(
                    TextItem("sport-art", "Bitte geben Sie dieses Instrument bzw. diese Sportart hier an:"),
                    none("sport-keiner", "Ich treibe keinen Sport oder spiele kein Instrument (Sie können diesen Bereich auslassen)."),
                ) + sportMusic,
            ),
            Section(
                "ARBEITS- UND BERUFS-MODUL (OPTIONAL)",
                "Die folgenden Fragen beziehen sich auf den Einfluss Ihres Schulter-, Arm- oder Handproblems auf Ihre Arbeit " +
                    "(einschließlich Haushaltsführung, falls dies Ihre Hauptbeschäftigung ist).\n\n$ask",
                listOf(
                    TextItem("arbeit-art", "Bitte geben Sie Ihre/n Arbeit/Beruf hier an:"),
                    none("arbeit-keiner", "Ich bin nicht berufstätig (Sie können diesen Bereich auslassen)."),
                ) + job,
            ),
        ),
        scores = listOf(
            // [(sum / n) - 1] x 25 is the sum from its least, n, to its most, 5n, as 0 to 100; at most three left out.
            Score(
                "dash", "DASH Wert für Behinderung/Symptome", ids(disability), rule = ScoreRule.ZeroTo100,
                direction = Direction.LowerIsBetter, minAnswered = disability.size - 3,
            ),
            lowerBetter("sport", "DASH-Sport/Musik", sportMusic, rule = ScoreRule.ZeroTo100),
            lowerBetter("arbeit", "DASH-Arbeit", job, rule = ScoreRule.ZeroTo100),
        ),
    )
}
