package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Kiefer / CMD" (#73), each as in its source of the date given. DIGOTOR's link of the
 * GAD-7 opens the GAQ-PP, and that of the DC/TMD Symptom-Fragebogen the Untersuchungsbogen once more: those two are taken
 * from their German originals instead (09.11.2026). None of the scored ones says what of a missing answer: each result
 * needs all of its items, as in #72.
 */
internal val JAW_CMD: List<Assessment> by lazy { listOf(DC_TMD_EXAM, DC_TMD_SQ, PAIN_DRAWING, DHI, OBC, GAD_7) }

/** A side of the jaw, as the DC/TMD's grid names it, right before left. */
private enum class Side(val key: String, val words: String, val joint: String) {
    Right("r", "rechte Seite", "Rechtes Kiefergelenk"),
    Left("l", "linke Seite", "Linkes Kiefergelenk"),
}

/** A row of the DC/TMD's grid, by [key], its words [what], and the [kinds] of pain or findings to check, each if J. */
private data class Row(val key: String, val what: String, val kinds: List<Option>)

/** Each of [rows] on both sides, one row after the other, right before left as on the form; unchecked is N (09.11.2026). */
private fun bothSides(prefix: String, rows: List<Row>): List<Item> =
    rows.flatMap { row -> Side.entries.map { side -> ChecklistItem("$prefix-${row.key}-${side.key}", "${row.what}, ${side.words}", row.kinds) } }

/** For each joint, right before left, [rows] by key, the joint's words before theirs, as sections 6 to 8 ask. */
private fun eachJoint(prefix: String, rows: List<Row>): List<Item> =
    Side.entries.flatMap { side -> rows.map { row -> ChecklistItem("$prefix-${row.key}-${side.key}", row.what.format(side.joint), row.kinds) } }

/** A sum of [items], better the lower, all of them needed. */
private fun lower(id: String, name: String, items: List<Item>, bands: List<Band> = emptyList()) =
    ItemScore(id, name, ids(items), direction = Direction.LowerIsBetter, bands = bands)

/** "falls negativ", checked for a measure below 0, in the [words] of its place on the form. */
private fun ifNegative(id: String, text: String, words: String = "falls negativ") = ChecklistItem(id, text, listOf(Option("negativ", words)))

