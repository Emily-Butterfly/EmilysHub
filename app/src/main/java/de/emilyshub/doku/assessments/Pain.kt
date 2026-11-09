package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Schmerz und Psychosoziales", its first part, pain (#71), each as in its PDF of the
 * date in its source. None of them says what of a missing answer: each result needs all of its items (09.11.2026).
 */
internal val PAIN: List<Assessment> by lazy { listOf(CSI_GE, CSI_9, DN4, LANSS, GCPS, NPQ, MIDAS, OEREBRO, START_BACK, PCS, FESS) }

/** A sum of [items], better the [direction] way, all of them needed. */
private fun sum(id: String, name: String, items: List<Item>, direction: Direction = Direction.LowerIsBetter, bands: List<Band> = emptyList()) =
    ItemScore(id, name, ids(items), direction = direction, bands = bands)

/** The answers of the CSI-GE, "nie" 0 to "immer" 4. */
private val CSI_GE_OPTIONS = numbered("nie", "selten", "gelegentlich", "oft", "immer")

/** Central Sensitization Inventory, German version, as in "Central-Sensitization-Inventory_CSI-GE_09-2026.pdf". */
private val CSI_GE: Assessment = run {
    val partA = listOf(
        "Wenn ich morgens aufwache, fühle ich mich müde und nicht erholt.",
        "Meine Muskeln fühlen sich steif und schmerzhaft an.",
        "Ich habe Angstattacken.",
        "Ich knirsche oder beiße meine Zähne zusammen.",
        "Ich habe Probleme mit Durchfall und/oder Verstopfung.",
        "Ich brauche Hilfe bei der Verrichtung meiner Alltagstätigkeiten.",
        "Ich reagiere empfindlich auf helles Licht.",
        "Ich ermüde sehr schnell bei körperlichen Aktivitäten.",
        "Ich habe am ganzen Körper Schmerzen.",
        "Ich habe Kopfschmerzen.",
        "Meine Blase fühlt sich unangenehm an und/oder ich habe Brennen beim Wasserlassen.",
        "Ich schlafe nicht gut.",
        "Ich habe Konzentrationsschwierigkeiten.",
        "Ich habe Hautprobleme, wie z.B. trockene oder juckende Haut oder Hautausschlag.",
        "Stress verstärkt meine körperlichen Beschwerden.",
        "Ich fühle mich traurig oder niedergeschlagen.",
        "Ich habe wenig Energie.",
        "Ich habe Muskelverspannungen im Nacken- und Schulterbereich.",
        "Ich habe Kieferschmerzen.",
        "Mir wird von manchen Gerüchen, wie z.B. Parfüm, schwindelig und übel.",
        "Ich muss häufig Wasserlassen.",
        "Meine Beine fühlen sich unangenehm und ruhelos an, wenn ich versuche nachts einzuschlafen.",
        "Ich habe Schwierigkeiten, mich an Dinge zu erinnern.",
        "Ich erlitt als Kind traumatische Erlebnisse.",
        "Ich habe Schmerzen im Beckenbereich.",
    ).mapIndexed { i, text -> ChoiceItem("a${i + 1}", "${i + 1}. $text", CSI_GE_OPTIONS) }
    // Part B has no points; the year of each diagnosis asked once it is given.
    val partB = listOf(
        "Restless-Legs-Syndrom (Syndrom der unruhigen Beine)",
        "Chronisches Erschöpfungssyndrom (Chronisches Fatigue Syndrom)",
        "Fibromyalgie",
        "Kiefergelenks-Funktionsstörung (Craniomandibuläre Dysfunktion)",
        "Migräne oder Spannungskopfschmerz",
        "Reizdarmsyndrom (Colon irritabile)",
        "Unverträglichkeit gegen verschiedene chemische Substanzen (Multiple Chemical Sensitivity)",
        "Nackenverletzung (einschließlich Schleudertrauma)",
        "Angst- oder Panikattacken",
        "Depression",
    ).flatMapIndexed { i, text ->
        val id = "b${i + 1}"
        listOf(YesNoItem(id, "${i + 1}. $text"), NumberItem("$id-jahr", "Jahr der Diagnosestellung", showIf = Condition.ifYes(id)))
    }
    Assessment(
        id = "csi-ge",
        name = "Central Sensitization Inventory",
        abbreviation = "CSI-GE",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "CSI-GE, Stand 09/2026",
        hint = "German version (CSI-GE), Zentraler Sensibilisierungsfragebogen.\n\n" +
            "Das CSI wurde entwickelt, um die verschiedenen gesundheitsrelevanten Dimensionen bzw. mit zentraler " +
            "Sensibilisierung assoziierten Begleiterkrankungen zu evaluieren.\n\n" +
            "Der Teil A hat 25 Items, jedes wird mit einem Score von 0-4 bewertet (0 entspricht einer geringen " +
            "Beeinträchtigung, 4 entspricht einer hohen Beeinträchtigung). Aus den vergebenen Punkten wird ein Gesamtscore " +
            "berechnet (Summation aller Werte). Werte >/=40 sind mit einer zentralen Sensibilisierung gleichzusetzen.\n\n" +
            "Der Teil B wird nicht mit Punkten bewertet und fließt dementsprechend nicht in den Gesamtscore mit ein.",
        sections = listOf(
            Section("Teil A", "Bitte kreuzen Sie bei jeder Aussage die für Sie aktuell am besten passende Antwort an.", partA),
            Section(
                "Teil B",
                "Hat ein Arzt/eine Ärztin bei Ihnen eine der folgenden Diagnosen gestellt? Bitte kreuzen Sie auf der rechten " +
                    "Seite die passende Antwort zu jeder ärztlichen Diagnose an und geben Sie das Jahr an in dem die Diagnose " +
                    "gestellt wurde.",
                partB,
            ),
        ),
        scores = listOf(
            sum("gesamt", "Gesamtsumme", partA, bands = listOf(Band.upTo(39, null), Band.upTo(100, "Mit einer zentralen Sensibilisierung gleichzusetzen (>/=40)"))),
        ),
    )
}

