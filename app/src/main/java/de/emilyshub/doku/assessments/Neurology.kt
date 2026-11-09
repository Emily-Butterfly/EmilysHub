package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Neurologie" (#74), each as in its PDF of the date in its source. None of them says
 * what of a missing answer: each result needs all of its items, as in #72. Where a PDF falls short, as asked on
 * 09.11.2026: the GAS rates any number of goals and reckons Kiresuk's T-score, the MFIS has all 21 statements, and the
 * Fugl-Meyer asks of the sense of position too.
 */
internal val NEUROLOGY: List<Assessment> by lazy { listOf(EMS, FOG, FUGL_MEYER, FAC, GAS, MINI_BEST, MFIS, MOS_PES, MS_DEPRESSION) }

/** A sum of [items], better the higher, all of them needed. */
private fun higher(id: String, name: String, items: List<Item>, bands: List<Band> = emptyList()) =
    Score(id, name, ids(items), direction = Direction.HigherIsBetter, bands = bands)

/** A sum of [items], better the lower, all of them needed. */
private fun lowerSum(id: String, name: String, items: List<Item>, bands: List<Band> = emptyList()) =
    Score(id, name, ids(items), direction = Direction.LowerIsBetter, bands = bands)

/** European Myelopathy Score, as in "European-Myelopathy-Score_EMS_10-2026.pdf". */
private val EMS: Assessment = run {
    val items = listOf(
        ChoiceItem(
            "a", "A) Funktion des ersten motorischen Neurons (Gang)",
            scored(
                "Unfähigkeit zu gehen, auf Hilfe angewiesen" to 1,
                "Auf ebenen Boden nur mit Gehilfe/Unterstützung" to 2,
                "Treppensteigen nur Gehhilfe/Unterstützung" to 3,
                "Unsicherer Gang, Gangbild plump erscheinend" to 4,
                "Unauffälliges Gangbild" to 5,
            ),
        ),
        ChoiceItem(
            "b", "B) Funktion des ersten motorischen Neurons und des afferenten Systems",
            scored("Überlaufblase, keine Kontrolle" to 1, "Unvollständige Entleerung und gestörte Frequenz" to 2, "Normale Blasen- und Darmfunktion" to 3),
        ),
        ChoiceItem(
            "c", "C) Funktion des zweiten Neurons",
            scored(
                "Handschrift verändert/Essen mit Besteck nicht möglich" to 1,
                "Handschrift verändert/Essen mit Besteck eingeschränkt" to 2,
                "Handschrift verändert/Schnürsenkel binden ungeschickt" to 3,
                "Normale Handschrift/Feinmotorik" to 4,
            ),
        ),
        ChoiceItem(
            "d", "D) Funktion der Hinterstränge (Propriozeption und Koordination)",
            scored("An-und Auskleiden nur mit Hilfe" to 1, "An-und Auskleiden ungeschickt und langsam" to 2, "An-und Auskleiden normal" to 3),
        ),
        ChoiceItem(
            "e", "E) Hinterwurzel (segmental radikuläre Parästhesie/Schmerzen)",
            scored("Invalidisierende Schmerzen" to 1, "Ertragbare Parästhesien/Schmerzen" to 2, "Keine Parästhesien/Schmerzen" to 3),
        ),
    )
    Assessment(
        id = "ems",
        name = "European Myelopathy Score",
        abbreviation = "EMS",
        categories = listOf(AssessmentCategory.Neurology),
        source = "EMS, Stand 10/2026",
        hint = "Vitzthum 2007, Herdmann 1994",
        sections = listOf(Section(items = items)),
        scores = listOf(
            higher("punkte", "Punkte", items, listOf(Band.upTo(8, "Grad 3"), Band.upTo(12, "Grad 2"), Band.upTo(16, "Grad 1"), Band.upTo(18, "Normal"))),
        ),
    )
}

/** Freezing of Gait Questionnaire, German version not validated, as in "Freezing-of-Gait-Questionnaire-Questionnaire_FOG_10-2026.pdf". */
private val FOG: Assessment = run {
    val items = listOf(
        "In Ihrem schlechtesten Zustand gehen Sie:" to
            numbered("Normal", "Ziemlich normal - etwas langsam", "Langsam, aber völlig selbständig", "Brauche Hilfe oder Gehstützen", "Nicht in der Lage zu gehen"),
        "Beeinflussen Ihre Gehschwierigkeiten Ihre alltäglichen Aktivitäten und Ihre Unabhängigkeit?" to
            numbered("Überhaupt nicht", "Ein bisschen", "Moderat", "Stark", "Nicht in der Lage zu gehen"),
        "Spüren Sie, wie Ihre Füße am Boden kleben bleiben während des Gehens, während des Drehens oder wenn Sie den ersten Schritt auslösen möchten?" to
            numbered("Nie", "Sehr selten (einmal im Monat)", "Selten (einmal in der Woche)", "Oft (einmal am Tag)", "Immer (beim Gehen)"),
        "Wie lange dauert Ihre längste Freezing Episode?" to
            numbered("Passiert nie", "1-2 Sekunden", "3-10 Sekunden", "11-30 Sekunden", "Nicht in der Lage zu gehen für mehr als 30 Sekunden"),
        "Wie lange dauert Ihre typische Starthemmung, wenn Sie den ersten Schritt einleiten?" to
            numbered(
                "Gar nicht", "Dauert länger als 1 Sekunde bis Gehstart", "Dauert länger als 3 Sekunden bis Gehstart",
                "Dauert länger als 10 Sekunden bis Gehstart", "Dauert länger als 30 Sekunden bis Gehstart",
            ),
        "Wie lange müssen Sie typischerweise zögern, bevor Sie drehen (Freezing beim Drehen)?" to
            numbered("Nie", "1-2 Sekunden bis zum Drehen", "3-10 Sekunden bis zum Drehen", "11-30 Sekunden bis zum Drehen", "Nicht in der Lage zu Drehen für mehr als 30 Sekunden"),
    ).mapIndexed { i, (text, options) -> ChoiceItem("${i + 1}", "${i + 1}. $text", options) }
    Assessment(
        id = "fog",
        name = "Freezing of Gait Questionnaire",
        abbreviation = "FOG",
        categories = listOf(AssessmentCategory.Neurology),
        source = "FOG, Stand 10/2026",
        hint = "Nicht validierte deutsche Version.\n\n" +
            "Das Einfrieren des Gangs (Freezing of Gait, FOG) ist ein belastendes Parkinson-Syndrom, das u.a. mit Stürzen " +
            "einhergeht. Der Freezing-of-Gait-Fragebogen (FOG-Q) erkennt FOG bei Patienten mit Parkinson. Das FOG-Q besteht aus " +
            "sechs Fragen. Die Fragen 1, 2, 4, 5 und 6 beziehen sich auf die Erfahrungen des Patienten in Bezug auf FOG der " +
            "Vorwoche. Bei Frage 3 wird der Patient nach seiner Erfahrung mit FOG in verschiedenen Situationen befragt, die " +
            "zeitlich unbegrenzt ist.\n\n" +
            "Jede Frage hat eine 5-Punkte-Skala, wobei 0 das Fehlen von Symptomen bedeutet und 4 das schlechteste Stadium " +
            "darstellt. Demnach reicht die Gesamtpunktzahl des FOG-Fragebogens von 0 bis 24 Punkten. Je höher der Gesamtwert, " +
            "desto ausgeprägter ist das Einfrieren des Gangs (FOG) bei Patienten mit Parkinson.",
        sections = listOf(Section(items = items)),
        scores = listOf(lowerSum("gesamt", "Gesamtpunktzahl", items)),
    )
}