/** DC/TMD Untersuchungsbogen, German version, as in "CMD_DC-TMD-Untersuchungsbogen_09-2026.pdf"; no result. */
private val DC_TMD_EXAM: Assessment = run {
    val pain = unscored("Schmerz", "Bekannter Schmerz")
    // Sections 4 and 5: the temporalis alone asks of a known headache.
    val locations = listOf(
        Row("temporalis", "Temporalis", unscored("Schmerz", "Bekannter Schmerz", "Bekannter Kopfschmerz")),
        Row("masseter", "Masseter", pain),
        Row("gelenk", "Gelenk", pain),
        Row("andere", "Andere Kaum.", pain),
        Row("nicht", "Nicht-Kaum.", pain),
    )
    /** The opening in mm of [prefix], "falls negativ" after it if [negative], then its rows. */
    fun movement(prefix: String, text: String, negative: Boolean = false) =
        listOf(NumberItem(prefix, text, unit = "mm")) + listOfNotNull(ifNegative("$prefix-negativ", text, "Falls negativ").takeIf { negative }) + bothSides(prefix, locations)
    val palpation = unscored("Schmerz", "Bekannter Schmerz", "Übertragener Schmerz")
    Assessment(
        id = "dc-tmd-untersuchung",
        name = "CMD – DC/TMD-Untersuchungsbogen",
        categories = listOf(AssessmentCategory.JawCmd),
        source = "CMD_DC-TMD-Untersuchungsbogen, Stand 09/2026",
        hint = "Gemäß validierter deutscher Übersetzung: Asendorf A, Eberhard L, Universitätsklinikum Heidelberg & Schierz O, " +
            "Universitätsmedizin Leipzig. Version 12/2018\n" +
            "Asendorf A, Möllenkamp J, Schierz O, et al. Interexaminer reliability of the German version of the DC/TMD. J Oral " +
            "Rehabil. 2021;48(1):28-34",
        sections = listOf(
            Section(
                items = listOf(
                    BodyRegionsItem(
                        "1a",
                        "1a. Schmerzlokalisation: letzte 30 Tage (Alle zutreffenden Antworten ankreuzen)",
                        regions = listOf(
                            BodyRegion.TemporalisRight, BodyRegion.MasseterRight, BodyRegion.OtherMasticatoryRight, BodyRegion.JawJointRight, BodyRegion.NonMasticatoryRight,
                            BodyRegion.TemporalisLeft, BodyRegion.MasseterLeft, BodyRegion.OtherMasticatoryLeft, BodyRegion.JawJointLeft, BodyRegion.NonMasticatoryLeft,
                        ),
                    ),
                    BodyRegionsItem(
                        "1b",
                        "1b. Kopfschmerzlokalisation: letzte 30 Tage (alle zutreffenden Antworten ankreuzen)",
                        regions = listOf(BodyRegion.TemporalisRight, BodyRegion.TemporalisLeft),
                    ),
                ),
            ),
            Section(
                title = "2. Schneidekantenverhältnisse",
                items = listOf(
                    ChoiceItem("2-referenzzahn", "Referenzzahn", unscored("11", "21", "Anderer")),
                    TextItem("2-referenzzahn-anderer", "Anderer", showIf = Condition("2-referenzzahn", setOf("2"))),
                    NumberItem("2-overjet", "Horizontaler inzisaler Überbiss (Overjet)", unit = "mm", decimals = true),
                    ifNegative("2-overjet-negativ", "Horizontaler inzisaler Überbiss (Overjet)"),
                    NumberItem("2-overbite", "Vertikaler inzisaler Überbiss (Overbite)", unit = "mm", decimals = true),
                    ifNegative("2-overbite-negativ", "Vertikaler inzisaler Überbiss (Overbite)"),
                    NumberItem("2-mittellinie", "Mittellinien-Abweichung", unit = "mm", decimals = true),
                    ChoiceItem("2-mittellinie-seite", "Mittellinien-Abweichung", unscored("R", "L")),
                ),
            ),
            Section(
                items = listOf(
                    ChoiceItem(
                        "3",
                        "3. Öffnungs- und Schließmuster (eine Antwort auswählen)",
                        unscored("Gerade", "Korrigierte Deviation", "Unkorrigierte Deviation nach rechts", "Unkorrigierte Deviation nach links"),
                    ),
                ),
            ),
            Section(
                title = "4. Öffnungs- und Schließbewegungen",
                items = listOf(NumberItem("4a", "A. Schmerzfreie SKD", unit = "mm")) +
                    movement("4b", "B. Maximale aktive SKD") +
                    movement("4c", "C. Maximale passive SKD") +
                    YesNoItem("4d", "D. Abgebrochen durch Patient"),
            ),
            Section(
                title = "5. Laterotrusion und Protrusion",
                items = movement("5a", "A. Laterotrusion nach rechts") +
                    movement("5b", "B. Laterotrusion nach links") +
                    movement("5c", "C. Protrusion", negative = true),
            ),
            Section(
                title = "6. Kiefergeräusche während Öffnungs- und Schließbewegungen",
                items = eachJoint(
                    "6",
                    listOf(
                        Row("knacken", "Knacken, %s", unscored("Untersucher: Öffnen", "Untersucher: Schließen", "Patient", "Schmerzh. Knacken", "Bekannter Schmerz")),
                        Row("reiben", "Reiben, %s", unscored("Untersucher: Öffnen", "Untersucher: Schließen", "Patient")),
                    ),
                ),
            ),
            Section(
                title = "7. Kiefergelenkgeräusche bei Laterotrusion und Protrusion",
                items = eachJoint(
                    "7",
                    listOf(
                        Row("knacken", "Knacken, %s", unscored("Untersucher", "Patient", "Schmerzh. Knacken", "Bekannter Schmerz")),
                        Row("reiben", "Reiben, %s", unscored("Untersucher", "Patient")),
                    ),
                ),
            ),
            Section(
                title = "8. Gelenkblockade",
                items = unscored("Blockade", "lösbar durch Patient", "lösbar durch Untersucher").let { kinds ->
                    eachJoint("8", listOf(Row("oeffnung", "%s, während der Öffnung", kinds), Row("weit", "%s, bei weiter Mundöffnung", kinds)))
                },
            ),
            Section(
                title = "9. Muskel- und Kiefergelenkschmerzen bei Palpation (i.d.R. für Temporalis und Masseter neun Punkte palpieren)",
                items = bothSides(
                    "9",
                    listOf(
                        Row("temporalis", "Muskulatur (1 kg): Temporalis", unscored("Schmerz", "Bekannter Schmerz", "Bekannter Kopfschmerz", "Ausbreitender Schmerz", "Übertragener Schmerz")),
                        Row("masseter", "Muskulatur (1 kg): Masseter", unscored("Schmerz", "Bekannter Schmerz", "Ausbreitender Schmerz", "Übertragener Schmerz")),
                        Row("lateraler-pol", "Kiefergelenk: Lateraler Pol (0,5 kg)", palpation),
                        Row("um-den-pol", "Kiefergelenk: Um den lateralen Pol (1 kg)", palpation),
                    ),
                ),
            ),
            Section(
                title = "Ergänzende Muskelschmerzen auf Palpation",
                items = bothSides(
                    "9e",
                    listOf(
                        Row("retromandibularis", "(0,5 kg) Regio retromandibularis", palpation),
                        Row("submandibularis", "(0,5 kg) Regio submandibularis", palpation),
                        Row("pteryg", "(0,5 kg) Bereich des M. pteryg. lat.", palpation),
                        Row("temporalissehne", "(0,5 kg) Temporalissehne", palpation),
                    ),
                ),
            ),
        ),
    )
}