/** Central Sensitization Inventory, short version, as in "Central-Sensitization-Inventory-Kurzversion_CSI-9_09-2026.pdf". */
private val CSI_9: Assessment = run {
    val options = numbered("nie", "selten", "manchmal", "oft", "immer")
    val items = listOf(
        "Ich fühle mich müde und nicht erholt, wenn ich morgens aufwache.",
        "Meine Muskeln fühlen sich steif an und sind schmerzhaft.",
        "Mein ganzer Körper tut weh.",
        "Ich habe Kopfschmerzen.",
        "Es fällt mir schwer, mich zu konzentrieren.",
        "Stress verstärkt meine körperlichen Beschwerden.",
        "Ich habe Hautprobleme wie Trockenheit, Juckreiz oder Ausschläge.",
        "Ich verspüre Muskelverspannungen im Nacken- oder Schulterbereich.",
        // "Schweirigkeiten" in the original, a slip mended.
        "Ich habe Schwierigkeiten mich an manches zu erinnern.",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", options) }
    Assessment(
        id = "csi-9",
        name = "Central Sensitization Inventory-Kurzversion",
        abbreviation = "CSI-9",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "CSI-9, Stand 09/2026",
        hint = "Kurzversion, CSI-9, nicht-validierte deutsche Version.\n\n" +
            "Bitte kreuzen Sie bei jeder Aussage die für Sie aktuell am besten passende Antwort an.\n\n" +
            // "Nigshigima" in the original, the Nishigami of its sources.
            "Schweregrade: 0 bis 9 leicht, 10 bis 19 moderat, 20 bis 36 stark (Nishigami et al. 2018).\n\n" +
            "Cut off von 20 Punkten diskriminiert Patienten mit zentraler Sensibilisierung (Fibromyalgie) und Patienten mit " +
            "MSK-Beschwerden. Ein Wert von mehr als 20 Punkten ist ein Hinweis für eine zentrale Sensibilisierung. Patienten " +
            "unter 20 Punkten zeigen Hinweise auf mehr muskuloskelettale Beschwerden (Sensitivität: 92.3%, Spezifität 93.3%, " +
            "AUC von 0.979, Tanaka et al. 2019).",
        sections = listOf(Section(items = items)),
        scores = listOf(
            sum("gesamt", "Gesamtsumme", items, bands = listOf(Band.upTo(9, "leicht"), Band.upTo(19, "moderat"), Band.upTo(36, "stark"))),
        ),
    )
}

/** Douleur Neuropathique en 4 Questions, as in "Douleur-Neuropathique-en-4-Questions_DN4-Fragebogen_09-2026.pdf". */
private val DN4: Assessment = run {
    val character = yesNoItems("Brennen", "Gefühl einer schmerzhaften Kälte", "Elektrische Schocks")
    val along = yesNoItems("Kribbeln", "Pieksen", "Taubheitsgefühl", "Juckreiz", first = 4)
    // Question 3 asks for what the examination shows, the others the patient.
    val examined = yesNoItems("Hypoästhesie bei Berührung", "Hypoästhesie bei Nadelreizen", first = 8)
    val rubbing = yesNoItems("Reiben", first = 10)
    val items = character + along + examined + rubbing
    Assessment(
        id = "dn4",
        name = "DN4-Fragebogen",
        abbreviation = "DN4",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "DN4-Fragebogen, Stand 09/2026",
        hint = "Deutsche validierte Version (Van Seventer R. et al 2010).\n\n" +
            "Beantworten Sie bitte die folgenden vier Fragen. Kreuzen Sie zu jedem Punkt nur eine Antwort an.\n\n" +
            "Auswertung des Scores: Ja: 1 Punkt, Nein: 0 Punkte. Ergebnis ist größer oder gleich 4/10: Hinweis auf " +
            "neuropathischen Schmerz; Ergebnis ist kleiner 4/10: eher kein neuropathischer Schmerz.",
        sections = listOf(
            // "Weißt" in the original, a slip mended.
            Section("Frage 1: Weist der Schmerz eines oder mehrere der folgenden Merkmale auf?", items = character),
            Section("Frage 2: Treten die folgenden Beschwerden zusammen mit den Schmerzen im selben Körperbereich auf?", items = along),
            Section("Frage 3: Sind die Schmerzen in einem Bereich lokalisiert, in dem die körperliche Untersuchung Folgendes zeigt?", items = examined),
            // "aufgelöst" in the original, a slip for "ausgelöst" (09.11.2026).
            Section("Frage 4: Werden die Schmerzen ausgelöst oder verschlimmert durch:", items = rubbing),
        ),
        scores = listOf(
            sum(
                "gesamt", "Ergebnis", items,
                bands = listOf(Band.upTo(3, "eher kein neuropathischer Schmerz"), Band.upTo(10, "Hinweis auf neuropathischen Schmerz")),
            ),
        ),
    )
}

/** Leeds Assessment of Neuropathic Symptoms and Signs, as in "Leeds-Assessment-of-Neuropathic-Symptoms-and-Signs_LANSS_09-2026.pdf". */
private val LANSS: Assessment = run {
    fun question(id: String, text: String, no: String, yes: String, points: Int) = ChoiceItem(id, "$id. $text", scored(no to 0, yes to points))
    val items = listOf(
        question(
            "1", "In dem Gebiet, in dem Sie die Schmerzen haben, verspüren Sie dort auch ungewöhnliche Gefühlsstörungen wie " +
                "Ameisenlaufen, Kribbeln oder ein Gefühl wie Nadelstiche?",
            "NEIN – Ich verspüre keine Gefühlsstörungen in dem Gebiet.", "JA – Ich habe diese Gefühlsstörungen oft.", 5,
        ),
        question(
            "2", "Verändert sich die Hautfarbe im schmerzhaften Gebiet (sieht sie vielleicht röter aus oder marmoriert/ " +
                "gefleckt), wenn der Schmerz besonders stark ist?",
            "NEIN – Die Schmerzen beeinflussen die Hautfarbe nicht.",
            "JA – Mir ist aufgefallen, dass die Schmerzen dazu führen, dass die Hautfarbe anders aussieht als normal.", 5,
        ),
        // "im betroffen Gebiet" in the original, a slip mended.
        question(
            "3", "Reagiert die Haut im betroffenen Gebiet durch die Schmerzen ungewöhnlich empfindlich auf Berührung? Es kann " +
                "zum Beispiel sein, dass leichte Berührung der Haut zu unangenehmen Gefühlen oder Schmerzen führt.",
            "NEIN – Die Schmerzen machen meine Haut im betroffenen Gebiet nicht ungewöhnlich empfindlich auf Berührung.",
            "JA – Meine Haut im betroffenen Gebiet ist besonders empfindlich auf Berührung.", 3,
        ),
        question(
            "4", "Kommen Ihre Schmerzen plötzlich und ohne erkennbaren Grund, wenn Sie sich nicht bewegen? Beschreibungen für " +
                "diese Art von Schmerz könnten z.B. sein: „anfallsartig“, „wie ein Stromschlag“ oder „vor Schmerz aufspringend“.",
            "NEIN – Mein Schmerz fühlt sich eigentlich nicht so an.", "JA – Diese Art von Empfindung habe ich oft.", 2,
        ),
        question(
            "5", "Fühlt sich in dem Gebiet, in dem Sie Schmerzen haben, die Haut ungewöhnlich heiß an, wie ein brennender Schmerz?",
            "NEIN – Ich habe keinen brennenden Schmerz.", "JA – Ich habe oft brennenden Schmerz.", 1,
        ),
        // Questions 6 and 7 test the skin, as an examination would.
        question(
            "6", "Reiben Sie vorsichtig eine Stelle im betroffenen, schmerzhaften Gebiet (das Sie oben angegeben haben) mit " +
                "Ihrem Zeigefinger und reiben Sie dann ebenso eine Stelle in einem nicht-betroffenen Gebiet (z.B. eine Stelle " +
                "weiter weg vom betroffenen Gebiet oder auf der gegenüberliegenden Seite des betroffenen Gebiets). Wie fühlt " +
                "sich dieses Reiben im betroffenen Gebiet an?",
            "Die Stelle im schmerzhaften Gebiet fühlt sich nicht anders an als die im nicht-schmerzhaften Gebiet.",
            "Die Stelle im schmerzhaften Gebiet fühlt sich unangenehm an, wie Nadelstiche, Kribbeln oder Brennen, das Gefühl " +
                "unterscheidet sich von der Stelle im nicht-schmerzhaften Gebiet.",
            5,
        ),
        // "auf die nicht-betroffenen Stelle" in the original, a slip mended.
        question(
            "7", "Drücken Sie vorsichtig mit Ihrer Fingerspitze auf eine Stelle im betroffenen Gebiet und drücken Sie dann auf " +
                "die gleiche Art auf die nicht-betroffene Stelle. Wie fühlt sich dieses Drücken im betroffenen Gebiet an?",
            "Die Stelle im schmerzhaften Gebiet fühlt sich nicht anders an als die im nicht-schmerzhaften Gebiet.",
            "Ich spüre eine Taubheit oder Empfindlichkeit im schmerzhaften Gebiet, die anders ist als im nicht-schmerzhaften Gebiet.",
            3,
        ),
    )
    Assessment(
        id = "lanss",
        name = "Leeds Assessment of Neuropathic Symptoms and Signs",
        abbreviation = "LANSS",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "LANSS, Stand 09/2026",
        hint = "Nicht validierte Version, eigene Übersetzung.\n\n" +
            "Mit den nachfolgenden Fragen möchten wir untersuchen, wie Sie Ihre Schmerzen spüren.\n" +
            "• Bitte denken Sie darüber nach, wie sich die Schmerzen über die letzte Woche angefühlt haben. Bitte markieren Sie " +
            "diejenigen Antworten, die am besten Ihre Schmerzen beschreiben.\n" +
            "• Unabhängig davon, wie stark Ihre Schmerzen sind, können die Beschreibungen zutreffen oder nicht zutreffen.\n" +
            "• Kreuzen Sie bitte nur die Antworten an, die Ihre Schmerzen beschreiben.\n\n" +
            // "Es wir empfohlen" in the original, a slip mended.
            "Für die Auswertung wird ca. 10 Minuten benötigt. Der Score kann zwischen 0-24 Punkten betragen. Ein Score von " +
            ">=12 steht für eine neuropathische Schmerzursache. Es wird empfohlen, das Gesamtergebnis zu verwenden. Einzelne " +
            "Items sind nicht aussagekräftig.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            sum("gesamt", "Score", items, bands = listOf(Band.upTo(11, null), Band.upTo(24, "Neuropathische Schmerzursache (>=12)"))),
        ),
    )
}