/**
 * Fugl-Meyer Assessment of the upper extremity, German version not validated, as in "Fugl-Meyer-Assessment-Score_10-2026.pdf";
 * the synergies of A 2) scored 0, 1, 2 as in the original (09.11.2026), and H with the original's four of position sense,
 * whose answers the PDF has without them (09.11.2026).
 */
private val FUGL_MEYER: Assessment = run {
    val none = numbered("nicht", "teilweise", "ganz")
    fun choice(id: String, text: String, options: List<Option>) = ChoiceItem(id, text, options)
    fun described(id: String, text: String, vararg labels: String) = ChoiceItem(id, text, numbered(*labels))

    val reflexes = listOf("a1-flexoren" to "Flexoren: Bizeps/Fingerflexoren", "a1-extensoren" to "Extensoren: Trizeps")
        .map { (id, text) -> choice(id, text, scored("nicht vorhanden" to 0, "kann ausgelöst werden" to 2)) }
    val synergies = listOf(
        "retraktion" to "Schulter: Retraktion", "elevation" to "Schulter: Elevation", "abduktion" to "Schulter: Abduktion (90°)",
        "aussenrotation" to "Schulter: Außenrotation", "flexion" to "Ellenbogen: Flexion", "supination" to "Unterarm: Supination",
        "adduktion" to "Schulter: Adduktion", "extension" to "Ellenbogen: Extension", "pronation" to "Unterarm: Pronation",
    ).map { (id, text) -> choice("a2-$id", text, none) }
    val mixed = listOf(
        described(
            "a3-lws", "Hand zu unterer LWS",
            "kann nicht durchgeführt werden", "Hand vor SIAS Hand hinter SIAS (ohne Kompensation)", "Hand an Lendenwirbelsäule (ohne Kompensation)",
        ),
        described(
            "a3-flexion", "Schulterflexion 0-90°, Ellenbogen bei 0°/Pro- Supination 0°",
            "sofortige Abduktion oder Ellenbogenflexion", "Abduktion oder Ellenbogenflexion während der Bewegung", "vollständige Flexion 90°, hält 0° im Ellenbogen",
        ),
        described(
            "a3-pro-supination", "Pro - Supination, Ellenbogen bei 90°/Schulter bei 0°",
            "keine Pronation/Supination, Startposition unmöglich", "begrenzte Pronation/Supination, behält die Position",
            "bei vollständiger Pronation/Supination, behält die Position",
        ),
    )
    val little = listOf(
        described(
            "a4-abduktion", "Schulterabduktion 0°-90°, Ellenbogen bei 0° Unterarm proniert",
            "sofortige Supination oder Ellenbogenflexion", "Supination oder Ellenbogenflexion während der Bewegung", "Abduktion 90°, Streckung und Pronat. erhalten",
        ),
        described(
            "a4-flexion", "Schulterflexion 90°-180°, Ellenbogen bei 0° Pro/Supination bei 0°",
            "sofortige Abduktion oder Ellenbogenflexion", "Abduktion oder Ellenbogenflexion während der Bewegung", "vollständige Flexion, 0° im Ellenbogen",
        ),
        described(
            "a4-pro-supination", "Pronation/Supination, Ellenbogen bei 0° Schulter bei 30°-90° Flexion",
            "keine Pronation/Supination, Ausgangsposition unmöglich", "begrenzte Pronation/Supination, behält die Streckung bei",
            "volle Pronation/Supination, behält die Ellbogenverlängerung bei",
        ),
    )
    val normalReflexes = listOf(
        described(
            "a5", "Bizeps, Trizeps, Fingerbeuger",
            "0 Punkte auf Teil IV oder 2 von 3 deutlich hyperaktiven Reflexen", "1 deutlich hyperaktiver Reflex oder mindestens 2 lebhafte Reflexe",
            "maximal 1 lebhafter Reflex, keiner hyperaktiv",
        ),
    )
    val wrist = listOf(
        described(
            "b-stabilitaet-90", "Stabilität bei 15° Dorsalextension, Ellenbogen bei 90°, Unterarm proniert, Schulter bei 0°",
            "weniger als 15° aktive DE", "DE 15° ohne Widerstand", "behält Position gegen Widerstand",
        ),
        described(
            "b-wiederholt-90", "Wiederholte Dorsalextension/freiwillige Flexion, Ellenbogen bei 90° flex., Unterarm proniert; Schulter bei 0°, leichte Fingerflexion",
            "kann nicht freiwillig durchführen", "begrenzter aktiver Bewegungsbereich", "volle aktive Bewegungsfreiheit, reibungslos",
        ),
        described(
            "b-stabilitaet-0", "Stabilität bei 15° Dorsalextension, Ellenbogen bei 0°, Unterarm ausgeprägt leichte Schulterflexion/Abduktion",
            "weniger als 15° aktive Dorsalflexion", "Dorsalflexion 15°, kein Widerstand wird genommen", "hält Position gegen Widerstand",
        ),
        described(
            "b-wiederholt-0", "Wiederholte Dorsalextension/freiwillige Flexion, Ellenbogen bei 0°, Unterarm proniert, leichte Schulterflexion/Abduktion",
            "kann nicht freiwillig durchführen,", "limitiertes ROM", "bewegt ohne Probleme",
        ),
        described("b-zirkumduktion", "Circumduction", "kann freiwillig nicht bewegen", "ruckartige oder inkomplette Bewegung", "komplette sanfte Bewegung"),
    )
    val hand = listOf(
        choice("c-beugung", "Massen Beugung von komplett aktiv oder passiver Extension", none),
        choice("c-extension", "Massen Extension von kompletter aktiver oder passiver Beugung", none),
        described(
            "c-a", "Greifen A - Flexion in PIP & DIP (Finger 2-5) Extension in MCP 2-5",
            "nicht durchführbar", "kann Position halten, aber schwer", "behält Position gegen Widerstand",
        ),
        described(
            "c-b", "Greifen B - Daumen Adduktion 1. CMC, MCP, IP bei 0° Stück Papier zwischen Daumen und 2. MCP Gelenk",
            "nicht durchführbar", "kann Papier halten, aber nicht gegen Zug", "kann Papier gegen Zug halten",
        ),
        described(
            "c-c", "Greifen C - Opposition Pulpa des Daumens gegen die Pulpa des 2. Finger, Kugelschreiber, Zug nach oben",
            "nicht durchführbar", "kann Kugelschreiber halten, aber nicht gegen Zug", "kann Kugelschreiber gegen Zug halten",
        ),
        described(
            "c-d", "Greifen D - Zylindergriff zylinderförmiges Objekt (kleine Dose) nach oben ziehen, Opposition in den Fingern I und II",
            "nicht durchführbar", "kann Zylinder halten, aber nicht gegen Zug", "kann Zylinder gegen Zug halten",
        ),
        described(
            "c-e", "Greifen E - Spherical Grip Finger in Abduktion/Flexion, Daumen in Opposition, Tennisball",
            "nicht durchführbar", "kann Ball halten, aber nicht gegen Zug", "kann Ball gegen Zug halten",
        ),
    )
    val coordination = listOf(
        described("d-tremor", "Tremor", "betont", "leicht", "nicht"),
        described("d-dysmetrie", "Dysmetrie", "betont und unsystematisch", "leicht und systematisch", "keine Dysmetrie"),
        described(
            "d-zeit", "Zeit",
            ">5 sek.: mehr als 5 sek. langsamer als unbetroffene Seite", "2 sek. - 5 sek.: 2-5 sek. langsamer als unbetroffene Seite",
            "<1 sek.: maximale Differenz ist 1 sek. zwischen beiden Seiten",
        ),
    )
    val touch = listOf("h-arm" to "leichte Berührung: Oberarm, Unterarm", "h-hand" to "leichte Berührung: palmare Oberfläche der Hand")
        .map { (id, text) -> described(id, text, "kein Gefühl", "vermindertes Gefühl", "normal") }
    val position = listOf("schulter" to "Schulter", "ellenbogen" to "Ellenbogen", "handgelenk" to "Handgelenk", "daumen" to "Daumen (IP-Gelenk)").map { (id, joint) ->
        described(
            "h-lage-$id", "Lagesinn (kleine Veränderungen der Position): $joint",
            "Abwesenheit, weniger als 3/4 korrekt", "3/4 korrekt, erheblicher Unterschied", "100% korrekt kleiner oder kein Unterschied",
        )
    }
    val joints = listOf(
        "schulter-flexion" to "Schulter: Flexion (0°-80°)", "schulter-abduktion" to "Schulter: Abduktion (0°-90°)",
        "schulter-aussenrotation" to "Schulter: Außenrotation", "schulter-innenrotation" to "Schulter: Innenrotation",
        "ellenbogen-flexion" to "Ellenbogen: Flexion", "ellenbogen-extension" to "Ellenbogen: Extension",
        "unterarm-pronation" to "Unterarm: Pronation", "unterarm-supination" to "Unterarm: Supination",
        "handgelenk-flexion" to "Handgelenk: Flexion", "handgelenk-extension" to "Handgelenk: Extension",
        "finger-flexion" to "Finger: Flexion", "finger-extension" to "Finger: Extension",
    )
    val motion = joints.map { (id, text) -> described("j-bewegung-$id", text, "nur ein paar Grad (weniger als 10° in der Schulter)", "verringert", "normal") }
    val pain = joints.map { (id, text) ->
        described("j-schmerz-$id", text, "ausgeprägter ständiger Schmerz während oder am Ende der Bewegung", "ein bisschen Schmerz", "kein Schmerz")
    }
    Assessment(
        id = "fugl-meyer",
        name = "Fugl-Meyer Assessment",
        abbreviation = "FMA",
        categories = listOf(AssessmentCategory.Neurology),
        source = "Fugl-Meyer Assessment Score, Stand 10/2026",
        hint = "Nicht validierte deutsche Version.",
        sections = listOf(
            Section("A. Obere Extremität (sitzende Position)", "1) Reflexaktivität", reflexes),
            Section(
                hint = "2) Freiwillige Bewegungen mit Synergien (ohne Hilfe der Schwerkraft)\n\n" +
                    "Beugesynergie: Hand vom kontralateralen Knie zum ipsilateralen Ohr. Von der Extensorsynergie " +
                    "(Schulteradduktion/Innenrotation, Ellbogenverlängerung, Unterarmpronation) bis zur Flexorsynergie " +
                    "(Schulterabduktion/Außenrotation, Ellbogenflexion, Unterarmsupination)",
                items = synergies,
            ),
            Section(hint = "3) Freiwillige Bewegung, Mix mit Synergien, ohne Kompensation", items = mixed),
            Section(hint = "4) Freiwillige Bewegung mit wenig oder keiner Synergie", items = little),
            Section(hint = "5) Normale Reflexaktivität wird nur evaluiert, wenn Punktzahl 6P bei Teil 4 erreicht wurde", items = normalReflexes),
            Section(
                "B. Handgelenk",
                "Am Ellbogen kann Unterstützung bereitgestellt werden, um die Position einzunehmen oder zu halten. Keine " +
                    "Unterstützung am Handgelenk. Überprüfen Sie vor dem Testen den passiven Bewegungsbereich",
                wrist,
            ),
            Section(
                "C. Hand",
                "Unterstützung kann am Ellbogen bereitgestellt werden, um 90° Flexion zu halten, keine Unterstützung am " +
                    "Handgelenk, im Vergleich zu nicht betroffener Hand, die Objekte sind zwischengeschaltet, aktiver Griff",
                hand,
            ),
            Section(
                "D. Koordination/Geschwindigkeit",
                "nach einem Versuch mit beiden Armen, verblindet, Spitze des Zeigefingers vom Knie zur Nase, 5 mal so schnell wie möglich",
                coordination,
            ),
            Section("H. Empfindungen obere Extremität", "verblindet, verglichen mit unbetroffener Seite", touch + position),
            Section("J. passive Gelenkbeweglichkeit, obere Extremität", "sitzende Position, verglichen mit unbetroffener Seite", motion),
            Section("J. Schmerz während passiver Bewegung, obere Extremität", items = pain),
        ),
        scores = listOf(
            higher("a", "A. Obere Extremität", reflexes + synergies + mixed + little + normalReflexes),
            higher("b", "B. Handgelenk", wrist),
            higher("c", "Hand", hand),
            higher("d", "Koordination/Geschwindigkeit", coordination),
            Score("a-d", "Total A-D (motorische Funktion)", emptyList(), direction = Direction.HigherIsBetter, sumOf = listOf("a", "b", "c", "d")),
            higher("h", "H. Empfindung", touch + position),
            higher("j-bewegung", "J. Passive Gelenkbewegung", motion),
            higher("j-schmerz", "Gelenkschmerz", pain),
        ),
    )
}