/**
 * DC/TMD Symptombezogener Fragebogen, German version 12/2018 of Asendorf, Eberhard and Schierz, as in the International
 * RDC/TMD Consortium's "DC-TMD-German-Assessment-Instruments_2018_12_16.pdf"; its skips as conditions; no result.
 */
private val DC_TMD_SQ: Assessment = run {
    val activities = listOf(
        "Kauen von harter oder zäher Nahrung",
        "Öffnung des Mundes oder Bewegung des Kiefers nach vorn oder zur Seite",
        "Angewohnheiten wie Zähne aufeinander halten, Pressen/Knirschen mit den Zähnen oder Kaugummikauen",
        "Andere Aktivitäten wie Reden, Küssen oder Gähnen",
    )
    fun changed(n: Int, showIf: Condition) =
        activities.zip(listOf("a", "b", "c", "d")).map { (text, part) -> YesNoItem("$n$part", "${part.uppercase()}. $text", showIf = showIf) }
    fun since(n: Int, text: String, showIf: Condition) = listOf(
        NumberItem("$n-jahre", "$n. $text", unit = "Jahre", showIf = showIf),
        NumberItem("$n-monate", "Monate", showIf = showIf),
    )
    Assessment(
        id = "dc-tmd-sq",
        name = "CMD – DC/TMD Symptom-Fragebogen",
        categories = listOf(AssessmentCategory.JawCmd),
        source = "DC/TMD Symptombezogener Fragebogen, deutsche Version 12/2018",
        hint = "Diagnostic Criteria for Temporomandibular Disorders – Symptombezogener Fragebogen\n\n" +
            "Copyright International RDC/TMD Consortium Network. Deutsche Übersetzung: Asendorf A, Eberhard L, " +
            "Universitätsklinikum Heidelberg & Schierz O, Universitätsmedizin Leipzig. Version 12/2018. Verfügbar unter " +
            "http://www.rdc-tmdinternational.org. Keine Erlaubnis zur Vervielfältigung, Übersetzung, Abbildung oder Verbreitung " +
            "erforderlich.",
        sections = listOf(
            Section(
                title = "Schmerzen",
                items = listOf(
                    YesNoItem("1", "1. Hatten Sie jemals auf einer oder beiden Seiten Schmerzen im Kiefer, im Schläfenbereich, im Ohr oder vor dem Ohr?"),
                ) + since(
                    2,
                    "Vor wie vielen Jahren oder Monaten hat Ihr Schmerz im Kiefer, den Schläfen, im oder vor dem Ohr erstmals begonnen?",
                    Condition.ifYes("1"),
                ) + ChoiceItem(
                    "3",
                    "3. Welche der folgenden Antworten beschreibt die Schmerzen im Kiefer, Schläfenbereich, im Ohr oder vor dem Ohr auf " +
                        "beiden Seiten in den letzten 30 Tagen am besten? Wählen Sie EINE Antwort.",
                    unscored("Keine Schmerzen", "Schmerzen kommen und gehen", "Schmerzen sind immer vorhanden"),
                    showIf = Condition.ifYes("1"),
                ),
            ),
            Section(
                hint = "4. Haben die folgenden Aktivitäten in den letzten 30 Tagen einen Schmerz im Kiefer oder im Schläfenbereich, im " +
                    "Ohr oder vor dem Ohr, auf einer oder beiden Seiten verändert (d. h. verbessert bzw. verschlechtert)?",
                items = changed(4, Condition("3", setOf("1", "2"))),
            ),
            Section(
                title = "Kopfschmerzen",
                items = listOf(YesNoItem("5", "5. Hatten Sie in den letzten 30 Tagen Kopfschmerzen im Schläfenbereich?")) +
                    since(6, "Vor wie vielen Jahren oder Monaten trat Ihr Schläfenkopfschmerz erstmals auf?", Condition.ifYes("5")),
            ),
            Section(
                hint = "7. Haben die folgenden Aktivitäten in den letzten 30 Tagen einen Schläfenkopfschmerz auf einer oder beiden Seiten " +
                    "verändert (d.h. verbessert bzw. verschlechtert)?",
                items = changed(7, Condition.ifYes("5")),
            ),
            Section(
                title = "Gelenkgeräusche",
                items = listOf(
                    YesNoItem("8", "8. Hatten Sie in den letzten 30 Tagen Gelenkgeräusche, wenn Sie Ihren Kiefer bewegt oder benutzt haben?"),
                    ChecklistItem("8-seite", "Office use", unscored("R", "L", "DNK")),
                ),
            ),
            Section(
                title = "Kieferklemme",
                items = listOf(
                    YesNoItem("9", "9. War Ihre Mundöffnung jemals, auch nur für einen Moment blockiert, sodass sich der Unterkiefer nicht VOLLSTÄNDIG öffnen ließ?"),
                    YesNoItem(
                        "10",
                        "10. War die Blockade Ihres Kiefers so stark, dass es Ihre Mundöffnung eingeschränkt und Ihre Fähigkeit zu Essen beeinträchtigt hat?",
                        showIf = Condition.ifYes("9"),
                    ),
                    YesNoItem(
                        "11",
                        "11. Hatten Sie in den letzten 30 Tagen eine Kieferklemme, sodass Sie den Mund, wenn auch nur kurzzeitig, nicht " +
                            "VOLLSTÄNDIG öffnen konnten, und löste sich diese anschließend, sodass Sie den Mund VOLLSTÄNDIG öffnen konnten?",
                        showIf = Condition.ifYes("9"),
                    ),
                    YesNoItem(
                        "12",
                        "12. Ist Ihr Kiefer gegenwärtig blockiert oder eingeschränkt, so dass sich Ihr Kiefer nicht VOLLSTÄNDIG öffnen lässt?",
                        showIf = Condition.ifYes("11"),
                    ),
                ),
            ),
            Section(
                title = "Kiefersperre",
                items = listOf(
                    YesNoItem(
                        "13",
                        "13. Ist Ihr Kiefer in den letzten 30 Tagen, bei weiter Mundöffnung, auch nur für einen kurzen Moment, blockiert " +
                            "bzw. hängen geblieben, sodass Sie den Mund von dieser weit geöffneten Position aus nicht mehr schließen konnten?",
                    ),
                    YesNoItem(
                        "14",
                        "14. Mussten Sie in den letzten 30 Tagen, als ihr Kiefer weit geöffnet blockiert oder hängen geblieben war, etwas " +
                            "unternehmen, um ihn schließen zu können, z.B. entspannen, bewegen, drücken oder umlenken?",
                        showIf = Condition.ifYes("13"),
                    ),
                ),
            ),
        ),
    )
}