/**
 * Graded Chronic Pain Scale, after Türp & Nilges 2000, as in "Graded-Chronic-Pain-Scale_GCPS_09-2026.pdf". Its means times
 * 10 fall in its bands as they are, without rounding: below 30 is 0 BP, below 50 a low intensity (09.11.2026).
 */
private val GCPS: Assessment = run {
    // The days of six months, 184 at the most.
    val days = NumberItem("1", "Frage 1: An ungefähr wie vielen Tagen konnten Sie in den letzten 6 Monaten aufgrund Ihrer Schmerzen Ihren normalen Beschäftigungen (Beruf, Schule/Studium, Hausarbeit) nicht nachgehen?", unit = "Tage", max = 184)
    fun pain(id: String, text: String) = ScaleItem(id, "Frage $id: $text", 0, 10, "keine Schmerzen", "stärkster vorstellbarer Schmerz")
    fun impairment(id: String, text: String) = ScaleItem(id, "Frage $id: $text", 0, 10, "keine Beeinträchtigung", "ich war außerstande, irgendetwas zu tun")
    val intensity = listOf(
        pain("2", "Wie würden Sie Ihre Schmerzen, wie sie in diesem Augenblick sind, einstufen?"),
        pain("3", "Wenn Sie an die Tage denken, an denen Sie in den letzten 6 Monaten Schmerzen hatten, wie würden Sie Ihre stärksten Schmerzen einstufen?"),
        pain("4", "Wenn Sie an die Tage denken, an denen Sie in den letzten 6 Monaten Schmerzen hatten, wie würden Sie die durchschnittliche Stärke Ihrer Schmerzen einstufen?"),
    )
    val disability = listOf(
        // "Inwieweit haben Sie Ihre Schmerzen" as in the original.
        impairment("5", "Inwieweit haben Sie Ihre Schmerzen in den letzten 6 Monaten bei Ihren alltäglichen Beschäftigungen beeinträchtigt?"),
        impairment("6", "Inwieweit haben Ihre Schmerzen in den letzten 6 Monaten Ihre Fähigkeit beeinträchtigt, an Familien- und Freizeitaktivitäten teilzunehmen?"),
        impairment("7", "Inwieweit haben Ihre Schmerzen in den letzten 6 Monaten Ihre Fähigkeit beeinträchtigt, Ihre Arbeit/Hausarbeit zu verrichten?"),
    )
    /** Beeinträchtigungspunkte 0 to 3 by score [of], the next from each of whole [bounds] on. */
    fun points(id: String, name: String, of: String, vararg bounds: Int) = GradedScore(
        id, name, unit = "BP", direction = Direction.LowerIsBetter,
        grades = bounds.mapIndexed { i, below -> Grade(i, Bound.below(of, below)) } + Grade(bounds.size),
    )
    Assessment(
        id = "gcps",
        name = "Graded Chronic Pain Scale",
        abbreviation = "GCPS",
        categories = listOf(AssessmentCategory.PainPsychosocial, AssessmentCategory.JawCmd),
        source = "GCPS, Stand 09/2026",
        hint = "Türp JC, Nilges P. Diagnostik von Patienten mit orofazialen Schmerzen - Die deutsche Version des „Graded Chronic " +
            "Pain Status“ in: Quintessenz. 2000; 51: 721-27.\n\n" +
            "Bitte beantworten Sie die folgenden sieben Fragen betreffend Ihrer Hauptschmerzen!\n\n" +
            "Beeinträchtigungspunkte (BP): Frage 1, Anzahl der Tage: 0-6 Tage = 0 BP, 7-14 Tage = 1 BP, 15-30 Tage = 2 BP, über " +
            "30 Tage = 3 BP (a). Fragen 5 + 6 + 7, subjektive Beeinträchtigung: Summe / 3 x 10 = Punkte; 0-29 = 0 BP, 30-49 = " +
            "1 BP, 50-69 = 2 BP, > 69 = 3 BP (b). Gesamtbeeinträchtigung: a + b.\n\n" +
            "Charakteristische Schmerzintensität: Fragen 2 + 3 + 4, Summe / 3 x 10.\n\n" +
            "Geringe Beeinträchtigung, funktionaler chronischer Schmerz: Grad I, geringe Schmerzintensität (weniger als 3 BP " +
            "und < 50 Schmerzintensität); Grad II, hohe Schmerzintensität (weniger als 3 BP und >= 50 Schmerzintensität). " +
            "Starke Beeinträchtigung, dysfunktionaler chronischer Schmerz (eine psychologische Schmerztherapie ist notwendig): " +
            "Grad III, mäßige Einschränkung (3-4 BP); Grad IV, hochgradige Einschränkung (5-6 BP), unabhängig von " +
            "Schmerzintensität.",
        sections = listOf(
            Section(items = listOf(TextItem("hauptschmerzen", "Hauptschmerzen"), days)),
            Section(
                hint = "In den folgenden Fragen 2 bis 4 geht es um die Stärke Ihrer Schmerzen. Sie können Ihre Schmerzen jeweils " +
                    "auf einer Skala von 0 bis 10 bewerten: Der Wert 0 bedeutet, dass Sie keine Schmerzen haben. Der Wert 10 " +
                    "bedeutet, dass Ihre Schmerzen nicht schlimmer sein könnten. Mit den dazwischenliegenden Werten können Sie " +
                    "Abstufungen vornehmen.",
                items = intensity,
            ),
            Section(
                hint = "Bei den folgenden Fragen 5 bis 7 geht es um die Beeinträchtigung Ihrer Aktivitäten durch Schmerzen. Sie " +
                    "können Ihre Beeinträchtigung jeweils auf einer Skala von 0 bis 10 bewerten: Der Wert 0 bedeutet, dass Sie " +
                    "keine Beeinträchtigung haben. Der Wert 10 bedeutet, dass Sie außerstande waren/sind, irgendetwas zu tun. " +
                    "Mit den dazwischenliegenden Werten können Sie Abstufungen vornehmen.",
                items = disability,
            ),
        ),
        scores = listOf(
            ItemScore("tage", "Anzahl der Tage", listOf(days.id), unit = "Tage", direction = Direction.LowerIsBetter),
            points("bp-tage", "Beeinträchtigungspunkte Tage (a)", "tage", 7, 15, 31),
            // Of 30 in percent: the mean times 10.
            ItemScore("beeintraechtigung", "Subjektive Beeinträchtigung", ids(disability), rule = ScoreRule.Percent, direction = Direction.LowerIsBetter),
            points("bp-beeintraechtigung", "Beeinträchtigungspunkte subjektiv (b)", "beeintraechtigung", 30, 50, 70),
            WeightedScore("bp", "Gesamtbeeinträchtigung", unit = "BP", direction = Direction.LowerIsBetter, parts = listOf("bp-tage", "bp-beeintraechtigung")),
            ItemScore("intensitaet", "Charakteristische Schmerzintensität", ids(intensity), rule = ScoreRule.Percent, direction = Direction.LowerIsBetter),
            GradedScore(
                "grad", "Grad", unit = "", direction = Direction.LowerIsBetter,
                grades = listOf(
                    Grade(1, Bound.below("bp", 3), Bound.below("intensitaet", 50)),
                    Grade(2, Bound.below("bp", 3)),
                    Grade(3, Bound.below("bp", 5)),
                    Grade(4),
                ),
                bands = listOf(
                    Band.upTo(1, "Grad I: Geringe Schmerzintensität, funktionaler chronischer Schmerz"),
                    Band.upTo(2, "Grad II: Hohe Schmerzintensität, funktionaler chronischer Schmerz"),
                    Band.upTo(3, "Grad III: Mäßige Einschränkung, dysfunktionaler chronischer Schmerz"),
                    Band.upTo(4, "Grad IV: Hochgradige Einschränkung, dysfunktionaler chronischer Schmerz"),
                ),
            ),
        ),
    )
}