/** Functional Ambulation Categories, validated German version, as in "Functional-Ambulation-Categories_10-2026.pdf". */
private val FAC: Assessment = run {
    val categories = listOf(
        "Nicht Funktionell / nicht möglich" to "Patient kann nicht gehen oder benötigt Hilfe von 2 oder mehr Personen.",
        "Abhängig Stufe 2" to "Patient benötigt sichere, ständige Unterstützung von einer Person, die hilft das Gewicht zu übernehmen und das Gleichgewicht zu halten.",
        "Abhängig Stufe 1" to "Patient benötigt ständige Intermittierende Unterstützung einer Person für Gleichgewicht oder Koordination.",
        "Abhängig - Aufsicht" to "Patient benötigt verbale Anleitung oder stand-by Hilfe einer Person ohne physischen Kontakt",
        "Unabhängig - auf ebenen Boden" to "Patient kann unabhängig auf ebenen Boden gehen, benötigt aber Hilfe bei Treppen, Hängen oder unebenen Oberflächen.",
        "Unabhängig" to "Patient kann überall selbstständig gehen.",
    )
    val item = ChoiceItem("kategorie", "Kategorie", numbered(*categories.mapIndexed { i, (name, text) -> "$i $name: $text" }.toTypedArray()))
    Assessment(
        id = "fac",
        name = "Functional Ambulation Categories",
        abbreviation = "FAC",
        categories = listOf(AssessmentCategory.Neurology),
        source = "Functional Ambulation Categories, Stand 10/2026",
        hint = "Validierte deutsche Version.\n\n" +
            "Diese Klassifikation berücksichtigt keine Hilfsmittel.\n\n" +
            "Diese Einteilung beschreibt das Ausmaß an Hilfe, welche neurologische Patienten beim Gehen in der Physiotherapie " +
            "benötigen. Daher ist diese Skala besser zur Einschätzung der Rehabilitation einsetzbar und weniger als " +
            "Messinstrument der Behinderung zu deuten. Validität und Reliabilität sind belegt, die Skala ist einfach anzuwenden " +
            "und zeigt Veränderungen im Übergang von Immobilität bis zum Gehen auf.\n" +
            "(Quelle: Wade DT (1995): Measurement in neurological rehabilitation. Oxford medical publications)",
        sections = listOf(Section(items = listOf(item))),
        scores = listOf(
            Score(
                "nummer", "Nummer", listOf(item.id), unit = "", direction = Direction.HigherIsBetter,
                bands = categories.mapIndexed { i, (name, _) -> Band.upTo(i, name) },
            ),
        ),
    )
}