/** Schmerzbild, as in "CMD_Schmerzbild_09-2026.pdf": its drawings as regions of the face and the jaw, and of the body; no result. */
private val PAIN_DRAWING: Assessment = Assessment(
    id = "cmd-schmerzbild",
    name = "CMD – Schmerzbild",
    categories = listOf(AssessmentCategory.JawCmd),
    source = "CMD_Schmerzbild, Stand 09/2026",
    hint = "Bitte kennzeichnen Sie all Ihre Schmerzbereiche, indem Sie diese schraffieren. Verwenden Sie hierfür die Schaubilder, " +
        "welche am meisten zutreffen.\n" +
        "Falls es einen genauen Schmerzpunkt gibt, markieren Sie diesen mit einem deutlichen Punkt. Sollte Ihr Schmerz von einer " +
        "Stelle zu einer anderen ausstrahlen, verwenden Sie bitte Pfeile, um diesen Weg anzuzeigen.",
    sections = listOf(
        Section(
            items = listOf(
                BodyRegionsItem("gesicht", "Mund und Zähne, Rechte und Linke Gesichtshälfte", regions = BodyRegion.jaw),
                BodyRegionsItem("koerper", "Körper"),
            ),
        ),
    ),
)

/** Dizziness Handicap Inventory, German version (Kurre 2009), as in "Dizziness-Handicap-Inventory_DHI_09-2026.pdf". */
private val DHI: Assessment = run {
    val answers = scored("Ja" to 4, "Manchmal" to 2, "Nein" to 0)
    val questions = listOf(
        "P" to "Verstärken sich Ihre Probleme, wenn Sie nach oben schauen?",
        "E" to "Fühlen Sie sich wegen Ihrer Probleme frustriert?",
        "F" to "Schränken Sie wegen Ihrer Probleme geschäftliche oder private Reisen ein?",
        "P" to "Verstärken sich Ihre Probleme, wenn Sie einen Gang im Supermarkt entlang gehen?",
        "F" to "Haben Sie wegen Ihrer Probleme Schwierigkeiten beim ins Bett gehen oder beim Aufstehen aus dem Bett?",
        "F" to "Schränken Ihre Probleme Sie deutlich ein, an gesellschaftlichen Aktivitäten teilzunehmen (z.B. auswärts essen " +
            "gehen, Einladungen folgen, zu Parties gehen, ins Kino gehen, Theater oder Konzerte besuchen)?",
        "F" to "Haben Sie wegen Ihrer Probleme Schwierigkeiten beim Lesen?",
        "P" to "Verstärken sich Ihre Probleme bei anspruchsvolleren Aktivitäten z.B. im Sport, beim Tanzen oder bei Hausarbeiten?",
        "E" to "Haben Sie wegen Ihrer Probleme Angst, das Haus ohne Begleitung zu verlassen?",
        "E" to "Sind Sie wegen Ihrer Probleme schon einmal in eine peinliche Situation geraten?",
        "P" to "Verstärken schnelle Kopfbewegungen Ihre Probleme?",
        "F" to "Meiden Sie die Höhe wegen Ihrer Probleme (zum Beispiel Berge, Hochhaus, Leiter, Gerüst)?",
        "P" to "Verstärken sich Ihre Probleme, wenn Sie sich im Bett drehen?",
        "F" to "Haben Sie wegen Ihrer Probleme Schwierigkeiten, anstrengende Haus- oder Gartenarbeit zu erledigen?",
        "E" to "Befürchten Sie, dass andere Leute wegen Ihrer Probleme denken, Sie seien betrunken?",
        "F" to "Haben Sie wegen Ihrer Probleme Schwierigkeiten, alleine spazieren zu gehen?",
        "P" to "Verstärken sich Ihre Probleme, wenn Sie auf einem Trottoir/Bürgersteig gehen?",
        "E" to "Ist es wegen Ihrer Probleme schwierig für Sie, sich zu konzentrieren?",
        "F" to "Ist es wegen Ihrer Probleme für Sie schwierig, sich im Dunkeln in Ihrer Wohnung zu bewegen?",
        "E" to "Haben Sie wegen Ihrer Probleme Angst, alleine zu Hause zu bleiben?",
        "E" to "Fühlen Sie sich wegen Ihrer Probleme behindert/eingeschränkt?",
        "E" to "Belasten Ihre Probleme die Beziehung zu Familienmitgliedern oder Freunden?",
        "E" to "Fühlen Sie sich aufgrund Ihrer Probleme deprimiert?",
        "F" to "Werden Sie durch Ihre Probleme beeinträchtigt, Ihre Aufgaben im Beruf oder Haushalt wahrzunehmen?",
        "P" to "Verstärken sich Ihre Probleme, wenn Sie sich nach vorne beugen?",
    )
    val items = questions.mapIndexed { i, (scale, text) -> ChoiceItem("${i + 1}", "$scale${i + 1} $text", answers) }
    fun total(id: String, name: String, scale: String) = lower(id, name, items.filterIndexed { i, _ -> questions[i].first == scale })
    Assessment(
        id = "dhi",
        name = "Dizziness Handicap Inventory",
        abbreviation = "DHI",
        categories = listOf(AssessmentCategory.JawCmd),
        source = "DHI, Stand 09/2026",
        hint = "Dieser Fragebogen dient dazu, die Probleme herauszufinden, die Sie wegen Ihres Schwindels oder Ihrer " +
            "Gleichgewichtsprobleme haben können. Beantworten Sie bitte jede Frage entweder mit „ja“, „nein“ oder „manchmal“. " +
            "Beantworten Sie jede Frage nur in Bezug auf Ihr Schwindel- oder Gleichgewichtsproblem.\n\n" +
            "Der Dizziness Handicap Inventory (Jacobson 1990, Kurre 2009) ist ein guter und einfacher Fragebogen, mit dem man die " +
            "Auslöser und Folgen von Schwindel und Gleichgewichtsstörungen dokumentieren kann. Außerdem kann er in der " +
            "Verlaufskontrolle eingesetzt werden. Er ist der am meisten verbreitete Fragebogen zur Thematik Schwindel und wurde " +
            "mittlerweile in 14 Sprachen übersetzt (Mutlu 2013).\n\n" +
            "Insgesamt besteht der DHI aus 25 und 3 Unterskalen. Die Fragen werden mittels Buchstaben den Unterkategorien " +
            "zugeordnet. P: Physisch – 7 Fragen mit maximal 28 Punkten – Fragen zur Symptomauslösung. E: Emotional – 9 Fragen " +
            "mit maximal 36 Punkten – Fragen zu emotionalen Auswirkungen. F: Funktionell – 9 Fragen mit maximal 36 Punkten – " +
            "Fragen zu Einschränkungen im Alltag.\n\n" +
            "Insgesamt können maximal 100 erreicht werden. Dies würde eine maximale gefühlte Einschränkung im Alltag bedeuten. " +
            "Je mehr Punkte, desto größer die Problematik im Alltag.\n\n" +
            "Die Fragen F5 und P13 können Hinweise auf einen benignen paroxysmalen Lagerungsschwindel, vestibulären oder " +
            "zervikogenen Schwindel geben. Auffälligkeiten bei den Fragen P1, P11 und P25, sind Hinweise für vestibuläre oder " +
            "zervikogene Auslöser. Hat der Proband Probleme im Dunkeln und mit Menschenmengen (F19, F6), könnte eine " +
            "Polyneuropathie oder eine visuelle Abhängigkeit vorliegen.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            total("p", "Total P: Physisch", "P"),
            total("e", "Total E: Emotional", "E"),
            total("f", "Total F: Funktionell", "F"),
            WeightedScore("total", "Total", direction = Direction.LowerIsBetter, parts = listOf("p", "e", "f")),
        ),
    )
}