/** Neurophysiology of Pain Questionnaire, German version (Richter et al. 2019), as in "Neurophysiology-of-Pain-Questionnaire_09-2026.pdf". */
private val NPQ: Assessment = run {
    /** Statement [n], a point for "richtig" if [right], else for "falsch". */
    fun statement(n: Int, text: String, right: Boolean) = ChoiceItem(
        "$n", "$n. $text",
        listOf(Option("r", "richtig (R)", if (right) 1 else 0), Option("f", "falsch (F)", if (right) 0 else 1), Option("u", "unentschlossen (U)", 0)),
    )
    val right = setOf(3, 5, 6, 12)
    val items = listOf(
        "Wenn ein Körperteil von Ihnen verletzt ist, übermitteln spezielle Schmerzrezeptoren die Schmerzmeldung ins Gehirn.",
        "Schmerz tritt nur dann auf, wenn Sie verletzt sind oder ein Verletzungsrisiko besteht.",
        "Spezielle Nerven in Ihrem Rückenmark senden Gefahrenmeldungen ans Gehirn.",
        "Schmerz tritt immer dann auf, wenn Sie verletzt sind.",
        "Das Gehirn entscheidet, wann Sie Schmerzen empfinden.",
        "Nerven passen sich an, indem sie ihre Reizbarkeit erhöhen.",
        "Anhaltende Schmerzen bedeuten, dass eine Verletzung nicht richtig ausgeheilt ist.",
        "Schlimmere Verletzungen bewirken immer auch stärkere Schmerzen.",
        "Absteigende Neurone sind immer hemmend.",
        "Eine identische Verletzung wird, unabhängig von der Umgebung, in der sie stattfindet, immer die gleichen Schmerzen verursachen.",
        "Sie können Schmerzen haben, ohne es zu merken.",
        "Wenn Sie verletzt sind, übermitteln spezielle Rezeptoren die Gefahrenmeldung ans Rückenmark.",
    ).mapIndexed { i, text -> statement(i + 1, text, i + 1 in right) }
    Assessment(
        id = "npq",
        name = "Neurophysiology of Pain Questionnaire",
        abbreviation = "NPQ-D",
        categories = listOf(AssessmentCategory.PainPsychosocial, AssessmentCategory.JawCmd),
        source = "Neurophysiology of Pain Questionnaire, Stand 09/2026",
        hint = "Validierte deutsche Version (Richter et al. 2019).\n\n" +
            "Sehr geehrte/r Patient/in, in diesem Fragebogen lesen Sie einige Aussagen zum Thema Schmerz. Sie haben die " +
            "Möglichkeit richtig (R), falsch (F) oder unentschlossen (U) anzukreuzen. Bitte machen Sie ein Kreuz pro Aussage.\n\n" +
            "Der NPQ-D Fragebogen kann grundsätzlich bei Schmerzpatienten verwendet werden, um den Effekt (Lernerfolg) einer " +
            "Schmerzedukation oder den allgemeinen Wissensstand über das Thema Schmerz-Neurophysiologie zu überprüfen.\n\n" +
            "Der Fragebogen kann vom Patienten eigenständig ausgeführt werden und der Zeiteinsatz sollte auf 5-10 Minuten " +
            "veranschlagt werden. Die Auswertung beträgt höchstens 2 Minuten.\n\n" +
            "Der Gesamtscore beträgt 12 Punkte für ein maximal gutes Ergebnis. Der schlechteste Wert und damit eine starkes " +
            "Wissensdefizit liegen bei 0 Punkten. Richtig sind die Fragen: 3,5,6,12. Falsch sind die Fragen: " +
            "1,2,4,7,8,9,10,11.\n\nGeschulte Patienten sollten ca. 60% der Fragen korrekt beantworten.",
        sections = listOf(Section("Fragebogen zur Neurophysiologie von Schmerzen", items = items)),
        scores = listOf(sum("gesamt", "Ergebnis", items, direction = Direction.HigherIsBetter)),
    )
}