/**
 * (Stroke) Goal Attainment Scale, as in "Goal-Attainment-Scale_10-2026.pdf": its one goal as many as set, and the
 * T-score after Kiresuk and Sherman, which the PDF does not give (09.11.2026).
 */
private val GAS: Assessment = run {
    val goals = GoalsItem(
        "ziele", "Zielvereinbarung Therapeut/Patient*in:", "Ziel",
        scored("+2 Ziel deutlich übertroffen" to 2, "+1 Ziel übertroffen" to 1, "0 Ziel erreicht" to 0, "-1 Unverändert" to -1, "-2 Schlechter" to -2),
    )
    Assessment(
        id = "gas",
        name = "Goal Attainment Scale",
        abbreviation = "GAS",
        categories = listOf(AssessmentCategory.Neurology),
        source = "(Stroke) Goal Attainment Scale, Stand 10/2026",
        hint = "Je Ziel die erreichte Stufe von -2 bis +2.\n\n" +
            "T-Wert nach Kiresuk & Sherman (1968), jedes Ziel gleich gewichtet: T = 50 + 10 · Σx / √(0,7 · n + 0,3 · n²), mit x " +
            "der Stufe je Ziel und n der Zahl der Ziele. 50 heißt: die Ziele im Mittel erreicht.",
        sections = listOf(Section(items = listOf(goals))),
        scores = listOf(Score("t", "T-Wert", listOf(goals.id), rule = ScoreRule.GoalT, unit = "", direction = Direction.HigherIsBetter)),
    )
}