/** Oral Behaviors Checklist, German version 12/2018, as in "Oral-Behaviors-Checklist_OBC_09-2026.pdf". */
private val OBC: Assessment = run {
    val asleep = numbered("Nie", "<1 Nacht/Monat", "1-3 Nächte/Monat", "1-3 Nächte/Woche", "4-7 Nächte/Woche")
    val awake = numbered("Nie", "Selten", "Manchmal", "Häufig", "Immer")
    val sleep = listOf(
        "Pressen oder Knirschen mit den Zähnen während des Schlafs, basierend auf den verfügbaren Informationen",
        "Schlafen in einer Position, die Druck auf den Kiefer ausübt (z.B. auf dem Bauch oder der Seite)",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1} $text", asleep) }
    val day = listOf(
        "Zähneknirschen während der Wachzeit",
        "Zähnepressen während der Wachzeit",
        "Pressen, Berühren oder Zusammenhalten der Zähne außer beim Essen (gemeint ist der Kontakt zwischen den oberen und unteren Zähnen)",
        "Anspannen oder Verspannen der Muskulatur ohne Pressen oder Aufeinanderlegen der Zähne",
        "Halten oder Schieben des Kiefers nach vorne oder zur Seite",
        "Kraftvolles Pressen der Zunge gegen die Zähne",
        "Legen der Zunge zwischen die Zähne",
        "Beißen und Kauen auf oder Spielen mit der Zunge, Wange oder Lippe",
        "Halten des Kiefers in starrer oder angespannter Position, um den Kiefer zu schienen oder zu schützen",
        "Objekte zwischen den Zähnen halten oder darauf beißen, z.B. Haare, Pfeife, Bleistift, Kugelschreiber, Finger, -nägel",
        "Kaugummikauen",
        "Spielen von Musikinstrumenten, bei denen der Mund oder Kiefer beansprucht wird (z.B. Holz-, Blechblas- und Streichinstrumente)",
        "Aufstützen des Kiefers auf die Hand, z.B. zum Umfassen oder Ausruhen des Kinns in der Hand",
        "Einseitiges Kauen von Nahrung",
        "Essen zwischen den Mahlzeiten (gemeint ist Nahrung, die gekaut werden muss)",
        "Anhaltendes Sprechen (z.B. Lehrtätigkeit, Verkauf, Kundenservice)",
        "Singen",
        "Gähnen",
        "Halten des Telefons zwischen Kopf und Schultern",
    ).mapIndexed { i, text -> ChoiceItem("${i + 3}", "${i + 3} $text", awake) }
    Assessment(
        id = "obc",
        name = "Oral Behaviors Checklist",
        abbreviation = "OBC",
        categories = listOf(AssessmentCategory.JawCmd),
        source = "OBC, Stand 09/2026",
        hint = "Fragebogen zu oralen Angewohnheiten\n\n" +
            "Dieser Fragebogen ist ein wichtiges Hilfsmittel, um Ihnen die bestmögliche Behandlung zukommen zu lassen. Ihre " +
            "Antworten können Ihrem/-r Arzt/Ärztin und Therapeuten/-in helfen, Ihre Beschwerden besser zu verstehen. Bitte " +
            "beantworten Sie jede Frage, so gut Sie können. Überspringen Sie Fragen nur, wenn Sie dazu aufgefordert werden. " +
            "Kreisen Sie bitte die für Sie passende Antwort ein.\n\n" +
            "Hinweis auf Parafunktion: Summenbildung über Antwortpunkte der Fragen (OBC): 0 keine, 1-24 gering, 25-84 ausgeprägt.\n\n" +
            "Gemäß validierter deutscher Übersetzung: Asendorf A, Eberhard L, Universitätsklinikum Heidelberg & Schierz O, " +
            "Universitätsmedizin Leipzig. Version 12/2018.",
        sections = listOf(
            Section(title = "Aktivitäten während des Schlafs", items = sleep),
            Section(title = "Aktivitäten während der Wachzeit", items = day),
        ),
        scores = listOf(
            lower("summe", "Punktzahl", sleep + day, listOf(Band.upTo(0, "keine"), Band.upTo(24, "gering"), Band.upTo(84, "ausgeprägt"))),
        ),
    )
}