/** Migraine Disability Assessment, revised German version (Agosti 2008), as in "Migraine-Disability-Assessment_MIDAS_09-2026.pdf". */
private val MIDAS: Assessment = run {
    // The days of three months, 92 at the most.
    fun days(id: String, text: String) = NumberItem(id, "$id. $text", unit = "Tage", max = 92)
    val items = listOf(
        days("1", "An wie vielen Tagen in den letzten drei Monaten sind Sie wegen Kopfschmerzen nicht zur Arbeit gegangen oder haben in der Schule gefehlt?"),
        days("2", "An wie vielen Tagen in den letzten drei Monaten war Ihre Leistungsfähigkeit am Arbeitsplatz oder in der Schule um die Hälfte oder mehr eingeschränkt? (Zählen Sie die Tage, die Sie bei Frage 1 angaben, NICHT dazu)"),
        days("3", "An wie vielen Tagen in den letzten drei Monaten konnten Sie wegen Ihrer Kopfschmerzen keine Hausarbeit verrichten?"),
        days("4", "An wie vielen Tagen in den letzten drei Monaten war Ihre Leistungsfähigkeit im Haushalt um die Hälfte oder mehr eingeschränkt? (Zählen Sie die Tage, die Sie bei Frage 3 angaben, NICHT dazu.)"),
        days("5", "An wie vielen Tagen in den letzten drei Monaten haben Sie an familiären, sozialen oder Freizeitaktivitäten wegen Ihrer Kopfschmerzen nicht teilnehmen können?"),
    )
    val apart = listOf(
        NumberItem("a", "A An wie vielen Tagen in den letzten drei Monaten hatten Sie Kopfschmerzen? (Wenn die Kopfschmerzen länger als einen Tag angehalten haben, zählen Sie jeden Tag)", unit = "Tage"),
        ScaleItem("b", "B Wie stark waren die Kopfschmerzen? Bitte geben Sie die Schmerzintensität auf einer Skala 0–10 an (0 = keine Schmerzen, 10 = unerträgliche Schmerzen)", 0, 10, "keine Schmerzen", "unerträgliche Schmerzen"),
    )
    Assessment(
        id = "midas",
        name = "Migraine Disability Assessment",
        abbreviation = "MIDAS",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "MIDAS, Stand 09/2026",
        hint = "Revidierte deutsche Version, Agosti 2008.\n\n" +
            "Bitte beantworten Sie die folgenden Fragen über ALLE Kopfschmerzattacken, die Sie in den letzten drei Monaten " +
            "hatten. Füllen Sie das Kästchen neben jeder Frage mit der entsprechenden Zahl aus. Wenn Sie eine Tätigkeit in den " +
            "vergangenen 3 Monaten nicht ausgeführt haben, tragen Sie eine Null ein.\n\n" +
            "Bitte zählen Sie die Tage der Fragen 1-5 zusammen, sobald Sie den Fragebogen vollständig ausgefüllt haben (die " +
            "Fragen A und B bitte nicht dazu zählen).",
        sections = listOf(Section(items = items), Section(items = apart)),
        scores = listOf(
            sum(
                "gesamt", "Ergebnis", items,
                bands = listOf(
                    Band.upTo(5, "Grad I: Weniger oder keine Beeinträchtigung"),
                    Band.upTo(10, "Grad II: Geringe Beeinträchtigung"),
                    Band.upTo(20, "Grad III: Mäßige Beeinträchtigung"),
                    Band.upTo(5 * 92, "Grad IV: Schwere Beeinträchtigung"),
                ),
            ),
        ),
    )
}