/** Mini-BESTest, German version not validated, as in "Mini-Best-Test_10-2026.pdf"; of 3 and 6 the worse side counts. */
private val MINI_BEST: Assessment = run {
    fun task(id: String, text: String, normal: String, moderate: String, hard: String) =
        ChoiceItem(id, text, scored("(2) Normal: $normal" to 2, "(1) Moderat: $moderate" to 1, "(0) Erschwert: $hard" to 0))
    fun seconds(id: String, text: String) = NumberItem(id, text, unit = "s", decimals = true)
    val step = listOf("Ausgleich durch einen großen, sicheren Schritt.", "Mehr als ein Schritt wurde benötigt.", "Kein Schritt, würde fallen ohne Hilfe oder fällt.")

    val sitToStand = task(
        "1", "1. Vom Sitz in den Stand",
        "Kommt in den Stand, ohne die Hände zu benutzen.", "Kommt in den Stand mit Benutzung der Hände, beim ersten Versuch.",
        "Nicht in der Lage, ohne Hilfe aufzustehen, oder braucht mehrere Versuche mit Hilfe der Hände.",
    )
    val heels = task(
        "2", "2. Fersenheben",
        "Stabil für 3 Sekunden mit maximaler Höhe.",
        "Fersen sind oben, aber nicht die gleiche Höhe, wie wenn mit Hilfe der Hände des Therapeuten, oder nicht stabil innerhalb der 3 Sekunden.",
        "< 3 sec.",
    )
    val oneLeg = listOf("links" to "Links", "rechts" to "Rechts").map { (id, side) ->
        task("3-$id", "3. Einbeinstand – $side", "20 sec.", "< 20 sec.", "Nicht in der Lage, Bein zu heben.")
    }
    val oneLegTimes = listOf("links" to "Links", "rechts" to "Rechts").flatMap { (id, side) ->
        listOf(1, 2).map { n -> seconds("3-$id-$n", "$side: Zeit in Sekunden, Versuch $n") }
    }
    val forward = task("4", "4. Kompensatorische Schrittkorrektur - Vorwärts", step[0], step[1], step[2])
    val backward = task("5", "5. Kompensatorische Schrittkorrektur - Rückwärts", step[0], step[1], step[2])
    val sideways = listOf("links" to "Links", "rechts" to "Rechts").map { (id, side) ->
        task(
            "6-$id", "6. Kompensatorische Schrittkorrektur - Seitlich – $side",
            "Gleicht selbstständig mit einem Schritt aus (Crossover oder Side Step - OK).", "Mehrere Schritte nötig, um auszugleichen.",
            "Fällt oder kann nicht ausgleichen.",
        )
    }
    val eyesOpen = task("7", "7. Stand, Füße eng zusammen, Augen offen, feste Unterlage", "30 sec.", "< 30 sec.", "Nicht möglich.")
    val eyesClosed = task("8", "8. Stand, Füße eng zusammen, Augen geschlossen, feste Unterlage", "30 sec.", "< 30 sec.", "Nicht möglich.")
    val incline = task(
        "9", "9. Anstieg/Erhöhung - Augen geschlossen",
        "30 sec.", "Steht ohne Hilfe < 30 sec. oder kippt auf der Unterlage um.", "Nicht möglich.",
    )
    val speed = task(
        "10", "10. Veränderung Ganggeschwindigkeit",
        "Eindeutige Geschwindigkeitsveränderung, ohne aus dem Gleichgewicht zu kommen.",
        "Nicht in der Lage, die Geschwindigkeit zu ändern oder Zeichen von Gleichgewichtsproblemen.",
        "Nicht in der Lage, die Geschwindigkeit zu ändern UND Gleichgewichtsprobleme.",
    )
    val headTurns = task(
        "11", "11. Gehen mit Kopfdrehungen - Horizontal",
        "Geht mit Kopfdrehungen ohne Änderungen in Ganggeschwindigkeit.", "Geht mit Kopfdrehungen mit reduzierter Geschwindigkeit.",
        "Geht mit Kopfdrehungen und Probleme, die Balance zu halten.",
    )
    val pivot = task(
        "12", "12. Gehen mit Drehungen (ganzer Körper)",
        "Dreht sich schnell (< 3 Schritte), Füße sind eng geschlossen, gute Balance.",
        "Dreht sich langsam (> 4 Schritte), Füße sind eng geschlossen, gute Balance.",
        "Nicht möglich zu drehen, ohne die Füße geschlossen zu halten, ohne Gleichgewichtsprobleme.",
    )
    val obstacle = task(
        "13", "13. Über Hindernisse steigen",
        "In der Lage über das Hindernis zu steigen, minimale Geschwindigkeitsveränderung und gute Balance.",
        "In der Lage über das Hindernis zu steigen, berührt es allerdings oder zeigt vorsichtiges Verhalten in Form von Geschwindigkeitsreduktion im Gang.",
        "Nicht in der Lage über das Hindernis zu steigen oder geht um das Hindernis herum.",
    )
    val dualTask = task(
        "14", "14. Timed Up & Go mit Dual Task",
        "Kein Unterschied im Sitzen, Gehen, Stehen zwischen Rückwärts zählen und TUG & TUG ohne Dual Task.",
        "Dual Task nimmt Einfluss auf Gehen oder Zählen (>10%) im Vergleich zum TUG ohne Dual Task.",
        "Hört auf zu Gehen beim Zählen oder hört auf zu Zählen beim Gehen.",
    )
    val tug = listOf(NumberItem("14-zaehlen", "Rückwärts zählen von"), seconds("14-tug", "TUG Sekunden"), seconds("14-dual", "Dual Task TUG Sekunden"))

    val anticipation = listOf(sitToStand, heels) + oneLeg
    val reactive = listOf(forward, backward) + sideways
    val sensory = listOf(eyesOpen, eyesClosed, incline)
    val gait = listOf(speed, headTurns, pivot, obstacle, dualTask)
    fun pairOf(items: List<Item>) = listOf(ids(items))
    Assessment(
        id = "mini-bestest",
        name = "Mini-BESTest",
        categories = listOf(AssessmentCategory.Neurology),
        source = "Mini Best Test, Stand 10/2026",
        hint = "Nicht validierte deutsche Version.\n\n" +
            "Rahmenbedingungen: Testpersonen sollten flache Schuhe tragen oder die Tests ohne Socken durchführen.\n\n" +
            "Equipment: Stuhl ohne Lehne und ohne Rollen, ansteigende Erhöhung, Stoppuhr, Hindernis (Box) - ca. 22 cm hoch, " +
            "Maßband für 3 Meter, Marker (Tape) oder Hütchen.\n\n" +
            "Maximal sind 28 Punkte aus 14 Aufgaben zu erreichen. Pro Aufgabe gibt es 0-2 Punkte zu holen. Hierbei steht 0 für " +
            "das schlechteste und 2 Punkte für das beste funktionelle Ergebnis. Wenn die Testperson ein Hilfsmittel bei der " +
            "Durchführung der Aufgaben braucht, wird das notiert.\n\n" +
            "Für Aufgabe 3 zählt man nur den Score von einer Seite zusammen, nämlich von der Seite mit dem schlechteren Score, " +
            "allerdings dabei nur den besseren Versuch. Für Aufgabe 6 zählt dasselbe, d.h. man zählt nur eine Seite und zwar die " +
            "mit dem schlechteren Score. Für Aufgabe 14 zieht man bei einer Abweichung von mehr als 10% zwischen TUG mit und " +
            "ohne Dual Task einen Punkt ab bei der Bewertung.",
        sections = listOf(
            Section(
                "Antizipation",
                "1. Anleitung: „Verschränken Sie Ihre Arme vor Ihrer Brust. Benutzen Sie nicht Ihre Hände beim Aufstehen und lehnen " +
                    "Sie nicht Ihren Rücken gegen die Lehne des Stuhls.“\n\n" +
                    "2. Anleitung: „Stellen Sie Ihre Füße ca. schulterbreit nebeneinander. Heben Sie die Fersen so weit Sie können " +
                    "und halten Sie diese Position für mindestens 3 Sekunden.“",
                listOf(sitToStand, heels),
            ),
            Section(
                "3. Einbeinstand",
                "(Benötigt Stoppuhr) (Werte nur die Seite mit dem schlechteren Score, zähle nur den besseren Versuch)\n\n" +
                    "Anleitung: „Nehmen Sie die Hände an Ihre Hüften, schauen Sie geradeaus und heben Sie ein Bein. Das Bein, das " +
                    "gehoben wird, darf nicht den Boden berühren.“",
                oneLegTimes + oneLeg,
            ),
            Section(
                "Reaktive Gleichgewichtskontrolle",
                "4. Anleitung: „Stehen Sie mit Ihren Füßen schulterbreit. Die Arme hängen an den Seiten herunter. Lehnen Sie sich " +
                    "nach vorne gegen meine Hände, soweit es für Sie möglich ist. Versuchen Sie höchstens einen Schritt umzusetzen, um " +
                    "beispielsweise einen Fall zu verhindern.“\n\n" +
                    "5. Anleitung: „Stehen Sie mit Ihren Füßen schulterbreit. Die Arme hängen an den Seiten herunter. Lehnen Sie sich " +
                    "nach hinten gegen meine Hände, soweit es für Sie möglich ist.“\n\n" +
                    "6. (Werte nur den schlechteren Score) Anleitung: „Stehen Sie mit Ihren Füßen schulterbreit. Die Arme hängen an " +
                    "den Seiten herunter. Lehnen Sie sich zur Seite gegen meine Hände, soweit es für Sie möglich ist.“",
                reactive,
            ),
            Section(
                "Sensorische Orientierung",
                "(Stoppuhr benötigt)\n\n" +
                    "7. Anleitung: „Legen Sie Ihre Hände an die Hüfte, stellen Sie Ihre Füße so eng wie möglich zusammen und schauen " +
                    "Sie geradeaus. Bleiben Sie so stabil stehen wie möglich, bis ich „Stop“ sage.“\n\n" +
                    "8. Anleitung: „Legen Sie Ihre Hände an die Hüfte, stellen Sie Ihre Füße so eng wie möglich zusammen und " +
                    "schließen Sie die Augen. Bleiben Sie so stabil stehen wie möglich, bis ich „Stop“ sage.“\n\n" +
                    "9. Anleitung: „Stellen Sie sich bitte auf die Erhöhung mit den Zehen in Richtung höchste Stelle. Die Arme " +
                    "hängen seitlich herunter und die Augen sind geschlossen.“",
                listOf(
                    seconds("7-zeit", "7. Zeit in Sekunden"), eyesOpen,
                    seconds("8-zeit", "8. Zeit in Sekunden"), eyesClosed,
                    seconds("9-zeit", "9. Zeit in Sekunden"), incline,
                ),
            ),
            Section(
                "Dynamische Testung Gang",
                "10. Anleitung: „Beginnen Sie in Ihrem normalen Tempo zu gehen. Wenn ich „schnell“ sage, laufen Sie so schnell wie " +
                    "möglich. Wenn ich „langsam“ sage, laufen Sie sehr langsam.“\n\n" +
                    "11. Anleitung: „Gehen Sie in Ihrem normalen Tempo los. Wenn ich „rechts“ sage, drehen Sie Ihren Kopf nach " +
                    "rechts, wenn ich „links“ sage, drehen Sie Ihren Kopf nach links, während Sie in einer Linie gehen.“\n\n" +
                    "12. Anleitung: „Gehen Sie in Ihrem normalen Tempo los. Wenn ich „drehen“ sage, drehen Sie sich in die " +
                    "entgegengesetzte Richtung und halten an. Wenn Sie stehen, sollten Ihre Füße eng zusammen stehen.“\n\n" +
                    "13. Anleitung: „Gehen Sie in Ihrem normalen Tempo los. Wenn Sie bei dem Hindernis angekommen sind, steigen Sie " +
                    "darüber, gehen Sie nicht drumherum.“",
                listOf(speed, headTurns, pivot, obstacle),
            ),
            Section(
                "14. Timed Up & Go mit Dual Task",
                "(3 Meter) (Bei einer Abweichung von mehr als 10% bei TUG mit und ohne Dual Task zieht man einen Punkt ab bei der " +
                    "Bewertung)\n\n" +
                    "Anleitung: „Wenn ich „los“ sage, stehen Sie vom Stuhl auf und gehen in Ihrem normalen Tempo bis zur Markierung, " +
                    "drehen sich um und setzen sich wieder auf den Stuhl.“\n\n" +
                    "Anleitung mit Dual Task: „Zählen Sie rückwärts von ______ während Sie vom Stuhl aufstehen, in Ihrem normalen " +
                    "Tempo bis zur Markierung gehen, sich umdrehen und sich wieder hinsetzen. Zählen Sie währenddessen weiter.“",
                tug + dualTask,
            ),
        ),
        scores = listOf(
            Score("antizipation", "Antizipation", ids(anticipation), direction = Direction.HigherIsBetter, worseOf = pairOf(oneLeg)),
            Score("reaktiv", "Reaktive Gleichgewichtskontrolle", ids(reactive), direction = Direction.HigherIsBetter, worseOf = pairOf(sideways)),
            higher("sensorisch", "Sensorische Orientierung", sensory),
            higher("gang", "Dynamische Testung Gang", gait),
            Score("total", "Total Score", emptyList(), direction = Direction.HigherIsBetter, sumOf = listOf("antizipation", "reaktiv", "sensorisch", "gang")),
        ),
    )
}