/**
 * Generalized Anxiety Disorder Scale 7, German version of Prof. Dr. Bernd Löwe (2015, Universitätsklinikum Hamburg-Eppendorf),
 * as DIGOTOR links to none (09.11.2026).
 */
private val GAD_7: Assessment = run {
    val answers = numbered("Überhaupt nicht", "An einzelnen Tagen", "An mehr als der Hälfte der Tage", "Beinahe jeden Tag")
    val items = listOf(
        "Nervosität, Ängstlichkeit oder Anspannung",
        "Nicht in der Lage sein, Sorgen zu stoppen oder zu kontrollieren",
        "Übermäßige Sorgen bezüglich verschiedener Angelegenheiten",
        "Schwierigkeiten zu entspannen",
        "Rastlosigkeit, so dass Stillsitzen schwer fällt",
        "Schnelle Verärgerung oder Gereiztheit",
        "Gefühl der Angst, so als würde etwas Schlimmes passieren",
    ).zip("abcdefg".toList()).map { (text, part) -> ChoiceItem("$part", "$part. $text", answers) }
    Assessment(
        id = "gad-7",
        name = "Generalized Anxiety Disorder Scale",
        abbreviation = "GAD-7",
        categories = listOf(AssessmentCategory.JawCmd),
        source = "GAD-7, deutsche Version (Löwe 2015)",
        hint = "Gesundheitsfragebogen für Patienten (GAD-7)\n\n" +
            "Wie oft fühlten Sie sich im Verlauf der letzten 2 Wochen durch die folgenden Beschwerden beeinträchtigt?\n\n" +
            "Bewertung des Schweregrads der Angst (GAD-7): Der Schweregrad wird berechnet, indem den Antwortkategorien " +
            "\"überhaupt nicht\", \"einige Tage\", \"mehr als die Hälfte der Tage\" und \"fast jeden Tag\" die Werte 0, 1, 2 bzw. 3 " +
            "zugeordnet werden. Der GAD-7-Gesamtwert für die sieben Items reicht von 0 bis 21.\n\n" +
            "Englische Version: Generalized Anxiety Disorder Scale-7 (GAD-7). Deutsche Version: © Prof. Dr. Bernd Löwe, 2015, " +
            "Universitätsklinikum Hamburg-Eppendorf",
        sections = listOf(Section(items = items)),
        scores = listOf(
            lower(
                "gesamt", "Gesamtwert", items,
                listOf(Band.upTo(4, "minimale Ängste"), Band.upTo(9, "leichte Ängste"), Band.upTo(14, "mäßige Ängste"), Band.upTo(21, "schwere Ängste")),
            ),
        ),
    )
}