/** Örebro Musculoskeletal Pain Screening Questionnaire, short version (Linton et al.), as in "Oerebro-Musculoskeletal-Pain-Screening-Questionnaire_09-2026.pdf". */
private val OEREBRO: Assessment = run {
    fun scale(n: Int, text: String, least: String, most: String, reversed: Boolean = false) =
        ScaleItem("$n", "$n. $text", 0, 10, least, most, reversed = reversed)
    val duration = ChoiceItem(
        "1", "1. Wie lange haben Sie Ihr aktuelles Schmerzproblem schon? Kreuzen Sie bitte nur eine Antwort an.",
        listOf("0-1 Wo.", "2 Wo.", "3-4 Wo.", "4-5 Wo.", "6-8 Wo.", "9-11 Wo.", "3-6 Mon.", "6-9 Mon.", "9-12 Mon.", "über 1 Jahr")
            .mapIndexed { i, label -> Option("$i", label, i + 1) },
    )
    val opinion = "ich bin ganz anderer Meinung" to "ich stimme komplett zu"
    val items = listOf(
        duration,
        scale(2, "Wie stark bewerten Sie den Schmerz, den Sie in der letzten Woche hatten?", "kein Schmerz", "größtmöglicher Schmerz"),
        // 3, 4 and 8 count 10 minus the number circled.
        scale(3, "Ich kann eine Stunde leichte Arbeiten durchführen.", "überhaupt nicht", "ohne Probleme", reversed = true),
        scale(4, "Ich kann nachts schlafen.", "überhaupt nicht", "ohne Probleme", reversed = true),
        scale(5, "Wie angespannt oder ängstlich fühlten Sie sich in der letzten Woche?", "absolut ruhig und entspannt", "so angespannt und ängstlich wie nie"),
        scale(6, "Wie sehr fühlten Sie sich in der letzten Woche bedrückt/deprimiert?", "überhaupt nicht", "sehr stark"),
        scale(7, "Wie groß ist aus Ihrer Sicht das Risiko, dass Ihr aktueller Schmerz bleiben wird?", "keine Gefahr", "sehr große Gefahr"),
        scale(8, "Wie hoch schätzen Sie die Chance ein, in 3 Monaten Ihren normalen Pflichten wieder nachkommen zu können?", "keine Chance", "sehr große Wahrscheinlichkeit", reversed = true),
        scale(9, "Wenn der Schmerz stärker wird, sollte ich mit dem, was ich gerade tue aufhören bis der Schmerz nachlässt?", opinion.first, opinion.second),
        scale(10, "Mit meinen gegenwärtigen Schmerzen sollte ich meine normale Arbeit nicht tun.", opinion.first, opinion.second),
    )
    Assessment(
        id = "oerebro",
        name = "Örebro Musculoskeletal Pain Screening Questionnaire",
        abbreviation = "ÖMPSQ",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "Örebro Musculoskeletal Pain Screening Questionnaire, Stand 09/2026",
        hint = "Kurzversion, nicht validierte deutsche Version (Linton et al. 2010).\n\n" +
            "Die Kurzversion des ÖMPSQ enthält 10 aus der Vollversion ausgewählte Artikel (siehe Linton, Nicholas & MacDonald, " +
            "2011). Diese Items werden mit 0-10 bewertet, wobei 0 für das Fehlen einer Beeinträchtigung und 10 für eine schwere " +
            "Beeinträchtigung steht. Drei Items müssen jedoch umgekehrt werden, damit alle Fragen in die gleiche Richtung " +
            "weisen: Punkt 1, zur Schmerzdauer, die Kategorien 1-10 von „0-1 Woche“ bis „über 1 Jahr“; bei den Punkten 2, 5, 6, " +
            "7, 9 und 10 die eingekreiste Zahl; bei den Punkten 3, 4 und 8 10 minus der eingekreisten Zahl. So würde z.B. " +
            "„6-8 Wochen“ mit „5“ bewertet werden.\n\n" +
            "Die Gesamtpunktzahl wird zwischen 1 und 100 liegen, wobei eine Punktzahl >50 ein höheres geschätztes Risiko für " +
            "zukünftige Arbeitsunfähigkeiten anzeigt (Linton, Nicholas & MacDonald, 2011).",
        sections = listOf(Section(items = items)),
        scores = listOf(
            sum("gesamt", "Gesamtpunktzahl", items, bands = listOf(Band.upTo(50, null), Band.upTo(100, "Höheres geschätztes Risiko für zukünftige Arbeitsunfähigkeiten (>50)"))),
        ),
    )
}