/**
 * Modified Fatigue Impact Scale, all 21 statements (09.11.2026): 1 to 7 as in DIGOTOR's "Modified-Fatigue-Impact-Scale_10-2026.pdf",
 * which has no more; 8 to 21 as in the German version in the appendix of Hopfgartner's thesis (LMU München), which lacks
 * the original's 19, here in words of our own.
 */
private val MFIS: Assessment = run {
    val answers = numbered("Niemals", "Selten", "Manchmal", "Oft", "So gut wie immer")
    val items = listOf(
        "... war ich weniger wachsam.",
        "... hatte ich Probleme aufmerksam zu sein.",
        "... war ich nicht in der Lage, klar zu denken.",
        "... war ich ungeschickt und unkoordiniert.",
        "... war ich vergesslich.",
        "... musste ich mich in meinen physischen Aktivitäten selbst antreiben.",
        "... war ich weniger motiviert, etwas zu tun, das physische Anstrengung erfordert.",
        "... war ich wenig motiviert, an sozialen Aktivitäten teilzunehmen.",
        "... war ich limitiert, Sachen außer Haus zu tun.",
        "... hatte ich Schwierigkeiten, körperliche Anstrengungen über längere Zeit durchzuhalten.",
        "... hatte ich Schwierigkeiten, Entscheidungen zu treffen.",
        "... war ich wenig motiviert, Sachen zu tun, bei denen ich mich konzentrieren musste.",
        "... habe ich mich schwach gefühlt.",
        "... habe ich mich körperlich nicht wohl gefühlt.",
        "... hatte ich Schwierigkeiten, Sachen zu beenden, bei denen ich mich konzentrieren muss.",
        "... hatte ich Schwierigkeiten, meine Gedanken zu Hause oder bei der Arbeit zusammenzuhalten.",
        "... war ich nicht in der Lage, Dinge, die körperliche Anstrengung erfordern, zu beenden.",
        "... war mein Denken verlangsamt.",
        "... hatte ich Schwierigkeiten, mich zu konzentrieren.",
        "... habe ich meine körperlichen Aktivitäten eingeschränkt.",
        "... habe ich häufige oder längere Pausen gebraucht.",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", answers) }
    fun of(vararg numbers: Int) = numbers.map { items[it - 1] }
    Assessment(
        id = "mfis",
        name = "Modified Fatigue Impact Scale",
        abbreviation = "MFIS",
        categories = listOf(AssessmentCategory.Neurology),
        source = "Modified Fatigue Impact Scale, Stand 10/2026; Aussagen 8 bis 21 nach Hopfgartner (LMU München)",
        hint = "Nicht validierte deutsche Version.\n\n" +
            "Hier ist eine Liste von Aussagen, die beschreiben, wie Fatigue eine Person beeinflussen kann.\n\n" +
            "Fatigue ist ein Gefühl von physischer Müdigkeit und Verlust an Energie. Diese Erfahrung machen viele Menschen immer " +
            "wieder.\n\n" +
            "Im Falle der MS kann dieses Gefühl der Müdigkeit öfter eintreten und einen größeren Einfluss auf die Lebensqualität " +
            "haben als gewöhnlich.\n\n" +
            "Bitte lesen Sie jede Aussage bedacht und kreisen Sie die Aussage ein, die am ehesten auf Ihre Symptomatik zutrifft, " +
            "die in den letzten 4 Wochen auftrat. (Bei Fragen nicht zögern und den Interviewer bitte nach Hilfe fragen.)\n\n" +
            "Die Modified Fatigue Impact Scale-5-Artikelversion (MFIS-5) besteht aus den 5 MFIS-Artikeln, die am stärksten mit " +
            "dem Gesamt-MFIS-Score korrelieren. Die folgenden Elemente aus dem MFIS bilden das MFIS-5: Elemente 1, 9, 10, 17 und " +
            "19. Daher werden Elemente aus allen drei Subskalen dargestellt. Die MFIS-5-Gesamtpunktzahl besteht aus der Summe " +
            "der Rohpunktzahlen für diese 5 Elemente und kann daher zwischen 0 und 20 liegen.",
        sections = listOf(Section(title = "Aufgrund der Fatigue in den letzten 4 Wochen...", items = items)),
        scores = listOf(
            lowerSum("koerperlich", "Körperlich", of(4, 6, 7, 10, 13, 14, 17, 20, 21)),
            lowerSum("kognitiv", "Kognitiv", of(1, 2, 3, 5, 11, 12, 15, 16, 18, 19)),
            lowerSum("psychosozial", "Psychosozial", of(8, 9)),
            Score("gesamt", "Gesamt", emptyList(), direction = Direction.LowerIsBetter, sumOf = listOf("koerperlich", "kognitiv", "psychosozial")),
            lowerSum("mfis-5", "MFIS-5", of(1, 9, 10, 17, 19)),
        ),
    )
}

/** MOS Schmerz Effekt Skala (engl. PES), German version not validated, as in "Medical-Outcomes-Study-–-Pain-Effects-Scale_MOS-PES_10-2026.pdf". */
private val MOS_PES: Assessment = run {
    val answers = scored("überhaupt nicht" to 1, "ein bisschen" to 2, "moderat" to 3, "ziemlich viel" to 4, "zu einem extremen Grad" to 5)
    val items = listOf(
        "Stimmung", "Möglichkeit zu gehen oder sich zu bewegen", "Zu schlafen", "Normal zu arbeiten (draußen oder zu Hause)",
        "Freizeitaktivitäten", "Lebensfreude",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", answers) }
    Assessment(
        id = "mos-pes",
        name = "MOS Schmerz-Effekt-Skala",
        abbreviation = "MOS-PES",
        categories = listOf(AssessmentCategory.Neurology),
        source = "MOS-PES, Stand 10/2026",
        hint = "Nicht validierte deutsche Version.\n\n" +
            "Personen mit MS können als Ergebnis ihrer Erkrankung hin und wieder unerwünschte sensorische Symptome haben (z.B. " +
            "Schmerz, Kribbeln, Brennen). Die unten stehenden Fragen beziehen sich auf Ihre Schmerzen und darauf, wie diese sich " +
            "auf Ihre Stimmung auswirken.\n\n" +
            "Bitte kreisen Sie die Nummer (0, 1, 2,…), je nachdem wie stark die Symptome, einschließlich Schmerz in den letzten 4 " +
            "Wochen mit Ihrem täglichen Leben interferierte, ein.\n\n" +
            "Falls Sie Hilfe beim Einkreisen Ihrer Antwort brauchen, fragen Sie Ihren Therapeuten nach der besten Antwort.\n\n" +
            "Bitte beantworten Sie jede Frage. Falls Sie sich nicht sicher sind, für welche Antwort Sie sich entscheiden sollen, " +
            "nehmen Sie jene, die Ihrer Antwort am nächsten kommt.\n\n" +
            "Die Rohwerte für die 6 Elemente, aus denen sich diese Skala zusammensetzt, werden einfach zu einem PES-Gesamtwert " +
            "addiert. Somit können die Punktzahlen zwischen 6 und 30 liegen. Die Elemente werden so skaliert, dass höhere Werte " +
            "einen stärkeren Einfluss von Schmerzen auf die Stimmung und das Verhalten eines Patienten anzeigen.",
        sections = listOf(
            Section(
                hint = "Wie sehr wurden folgende Aussagen durch die genannten Symptome (Schmerz, Kribbeln, Brennen) in den letzten 4 Wochen gestört:",
                items = items,
            ),
        ),
        scores = listOf(lowerSum("gesamt", "PES-Gesamtwert", items)),
    )
}

/**
 * Rapid Screening for depression in multiple sclerosis, German version not validated, as in
 * "Rapid-Screening-for-depression-in-multiple-sclerosis_10-2026.pdf": the nine symptoms shown once either question is yes,
 * the first two of them apart, as one of them is needed.
 */
private val MS_DEPRESSION: Assessment = run {
    val screening = listOf(
        YesNoItem("1", "1. Haben Sie sich in den letzten 2 Wochen niedergeschlagen, deprimiert oder hoffnungslos gefühlt?"),
        YesNoItem("2", "2. Haben Sie in den letzten 2 Wochen wenig Interesse oder Freude daran gehabt, Dinge zu erledigen?"),
    )
    val either = Condition.ifYes("1") or Condition.ifYes("2")
    fun symptoms(vararg labels: String, first: Int) = labels.mapIndexed { i, label -> Option("${first + i}", "${first + i}. $label", 1) }
    val core = ChecklistItem(
        "kern", "Symptome 1 und 2", symptoms("Depressive Stimmung", "Verlust an Interesse und Freude", first = 1), scored = true, showIf = either,
    )
    val further = ChecklistItem(
        "weitere", "Symptome 3 bis 9",
        symptoms(
            "Signifikanter Gewichtsverlust (ohne Diät) oder Gewichtssteigerung oder verminderter Appetit",
            "Schlaflosigkeit oder übermäßige Schläfrigkeit",
            "Psychomotorische Unruhe oder Retardierung",
            "Fatigue oder Verlust an Energie",
            "Gefühl der Wertlosigkeit, Gefühl fehl am Platz zu sein oder Gefühl der übermäßigen Schuld",
            "Verminderte Fähigkeit zu denken, sich zu konzentrieren, Gefühl der Unentschlossenheit",
            "Wiederkehrende Gedanken an den eigenen Tod oder Selbstmord",
            first = 3,
        ),
        scored = true, showIf = either,
    )
    Assessment(
        id = "ms-depression",
        name = "Rapid Screening for depression in multiple sclerosis (MS)",
        categories = listOf(AssessmentCategory.Neurology),
        source = "Rapid Screening for depression in multiple sclerosis, Stand 10/2026",
        hint = "Schnelles Screening für Depression bei Multipler Sklerose (MS). Nicht validierte deutsche Version.",
        sections = listOf(
            Section("Das Zwei initiale Fragen Screening - Differentialdiagnostik Depression:", items = screening),
            Section(
                hint = "Falls eine oder beide Fragen mit „Ja“ beantwortet werden: Evaluiere Major Depression basieren auf folgenden " +
                    "DSM-IV Kriterien:\n\n" +
                    "Fünf oder mehr der gelisteten neun Symptome waren in den letzten zwei Wochen präsent und waren vorher nicht da. " +
                    "Mindestens ein Symptom ist deprimierte Stimmung, Verlust an Interesse oder Freude.",
                items = listOf(core, further),
            ),
        ),
        scores = listOf(
            lowerSum("screening", "Screening", screening, listOf(Band.upTo(0, "negativ"), Band.upTo(2, "positiv"))),
            lowerSum("symptome", "Symptome", listOf(core, further)),
            lowerSum("kern", "Symptome 1 und 2", listOf(core)),
            Score(
                "dsm-iv", "Major Depression nach DSM-IV", emptyList(), unit = "", direction = Direction.LowerIsBetter,
                bands = listOf(Band.upTo(0, "Kriterien nicht erfüllt"), Band.upTo(1, "Kriterien erfüllt")),
                grades = listOf(Grade(1, Bound("symptome", from = 500), Bound("kern", from = 100)), Grade(0)),
            ),
        ),
    )
}