/** STarT Back Screening Tool, validated German version (Aebischer et al. 2015), as in "STarT-Back-Screening-Tool-Deutsch_09-2026.pdf". */
private val START_BACK: Assessment = run {
    val statements = listOf(
        "Mein Schmerz hat während der letzten 2 Wochen zeitweise in die Beine ausgestrahlt.",
        "Zusätzlich zum Hauptschmerz hatte ich in den letzten 2 Wochen auch noch an anderen Stellen des Rückens Schmerzen.",
        "In den letzten 2 Wochen bin ich wegen meiner Schmerzen nur kurze Strecken gegangen.",
        "In den letzten 2 Wochen habe ich mich wegen meiner Schmerzen langsamer als gewöhnlich angezogen.",
        "Körperliche Aktivitäten sind für Menschen in meinem Zustand eventuell gefährlich.",
        "In den letzten 2 Wochen hatte ich viele Sorgen.",
        "Mein Schmerz ist sehr schlimm und ich habe das Gefühl, dass er sich wahrscheinlich nie mehr bessern wird.",
        "In den letzten 2 Wochen hatte ich ganz allgemein nicht mehr so viel Freude an den Dingen, die mir sonst Freude bereiten.",
    )
    val items = yesNoItems(*statements.toTypedArray()) + ChoiceItem(
        "9", "9. Wie störend war Ihr Schmerz in den letzten 2 Wochen?",
        scored("überhaupt nicht" to 0, "ein wenig" to 0, "mäßig" to 0, "sehr" to 1, "extrem" to 1),
    )
    val psychosocial = items.drop(4)
    Assessment(
        id = "start-back",
        name = "STarT Back Screening Tool-Deutsch",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "STarT Back Screening Tool-Deutsch, Stand 09/2026",
        hint = "Validierte deutsche Version (Aebischer et al. 2015).\n\n" +
            "Mit den nachfolgenden Fragen möchten wir untersuchen, wie stark Ihr Rückenschmerz Sie beeinträchtigt. Bitte lesen " +
            "Sie die nachfolgende Aufstellung durch und beantworten Sie die folgenden Fragen über ihre Rückenschmerzen. Es sind " +
            "nur die zwei letzten Wochen relevant.\n\n" +
            "Eine Gesamtpunktzahl von <=3 ist mit einem geringen Chronifizierungsrisiko verbunden. Darüber hinaus ist die " +
            "Notwendigkeit einer physiotherapeutischen Behandlung in dieser Patientengruppe gering (Hill et al. 2011). Bei " +
            "Werten >/=4 wird der Teilscore der Fragen 5-9 genauer betrachtet. Hat der Patient hier 3 oder weniger Fragen mit " +
            "Ja beantwortet, besteht ein mittleres Chronifizierungsrisiko. Bei 4 oder 5 positiv bewerteten Fragen wird der " +
            "Patient der Gruppe mit hoher Chronifizierungsgefahr zugeordnet. Solche Patienten profitieren von einem " +
            "multidisziplinären Therapieansatz, indem auch verhaltenstherapeutische Maßnahmen im Vordergrund stehen.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            sum("gesamt", "Gesamtpunktzahl", items),
            sum("teil", "Teilsumme (Fragen 5-9)", psychosocial),
            GradedScore(
                "risiko", "Chronifizierungsrisiko", unit = "", direction = Direction.LowerIsBetter,
                grades = listOf(Grade(1, Bound.below("gesamt", 4)), Grade(2, Bound.below("teil", 4)), Grade(3)),
                bands = listOf(
                    Band.upTo(1, "Geringes Chronifizierungsrisiko"),
                    Band.upTo(2, "Mittleres Chronifizierungsrisiko"),
                    Band.upTo(3, "Hohe Chronifizierungsgefahr"),
                ),
            ),
        ),
    )
}

/** Pain Catastrophizing Scale, German version (Meyer et al. 2008), as in "Pain-Catastrophizing-Scale_PCS_09-2026.pdf". */
private val PCS: Assessment = run {
    val options = numbered("trifft überhaupt nicht zu", "trifft eher nicht zu", "teils-teils", "trifft eher zu", "trifft immer zu")
    val items = listOf(
        "Ich mache mir ständig Sorgen, ob die Schmerzen wohl jemals wieder aufhören werden.",
        "Ich denke, ich kann nicht mehr.",
        "Der Zustand ist schrecklich und ich denke, dass es nie mehr besser wird.",
        "Der Zustand ist furchtbar und droht mich zu überwältigen.",
        "Ich habe das Gefühl, ich halte es nicht mehr aus.",
        "Ich bekomme Angst, dass die Schmerzen noch stärker werden.",
        "Ich denke ständig an andere Situationen, in denen ich Schmerzen hatte.",
        "Ich wünsche mir verzweifelt, dass die Schmerzen weggehen.",
        "Ich kann nicht aufhören, an die Schmerzen zu denken.",
        "Ich denke ständig daran, wie sehr es schmerzt.",
        "Ich denke ständig daran, wie sehr ich mir ein Ende der Schmerzen herbeiwünsche.",
        "Es gibt nichts, was ich tun kann, um die Schmerzen zu lindern.",
        "Ich mache mir Sorgen, dass die Schmerzen auf etwas Schlimmes hindeuten.",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", options) }
    fun of(vararg numbers: Int) = numbers.map { items[it - 1] }
    Assessment(
        id = "pcs",
        name = "Pain Catastrophizing Scale",
        abbreviation = "PCS",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "PCS, Stand 09/2026",
        hint = "Deutsche Version (Meyer et al. 2008).\n\n" +
            "Hier finden Sie verschiedene Fragen vor. Bitte lesen Sie jeweils die Einleitung und füllen Sie alle nachfolgenden " +
            "Fragen aus.\n\n" +
            "Irgendwann im Leben erleidet jeder Mensch einmal Schmerzen. Dies können z. B. Kopf-, Zahn-, Gelenk- oder " +
            "Muskelschmerzen sein. Menschen sind oft Situationen ausgesetzt, die Schmerzen verursachen wie Krankheiten, " +
            "Verletzungen, Zahnbehandlungen oder Operationen. Wir sind an den Gedanken und Gefühlen interessiert, die Sie haben, " +
            "wenn Sie Schmerzen erleiden.\n\n" +
            "Die folgenden dreizehn Sätze beschreiben verschiedene Gedanken und Gefühle, die bei Schmerzen auftreten können. " +
            "Bitte markieren Sie auf der folgenden Skala, wie stark diese Gedanken und Gefühle auf Sie zutreffen, wenn Sie " +
            "Schmerzen haben.\n\n" +
            "Je höher die Werte, desto größer wird das Katastrophisieren angesehen. Das Ergebnis der PCS reicht von 0 bis 52 " +
            "Punkte. Die Werte der Subskalen ergeben sich aus der Summe der zugehörigen Aussagen und die Gesamtpunktzahl wird " +
            // "Ausssagen" in the original, a slip mended.
            "aus der Summe aller Aussagen berechnet. Hilflosigkeit: Aussagen 1-5; 12. Verstärkung: Aussagen 6,7,13. Grübeln: " +
            "Aussagen 8-11.\n\n" +
            "Minimal messbare Veränderung (MDC): PCS-Hilflosigkeit 6,46, PCS-Verstärkung 4,02, PCS-Grübeln 5,06, " +
            "PCS-Gesamtpunktzahl 12,79 (Standardfehler des Mittelwerts, SEM: 2,33, 1,45, 1,82, 4,61).",
        sections = listOf(Section(hint = "Wenn ich Schmerzen habe, beschäftigen mich folgende Gedanken…", items = items)),
        scores = listOf(
            sum("gesamt", "Gesamtpunktzahl", items),
            sum("hilflosigkeit", "Hilflosigkeit", of(1, 2, 3, 4, 5, 12)),
            sum("verstaerkung", "Verstärkung", of(6, 7, 13)),
            sum("gruebeln", "Grübeln", of(8, 9, 10, 11)),
        ),
    )
}

/** Fragebogen zur Erfassung der schmerzspezifischen Selbstwirksamkeit (Mangels et al. 2009), as in "Fragebogen-schmerzspezifische-Selbstwirksamkeit_09-2026.pdf". */
private val FESS: Assessment = run {
    val items = listOf(
        "Ich kann trotz der Schmerzen Dinge genießen.",
        "Ich kann trotz der Schmerzen die meisten Dinge im Haushalt tun (z. B. aufräumen, abwaschen).",
        "Ich kann mich trotz der Schmerzen mit meinen Freunden oder Familienangehörigen so oft treffen, wie ich es früher getan habe/wie ich es gewohnt bin.",
        "Ich werde mit meinen Schmerzen in den meisten Situationen fertig.",
        "Ich kann trotz der Schmerzen irgendeine Form von Arbeit ausüben („Arbeit“ beinhaltet dabei Hausarbeit, bezahlte und unbezahlte Arbeit).",
        "Ich kann trotz der Schmerzen immer noch viele Dinge tun, die ich gerne mache, wie z. B. Hobbies oder Freizeitaktivitäten.",
        "Ich werde mit meinen Schmerzen auch ohne Medikamente fertig",
        "Ich kann trotz der Schmerzen noch die meisten meiner Ziele im Leben erreichen.",
        "Ich kann trotz der Schmerzen noch ein normales Leben führen.",
        "Ich kann trotz der Schmerzen nach und nach aktiver werden.",
    ).mapIndexed { i, text -> ScaleItem("${i + 1}", "${i + 1}. $text", 0, 6, "stimme nicht zu", "stimme komplett zu") }
    Assessment(
        id = "fess",
        name = "Fragebogen zur schmerzspezifischen Selbstwirksamkeit",
        abbreviation = "FESS",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "Fragebogen schmerzspezifische Selbstwirksamkeit, Stand 09/2026",
        hint = "Fragebogen zur Erfassung der schmerzspezifischen Selbstwirksamkeit (FESS, Mangels et al. 2009), eine " +
            "Adaptation des Pain Self Efficacy Questionnaire.\n\n" +
            "Bitte beantworten Sie jede Frage mit einer Antwort von „0“ („stimme nicht zu“) bis „6“ („stimme komplett zu“).\n\n" +
            "Interpretation für die Einschränkung der Selbstwirksamkeit: < 20 = extrem, 20-30 = moderat, 31-40 = gering, " +
            "> 40 = minimal/nicht.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            sum(
                "gesamt", "Gesamtsumme", items, direction = Direction.HigherIsBetter,
                bands = listOf(
                    Band.upTo(19, "Einschränkung der Selbstwirksamkeit: extrem"),
                    Band.upTo(30, "Einschränkung der Selbstwirksamkeit: moderat"),
                    Band.upTo(40, "Einschränkung der Selbstwirksamkeit: gering"),
                    Band.upTo(60, "Einschränkung der Selbstwirksamkeit: minimal/nicht"),
                ),
            ),
        ),
    )
}
