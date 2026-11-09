package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Untere Extremität", second part: foot, ankle, Achilles tendon and the rest (#69),
 * each as in its PDF of the date in its source.
 */
internal val FOOT_ANKLE: List<Assessment> by lazy {
    listOf(ALR_RSI, ANKLE_GO, ATRS, CAIT, FAAM_G, FADI, FAOS, LEFS, PGQ, RRS, SEFAS, VISA_A, VISA_G)
}

/** A score of [items], better the higher, all of them needed unless [minAnswered] says less. */
private fun higherBetter(id: String, name: String, items: List<Item>, rule: ScoreRule = ScoreRule.Sum, unit: String = "Punkte", bands: List<Band> = emptyList(), minAnswered: Int = items.size) =
    ItemScore(id, name, ids(items), rule = rule, unit = unit, direction = Direction.HigherIsBetter, bands = bands, minAnswered = minAnswered)

/** Achilles Tendon Total Rupture Score, German version (Arbab 2024), as in "…_ATRS_10-2026.pdf". */
private val ATRS: Assessment = run {
    val items = listOf(
        "Sind Sie aufgrund verminderter Kraft in Ihrer Achillessehne/Fuß eingeschränkt?",
        "Sind Sie aufgrund einer Ermüdung in Ihrer Achillessehne/Fuß eingeschränkt?",
        "Sind Sie aufgrund einer Steife in Ihrer Achillessehne/Fuß eingeschränkt?",
        "Sind Sie aufgrund von Schmerzen in Ihrer Achillessehne/Fuß eingeschränkt?",
        "Sind Sie in Ihren Tätigkeiten des alltäglichen Lebens eingeschränkt?",
        "Sind Sie eingeschränkt, sobald Sie auf unebenen Boden gehen?",
        "Sind Sie eingeschränkt, wenn Sie schnell Stufen oder einen Hügel hinaufgehen?",
        "Sind Sie eingeschränkt durch Aktivitäten die Laufen beinhalten?",
        "Sind Sie eingeschränkt durch Aktivitäten die Springen beinhalten?",
        "Sind Sie eingeschränkt bei der Durchführung anstrengender körperlicher Arbeit?",
    ).mapIndexed { i, text -> ScaleItem("${i + 1}", "${i + 1}. $text", 0, 10, "maximale Einschränkung", "keine Einschränkung") }
    Assessment(
        id = "atrs",
        name = "Achilles Tendon Total Rupture Score",
        abbreviation = "ATRS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "ATRS, Stand 10/2026",
        hint = "Validierte deutsche Version (Arbab 2024).\n\nDer validierte deutsche Version des ATRS umfasst zehn Fragen. Diese " +
            "beziehen sich auf Ihre Einschränkungen/Symptome bezüglich Ihrer Achillessehnenverletzung. Beantworten Sie bitte " +
            "jede Frage durch Zuordnung Ihre Einschränkungen/Symptome auf einer Skala von 0 bis 10. 0 bedeutet maximale " +
            "Einschränkung, 10 bedeutet keine Einschränkung.\n\n" +
            "Höhere Werte gehen mit einer geringeren Einschränkung bzw. höheren Funktion des Sprunggelenks/der Achillessehne einher.",
        sections = listOf(Section(items = items)),
        scores = listOf(higherBetter("gesamt", "Gesamtpunktzahl", items)),
    )
}

/** Ankle Ligament Reconstruction – Return to Sport after Injury, not validated German version, as in "…_ALR-RSI-Skala_11-2026.pdf". */
private val ALR_RSI: Assessment = run {
    val confident = "Überhaupt nicht zuversichtlich" to "völlig zuversichtlich"
    val items = listOf(
        "Sind Sie zuversichtlich, dass Sie den Sport auf demselben Niveau wie vorher ausüben können?" to confident,
        "Halten Sie es für wahrscheinlich, Ihr Sprunggelenk durch die Teilnahme an Ihrem Sport wieder verletzen zu können?" to ("Sehr wahrscheinlich" to "völlig unwahrscheinlich"),
        "Macht Sie der Gedanke an die Ausübung Ihres Sports nervös?" to ("Sehr nervös" to "gar nicht nervös"),
        "Sind Sie zuversichtlich, dass Ihr Sprunggelenk bei der Ausübung Ihres Sports nicht nachgeben wird?" to confident,
        "Sind Sie zuversichtlich, dass Sie Ihren Sport ausüben können, ohne Bedenken wegen Ihres Sprunggelenks zu haben?" to confident,
        "Finden Sie es frustrierend, dass Sie in Bezug auf Ihren Sport Ihr Sprunggelenk berücksichtigen müssen?" to ("Sehr frustrierend" to "Überhaupt nicht frustrierend"),
        "Befürchten Sie, dass Sie Ihr Sprunggelenk bei der Ausübung Ihres Sports wieder verletzen könnten?" to ("Habe große Sorge" to "Habe überhaupt keine Sorge"),
        "Sind Sie zuversichtlich, dass Ihr Sprunggelenk unter Belastung standhält?" to confident,
        "Haben Sie Angst, dass Sie Ihr Sprunggelenk bei der Ausübung Ihres Sports versehentlich wieder verletzen könnten?" to ("Habe große Angst" to "Habe überhaupt keine Angst"),
        "Hält Sie der Gedanke daran, nochmals operiert und nachbehandelt werden zu müssen, davon ab, Ihren Sport auszuüben?" to ("Die ganze Zeit" to "Zu keinem Zeitpunkt"),
        "Sind Sie zuversichtlich, Ihren Sport gut ausüben zu können?" to confident,
        "Sind Sie gelassen, wenn es um die Ausübung Ihres Sports geht?" to ("Gar nicht gelassen" to "völlig gelassen"),
    ).mapIndexed { i, (text, ends) -> ScaleItem("${i + 1}", "${i + 1}. $text", 0, 100, ends.first, ends.second, step = 10) }
    Assessment(
        id = "alr-rsi",
        name = "Ankle Ligament Reconstruction-Return to Sport after Injury",
        abbreviation = "ALR-RSI",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "ALR-RSI-Skala, Stand 11/2026",
        hint = "Nicht validierte deutsche Version, Sigonney et al. 2020.\n\nSehr geehrte/-r Patient/-in, der folgende Fragebogen " +
            "dient der Erfassung von Beschwerden und Problemen, die durch Ihr Sprunggelenk verursacht werden. Bitte " +
            "beantworten Sie folgende Fragen zu der Sportart, die Sie als Hauptsportart vor Ihrer Verletzung ausgeübt haben. " +
            "Kreuzen Sie für jede Frage dasjenige Kästchen zwischen den beiden beschriebenen Extremen an, das Ihr derzeitiges " +
            "Befinden wiedergibt.\n\n" +
            "Die Werte jeder Frage werden zu einem Gesamtscore addiert mit 100 multipliziert und durch den Maximalscore " +
            "dividiert: Score x 100 / 120 = Ergebnis. Das Ergebnis im ALR-RSI ist signifikant mit der Funktion im AOFAS Score " +
            "korreliert. Der Score ist dazu geeignet zwischen Sportler*innen mit und ohne einem erfolgreichen Return to Sport " +
            "zu diskriminieren. Sportler*innen mit einem erfolgreichen Return to Sport erreichen im Vergleich zu " +
            "Sportler*innen mit einem erfolglosen RTS höhere Werte: 68.8 (56.5–86.5) vs 45.0 (31.3–55.8). Ein Zusammenhang " +
            "zu funktionellen Werten kann, muss aber nicht bestehen. Patienten, die in physischen Messverfahren gute bis sehr " +
            "gute Werte erreichen, können daher zusätzlich mit Informationen oder verhaltenstherapeutischen Ansätzen " +
            "behandelt werden.",
        sections = listOf(Section(items = items)),
        // The original counts each question 0 to 10, of 120; its ticks of 0 to 100 come to the same percent.
        scores = listOf(higherBetter("ergebnis", "Ergebnis", items, rule = ScoreRule.Percent, unit = "%")),
    )
}

/** Ankle-GO Score (Picot et al., 2024), as in "Ankle-GO-Score_10-2026.pdf": tests and questionnaires, each by its range. */
private val ANKLE_GO: Assessment = run {
    fun stable(id: String) = YesNoItem("$id-stabil", "kein Instabilitätsgefühl (+1)")
    val sls = listOf(ChoiceItem("sls", "Ausgangswert", scored("> 3 Fehler" to 0, "1 - 3 Fehler" to 1, "0 Fehler" to 2)), stable("sls"))
    val sebt = listOf(
        ChoiceItem("sebt", "Ausgangswert", scored("< 90 %" to 0, "90 - 95 %" to 2, "> 95 %" to 4)),
        YesNoItem("sebt-ant", "anterior (ANT) > 60 % (+1)"),
        YesNoItem("sebt-pm", "posteromedial (PM) > 90 % (+1)"),
        stable("sebt"),
    )
    val sht = listOf(ChoiceItem("sht", "Ausgangswert", scored("> 13 s" to 0, "10 - 13 s" to 2, "< 10 s" to 4)), stable("sht"))
    val f8t = listOf(ChoiceItem("f8t", "Ausgangswert", scored("> 18 s" to 0, "13 - 18 s" to 1, "< 13 s" to 2)), stable("f8t"))
    val faam = listOf(
        ChoiceItem("faam-adl", "Foot and Ankle Ability Measure (FAAM) – Activities of Daily Living", scored("< 90 %" to 0, "90 - 95 %" to 1, "> 95 %" to 2)),
        // The original reads "> 80 %" for the least, a slip for "< 80 %".
        ChoiceItem("faam-sport", "Foot and Ankle Ability Measure (FAAM) – Sport", scored("< 80 %" to 0, "80 - 95 %" to 1, "> 95 %" to 2)),
    )
    val alrRsi = ChoiceItem(
        "alr-rsi", "Ankle ligament reconstruction-return to sport after injury (ALR-RSI)",
        scored("< 55 %" to 0, "55 - 63 %" to 1, "63 - 76 %" to 2, "> 76 %" to 3),
    )
    val all = sls + sebt + sht + f8t + faam + alrRsi
    Assessment(
        id = "ankle-go",
        name = "Ankle-GO Score",
        abbreviation = "Ankle-GO",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "Ankle-GO Score, Stand 10/2026",
        hint = "Picot et al., 2024.\n\nDer Ankle-GO Score bietet eine verlässliche und objektive Grundlage für die Einschätzung der " +
            "Rückkehr zum Sport nach einer lateralen Sprunggelenksverletzung.\n\n" +
            "Der Ankle-GO ist ein zusammengesetzter Score, der auf der Summe von sieben Komponenten basiert und eine objektive " +
            "Bewertung der Hauptdefizite im Zusammenhang mit einer lateralen Sprunggelenksdistorsion (LAS) oder einer " +
            "chronischen Sprunggelenksinstabilität (CAI) ermöglicht. Er wurde aus vier funktionellen Tests berechnet: dem " +
            "Einbeinstand (Single Leg Stance, SLS) auf fester Oberfläche, dem modifizierten Star Excursion Balance Test (mSEBT), " +
            "dem Side Hop Test (SHT) und dem Figure-of-8-Test (F8T). Zusätzlich wurden zwei patientenberichtete Fragebögen " +
            "verwendet: das Foot and Ankle Ability Measure (FAAM), das zwei Subskalen zur Bewertung von Aktivitäten des täglichen " +
            "Lebens (FAAMadl) und sportlichen Aktivitäten (FAAMsport) umfasst, sowie das Ankle Ligament Reconstruction–Return to " +
            "Sport after Injury (ALR-RSI).\n\n" +
            "Ein höherer Wert im Ankle-GO Score bedeutet bessere Funktion, bessere Balance/Kontrolle, bessere subjektive " +
            "Wahrnehmung und ein geringeres Risiko für eine erneute Verletzung hin und damit eine bessere Ausgangslage für z. B. " +
            "die Rückkehr zum Sport. Ein Wert unter 8 Punkten nach 2 Monaten deutet darauf hin, dass eine Rückkehr auf das " +
            "ursprüngliche sportliche Niveau nach 4 Monaten eher unwahrscheinlich ist.",
        sections = listOf(
            Section(
                "Einbeinstand (Single Leg Stance, SLS) auf stabiler Oberfläche – maximal 3 Punkte",
                "Die Testperson steht barfuß auf einem Bein, das Knie ist leicht gebeugt (10°), die Hände liegen auf den Hüften, " +
                    "und die Augen bleiben 20 Sekunden lang geschlossen. Der Test bewertet die statische posturale Kontrolle " +
                    "anhand der Anzahl der Fehler. Ein Fehler wird registriert, wenn z. B. die Hände von den Hüften genommen " +
                    "werden, der Oberschenkel mehr als 30° flektiert oder abduziert wird, die Ferse oder der Vorfuß angehoben " +
                    "wird, die Testposition länger als 5 Sekunden verlassen wird oder die Augen geöffnet werden (Riemann et al., " +
                    "1999). Nach zwei Übungssitzungen wird der Test einmal pro Bein durchgeführt.",
                sls,
            ),
            Section(
                "Modifizierter Star Excursion Balance Test (mSEBT) – maximal 7 Punkte",
                "Die Testperson steht barfuß auf dem zu testenden Bein im Zentrum eines Y-förmigen Markers und erreicht mit dem " +
                    "anderen Bein so weit wie möglich in drei Richtungen: anterior (ANT), posteromedial (PM) und posterolateral " +
                    "(PL). Die Messung wird abgebrochen, wenn das Gleichgewicht verloren geht, die Hände die Hüften verlassen oder " +
                    "das Standbein bewegt wird. Die erreichten Distanzen werden im Verhältnis zur Beinlänge normalisiert. Nach " +
                    "vier Übungsversuchen werden drei Durchgänge pro Richtung aufgezeichnet und gemittelt. Ein Punkt wird " +
                    "hinzugefügt, wenn der Wert in der ANT-Richtung über 60 % und ein weiterer, wenn der Wert in der PM-Richtung " +
                    "über 90 % liegt (Rosen et al., 2019).",
                sebt,
            ),
            Section(
                "Side hop Test (SHT) – maximal 5 Punkte",
                "Dieser Test umfasst zehn seitliche Sprünge zwischen zwei Linien mit 30 cm Abstand, so schnell wie möglich. Der " +
                    "erste Sprung erfolgt nach außen. Berührt die Testperson eine Linie, wird dieser Durchgang nicht gewertet " +
                    "(Docherty et al., 2005).",
                sht,
            ),
            Section(
                "Figure-of-8-Test (F8T) – maximal 3 Punkte",
                "Die Testperson springt in einer 8-förmigen Bahn um zwei 5 m voneinander entfernte Stangen, so schnell wie " +
                    "möglich. Zwei Runden (insgesamt 20 m) werden absolviert. Ein zusätzlicher Punkt wird vergeben, wenn keine " +
                    "Instabilitätsgefühle während des Tests auftreten (Caffrey et al., 2009).",
                f8t,
            ),
            Section(
                "Patienten-Fragebögen (PROMs)",
                "Foot and Ankle Ability Measure (FAAM), insgesamt 4 Punkte: Dieses Instrument bewertet die vom Patienten " +
                    "berichtete Funktion und besteht aus zwei Subskalen (je 2 Punkte): 21 Items zur Bewertung der " +
                    "Alltagsaktivitäten (FAAMadl) und 8 Items zu sportlichen Aktivitäten (FAAMsport) (Carcia et al., 2008). Die " +
                    "Patientinnen und Patienten beantworten jedes Item auf einer 5-Punkte-Skala (0 = unfähig, die Aktivität " +
                    "auszuführen; 4 = ohne Schwierigkeiten) oder wählen „nicht anwendbar“, wenn die Einschränkung nicht durch Fuß " +
                    "oder Sprunggelenk bedingt ist. Der Prozentsatz jeder Subskala wird anschließend berechnet.\n\n" +
                    "Ankle Ligament Reconstruction–Return to Sport after Injury (ALR-RSI), 3 Punkte: Dieser Fragebogen misst die " +
                    "psychologische Bereitschaft von Patientinnen und Patienten mit Sprunggelenksverletzungen, in den Sport " +
                    "zurückzukehren. Er umfasst 12 Fragen mit einer Bewertungsskala von 0 (kein Vertrauen) bis 10 (vollständiges " +
                    "Vertrauen). Die Gesamtsumme wird durch 1,2 geteilt, um einen Prozentsatz zu erhalten (Sigonney et al., 2020).",
                faam + alrRsi,
            ),
        ),
        scores = listOf(
            higherBetter(
                "gesamt", "Ankle-GO Gesamt", all,
                bands = listOf(Band.upTo(7, "Ein Wert unter 8 Punkten identifiziert Betroffene mit einem signifikant höheren Verletzungsrisiko."), Band.upTo(25, null)),
            ),
        ),
    )
}

/** Cumberland Ankle Instability Tool, German version (Gehring et al. 2016), as in "Cumberland-Ankle-Instability-Tool_11-2026.pdf". */
private val CAIT: Assessment = run {
    // Each scored from the top down to 0; the last of 8 and 9, never having turned the ankle, as 3.
    val questions = listOf(
        "Ich habe Schmerzen in meinem Sprunggelenk:" to listOf(
            "Nie", "Beim Sport", "Beim Rennen auf unebenem Untergrund", "Beim Rennen auf ebenem Untergrund",
            "Beim Gehen auf unebenem Untergrund", "Beim Gehen auf ebenem Untergrund",
        ),
        "Mein Sprunggelenk fühlt sich INSTABIL an:" to listOf(
            "Nie", "Manchmal während des Sports (nicht jedes Mal)", "Regelmäßig während des Sports (jedes Mal)",
            "Manchmal während Alltagsaktivitäten", "Regelmäßig während Alltagsaktivitäten",
        ),
        "Bei schnellen Richtungswechseln fühlt sich mein Sprunggelenk INSTABIL an:" to listOf("Nie", "Manchmal beim Rennen", "Häufig beim Rennen", "Beim Gehen"),
        "Beim Treppe hinuntergehen fühlt sich mein Sprunggelenk INSTABIL an:" to listOf("Nie", "Wenn ich schnell gehe", "Gelegentlich", "Immer"),
        "Mein Sprunggelenk fühlt sich beim Stehen auf einem Bein INSTABIL an:" to listOf("Nie", "Wenn ich auf dem Fußballen stehe", "Wenn ich auf dem gesamten Fuß stehe"),
        // "auf der Stell hüpfe" in the original.
        "Mein Sprunggelenk fühlt sich INSTABIL an:" to listOf("Nie", "Wenn ich von einem Bein auf das andere hüpfe", "Wenn ich auf der Stelle hüpfe", "Wenn ich springe"),
        "Mein Sprunggelenk fühlt sich INSTABIL an:" to listOf(
            "Nie", "Beim Rennen auf unebenem Untergrund", "Beim Joggen auf unebenem Untergrund", "Beim Gehen auf unebenem Untergrund",
            "Beim Gehen auf ebenem Untergrund",
        ),
    )
    fun side(prefix: String): List<Item> = questions.mapIndexed { i, (text, labels) ->
        ChoiceItem("$prefix${i + 1}", "${i + 1}. $text", fourToZero(*labels.toTypedArray()))
    } + listOf(
        ChoiceItem(
            "${prefix}8", "8. Wenn ich beginne im Sprunggelenk umzuknicken, kann ich dies normalerweise stoppen:",
            scored("Sofort" to 3, "Häufig" to 2, "Manchmal" to 1, "Nie" to 0, "Ich bin noch nie umgeknickt" to 3),
        ),
        ChoiceItem(
            "${prefix}9", "9. Nach einem typischen Umknickereignis, kehrt mein Sprunggelenk in den Normalzustand zurück:",
            scored("Fast umgehend" to 3, "In weniger als einem Tag" to 2, "In 1-2 Tagen" to 1, "In mehr als 2 Tagen" to 0, "Ich bin noch nie im Sprunggelenk umgeknickt" to 3),
        ),
    )
    val left = side("l")
    val right = side("r")
    val bands = listOf(Band.upTo(24, "eignet sich zur Identifikation einer chronischen Sprunggelenksinstabilität (Wright et al. 2014)"), Band.upTo(30, null))
    Assessment(
        id = "cait",
        name = "Cumberland Ankle Instability Tool",
        abbreviation = "CAIT",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "Cumberland Ankle Instability Tool, Stand 11/2026",
        hint = "Validierte deutsche Version, Gehring et al. 2016.\n\nSehr geehrter Patient, der folgende Fragebogen dient der " +
            "Erfassung von Beschwerden und Problemen, die durch Ihr Sprunggelenk verursacht werden. Die dadurch gewonnenen " +
            "Informationen werden uns helfen einzuschätzen, wie es Ihnen mit Ihrem Sprunggelenk geht und wie gut Sie in der " +
            "Lage sind, Ihre üblichen Aktivitäten zu verrichten. Bitte kreuzen Sie die Aussage an, die am BESTEN Ihre " +
            "Sprunggelenke beschreibt. Bitte bewerten Sie Ihre Sprunggelenke getrennt voneinander und geben Sie je eine Antwort " +
            "für LINKS und eine für RECHTS an. Beantworten Sie bitte jede Frage durch Ankreuzen des zugehörigen Kästchens. Bitte " +
            "beantworten Sie alle Fragen gemäß Ihrem aktuellen Zustand. Sollten Sie momentan keine Beschwerden haben, dann " +
            "bewerten Sie die Fragen entsprechend Ihrem Zustand in der vergangenen Woche.\n\n" +
            "Ein Wert von <25 eignet sich zur Identifikation einer chronischen Sprunggelenksinstabilität (Wright et al. 2014). " +
            "Die minimal detectable change und die minimal clinical important difference liegt bei >=3 Punkten (Wright et al. " +
            "2017). Der Score weist moderate bis große Korrelationen zu Leistungstests auf (Kunugi et al. 2018, Sierra-Guzman " +
            "et al. 2018): Dynamische Balance (Star Excursion Balance Test), Time to Stabilization nach einer Landung, " +
            "Reaktionszeit der Peroneen.",
        sections = listOf(Section("Links", items = left), Section("Rechts", items = right)),
        scores = listOf(
            higherBetter("links", "Gesamtscore links", left, bands = bands),
            higherBetter("rechts", "Gesamtscore rechts", right, bands = bands),
        ),
    )
}

/** Foot and Ankle Ability Measure, German version (Nauck et al. 2011), as in "…_FAAM-G_11-2026.pdf". */
private val FAAM_G: Assessment = run {
    val options = scored(
        "Keine Schwierigkeit" to 4, "Leichte Schwierigkeit" to 3, "Mäßige Schwierigkeit" to 2, "Extreme Schwierigkeit" to 1, "Nicht ausführbar" to 0,
    ) + notApplicable("Nicht zutreffend")
    val adl = listOf(
        "Stehen", "Gehen auf ebenem Untergrund", "Gehen auf ebenem Untergrund ohne Schuhe", "Bergauf Gehen", "Bergab Gehen",
        "Treppe heraufsteigen", "Treppe heruntersteigen", "Gehen auf unebenem Untergrund", "An der Bordsteinkante Auf- und absteigen",
        "In die Hocke gehen", "In den Zehenstand gehen", "Gehbeginn", "5 Min. oder weniger Gehen", "Ca. 10 Min. Gehen",
        "15 Min. oder länger Gehen", "Tätigkeiten im Haushalt", "Alltagsaktivitäten", "Körperpflege",
        "Leichte bis mittelschwere Arbeit (Stehen und Gehen)", "Schwere Arbeit (schieben, ziehen, aufsteigen tragen)", "Freizeitaktivitäten",
    ).mapIndexed { i, text -> ChoiceItem("adl${i + 1}", text, options) }
    val sport = listOf(
        "Rennen", "Absprung", "Landung", "Schnelle Starts und Stopps", "Richtungsänderungen, Seitwärtsbewegungen",
        "Aktivitäten mit niedriger Stoßbelastung", "Fähigkeit, Aktivitäten mit Ihrer normalen Technik durchzuführen",
        "Zeitlich unbegrenzte Fähigkeit zur Teilnahme in einer von Ihnen gewählten Sportart",
    ).mapIndexed { i, text -> ChoiceItem("sport${i + 1}", text, options) }
    fun rating(id: String, activities: String) = NumberItem(
        id,
        "Wie würden Sie Ihren derzeitigen Funktionszustand während Ihrer $activities Aktivitäten auf einer Skala von 0-100 " +
            "einschätzen? 100 entspricht dabei dem Funktionszustand, bevor Sie Fuß- und Sprunggelenkprobleme hatten. 0 bedeutet " +
            "die Unfähigkeit, irgendwelche Ihrer $activities Aktivitäten durchzuführen",
        unit = "%",
    )
    val overall = ChoiceItem(
        "funktionszustand", "Wie würden Sie den derzeitigen Funktionszustand Ihres Fußes insgesamt beurteilen?",
        unscored("Normal", "Beinahe normal", "Abnormal", "Schwer abnormal"),
    )
    // In percent of what is answered with points, "nicht zutreffend" left out, with any number answered, as the HOS (08.11.2026).
    fun percent(id: String, name: String, items: List<Item>) = higherBetter(id, name, items, rule = ScoreRule.Percent, unit = "%", minAnswered = 1)
    Assessment(
        id = "faam-g",
        name = "Foot and Ankle Ability Measure",
        abbreviation = "FAAM-G",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "FAAM-G, Stand 11/2026",
        hint = "Validierte deutsche Version (Nauck et al. 2011).\n\nSehr geehrter Patient, der folgende Fragebogen dient der " +
            "Erfassung von Beschwerden und Problemen, die durch Ihr Sprunggelenk verursacht werden. Die dadurch gewonnenen " +
            "Informationen werden uns helfen einzuschätzen, wie es Ihnen mit Ihrem Sprunggelenk geht und wie gut Sie in der " +
            "Lage sind, Ihre alltäglichen oder sportlichen Aktivitäten zu verrichten. Bitte beantworten Sie jede Frage mit einer " +
            "Antwort, die Ihren Zustand während der vergangenen Woche am besten beschreibt. Wenn die angegebene Aktivität durch " +
            "etwas anderes als durch ihr Sprunggelenk oder Fuß limitiert ist, kreuzen Sie „nicht zutreffend“ an.\n\n" +
            "Die Fragen werden von links nach rechts mit einem absteigenden Zahlenwert versehen: 4,3,2,1,0. Die letzte Kategorie " +
            "(„nicht zutreffend“) wird nicht bewertet. Das Ergebnis jeder Frage wird aufsummiert. Dies ergibt einen maximalen " +
            "Punktwert von 84 (Teilbereich alltägliche Aktivitäten) und 32 (Teilbereich sportliche Aktivitäten). Zur Berechnung " +
            "des Scores wird das ermittelte Ergebnis durch das maximal mögliche Ergebnis dividiert und dann mit 100 " +
            "multipliziert. Hohe Werte entsprechen einer kleinen Funktionseinschränkung bzw. einer großen Funktionskapazität. Für eine " +
            "klinisch relevante Verbesserung (MCID) sollte sich der Score um 8-9 Punkte verbessern (sowohl ADL als Sport, " +
            "Kivlian et al. 2011, Martin et al. 2005).",
        sections = listOf(
            Section("A: Teilbereich Tätigkeiten des alltäglichen Lebens", items = adl + rating("funktion-alltag", "alltäglichen")),
            Section("B: Teilbereich Sport", items = sport + rating("funktion-sport", "sportlichen")),
            Section(items = listOf(overall)),
        ),
        scores = listOf(
            percent("adl", "Tätigkeiten des alltäglichen Lebens", adl),
            percent("sport", "Sport", sport),
        ),
    )
}

/** Foot and Ankle Disability Score (the Index), as in "Foot-and-Ankle-Disability-Score_FADI_11-2026.pdf". */
private val FADI: Assessment = run {
    val activity = scored(
        "keine Beschwerden" to 4, "leichte Beschwerden" to 3, "mäßige Beschwerden" to 2, "starke Beschwerden" to 1, "nicht durchführbar" to 0,
    ) + notApplicable("nicht anwendbar")
    val pain = scored("kein Schmerz" to 4, "leichte Schmerzen" to 3, "mäßige Schmerzen" to 2, "starke Schmerzen" to 1, "Fuß ist nicht belastbar" to 0)
    val daily = listOf(
        "Stehen", "Gehen auf ebenem Untergrund", "Gehen auf ebenem Untergrund ohne Schuhe", "Bergauf gehen", "Bergab gehen",
        "Treppe aufwärts gehen", "Treppe abwärts gehen", "Gehen auf unebenem Untergrund",
        "„Kurven hoch und runter steigen“ (Stepping up and down curves)", "Kniebeugen", "Schlafen", "An die Zehen fassen",
        "Direkt losgehen", "5 Minuten und weniger gehen", "Annähernd 10 Minuten gehen", "15 Minuten und mehr gehen", "Hausarbeiten",
        "Aktivitäten des täglichen Lebens", "Körperpflege", "Leichte bis mäßige Arbeit (stehende, gehende Tätigkeit)",
        "Anstrengende Arbeit (ziehen/drücken, tragen, steigen)", "Freizeitaktivitäten",
    ).mapIndexed { i, text -> ChoiceItem("alltag${i + 1}", "${i + 1}. $text", activity) } +
        listOf("Allgemeines Schmerzniveau", "Ruheschmerz", "Schmerz bei normaler Aktivität", "Schmerz am Morgen")
            .mapIndexed { i, text -> ChoiceItem("alltag${i + 23}", "${i + 23}. $text", pain) }
    val sport = listOf(
        "Rennen/Sprinten", "Springen", "Landen", "Schnelles Kniebeugen", "Grätschen, seitliche Bewegungen", "Aktivitäten mit geringer Belastung",
        "Fähigkeit, seine Aktivitäten mit einer normalen Technik durchzuführen", "Fähigkeit, seinen Sport so lange wie gewünscht durchzuführen",
    ).mapIndexed { i, text -> ChoiceItem("sport${i + 1}", "${i + 1}. $text", activity) }
    Assessment(
        id = "fadi",
        name = "Foot and Ankle Disability Score",
        abbreviation = "FADI",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "FADI, Stand 11/2026",
        hint = "Bitte beantworten Sie jede Frage mit der Antwort, die am besten Ihren Zustand in den letzten Wochen beschreibt. " +
            "Wenn die gefragte Aktivität durch etwas anderes als Ihren Fuß/Ihr Sprunggelenk beeinträchtigt wird, notieren Sie " +
            "bitte „nicht anwendbar“. Die Angaben beziehen sich auf beide Füße. Es wird nicht zwischen den Seiten " +
            "unterschieden.\n\n" +
            "Der FADI (Foot and Ankle Disability Index, Martin 1999) eignet sich, um den Verlauf einer Therapie bei chronischen " +
            "Sprunggelenksinstabilitäten zu beurteilen. Der FADI und der FADI Sport wurden 1999 von Martin erstmalig " +
            "beschrieben. Er wurde für Patienten und Sportler mit chronischer Sprunggelenkinstabilität entwickelt. Seine " +
            "Validität und Ansprechbarkeit auf Veränderungen wurden untersucht (Hale 2005, Eechaute 2007).",
        sections = listOf(
            Section("FADI-Elemente im Alltag", "Die Maximalpunktzahl beträgt 104 Punkte.", daily),
            Section("FADI-Elemente im Sport", "Die Maximalpunktzahl beträgt 32 Punkte.", sport),
        ),
        // As points, all needed: "nicht anwendbar" leaves the sum without a result (08.11.2026).
        scores = listOf(higherBetter("alltag", "Gesamtpunktzahl Alltag", daily), higherBetter("sport", "Gesamtpunktzahl Sport", sport)),
    )
}

/** Foot and Ankle Outcome Score, German version (van Bergen 2014), as in "Foot-and-Ankle-Outcome-Score_FAOS_11-2026.pdf". */
private val FAOS: Assessment = run {
    val often = fourToZero("niemals", "selten", "manchmal", "oft", "immer")
    val can = fourToZero("immer", "oft", "manchmal", "selten", "niemals")
    val strength = fourToZero("keine", "schwach", "mäßig", "stark", "sehr stark")
    val difficulty = fourToZero("keine", "wenig", "einige", "große", "sehr große")
    val howMuch = fourToZero("gar nicht", "wenig", "etwas", "stark", "sehr stark")
    val symptoms = listOf(
        ChoiceItem("s1", "S1. Hatten Sie Schwellungen an Ihrem Fuß/Sprunggelenk?", often),
        ChoiceItem("s2", "S2. Fühlten Sie ein Mahlen, hören Sie ein Klicken oder irgendein Geräusch, wenn Sie Ihren Fuß/Ihr Sprunggelenk bewegten?", often),
        ChoiceItem("s3", "S3. Blieb Ihr Fuß/Sprunggelenk hängen oder blockierte er/es bei Bewegung?", often),
        ChoiceItem("s4", "S4. Konnten Sie Ihren Fuß/Ihr Sprunggelenk ganz ausstrecken?", can),
        ChoiceItem("s5", "S5. Konnten Sie Ihren Fuß/Ihr Sprunggelenk ganz beugen?", can),
    )
    val stiffness = listOf(
        ChoiceItem("s6", "S6. Wie stark war Ihre Fuß-/Sprunggelenkssteifigkeit morgens direkt nach dem Aufstehen?", strength),
        ChoiceItem("s7", "S7. Wie stark war Ihre Fuß-/Sprunggelenkssteifigkeit nach dem Sie saßen, lagen, oder sich im Verlauf des Tages ausruhten?", strength),
    )
    val painHowOften = ChoiceItem("p1", "P1. Wie oft haben Sie Schmerzen im Fuß-/Sprunggelenk?", fourToZero("nie", "monatlich", "wöchentlich", "täglich", "immer"))
    val painWhen = listOf(
        "Sich im Fuß/Sprunggelenk drehten?", "Ihren Fuß/Ihr Sprunggelenk ganz ausstreckten?", "Ihren Fuß/Ihr Sprunggelenk ganz beugten?",
        "Auf ebenem Boden gingen?", "Treppen herauf und herunterstiegen", "Nachts im Bett lagen?", "Saßen oder Lagen?", "Aufrecht standen?",
    ).mapIndexed { i, text -> ChoiceItem("p${i + 2}", "P${i + 2}. $text", strength) }
    val adl = listOf(
        "Treppen hinunterstiegen?", "Treppen hinaufstiegen?", "Vom Sitzen aufstanden?", "Aufrecht Standen?",
        "Sich bückten um zum Beispiel etwas vom Boden aufzuheben?", "Auf ebenem Boden gingen?", "Ins Auto ein- oder ausstiegen?",
        "Einkaufen gingen?", "Socken/Strümpfe anzogen?", "Vom Bett aufstanden?", "Socken/Strümpfe auszogen?",
        "Im Bett lagen und sich drehten ohne den Fuß/das Sprunggelenk dabei zu beugen?", "In die oder aus der Badewanne stiegen?",
        "Saßen?", "Sich auf die Toilette setzten oder aufstanden?",
        "Schwere Hausarbeit verrichteten (schwere Kisten umstellen, Boden schrubben…)?", "Leichte Hausarbeit verrichteten (Kochen, Staub wischen…)?",
    ).mapIndexed { i, text -> ChoiceItem("a${i + 1}", "A${i + 1}. $text", difficulty) }
    // The first numbered "PS1" in the original.
    val sport = listOf("In die Hocke gingen?", "Rannten?", "Hüpften?", "Sich auf Ihrem krankem Fuß umdrehten?", "Sich hinknieten?")
        .mapIndexed { i, text -> ChoiceItem("sp${i + 1}", "SP${i + 1}. $text", difficulty) }
    val quality = listOf(
        ChoiceItem("q1", "Q1. Wie oft sind Sie sich Ihres Fuß-/Sprunggelenksproblems bewusst?", fourToZero("nie", "monatlich", "wöchentlich", "täglich", "immer")),
        ChoiceItem("q2", "Q2. Haben Sie Ihre Lebensweise verändert, um eventuell Ihrem Fuß/Sprunggelenk schadende Tätigkeiten zu vermeiden?", howMuch),
        ChoiceItem("q3", "Q3. Wie sehr leiden Sie unter einem Mangel an Vertrauen und Zuversicht hinsichtlich Ihres Fußes/Sprunggelenkes?", howMuch),
        ChoiceItem("q4", "Q4. Wie würden Sie insgesamt die Schwierigkeiten bewerten, die Sie durch Ihren Fuß/Ihr Sprunggelenk haben?", difficulty),
    )
    Assessment(
        id = "faos",
        name = "Foot and Ankle Outcome Score",
        abbreviation = "FAOS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "FAOS, Stand 11/2026",
        hint = "Validierte deutsche Version, C. J. A. van Bergen; 2014.\n\nDieser Fragebogen stellt Ihnen Fragen zum Zustand Ihres " +
            "Fuß und Sprunggelenks. Die dadurch gewonnenen Informationen erlauben uns Ihre Beurteilung von Ihrem Fuß und " +
            "Sprunggelenk zu verfolgen und dokumentiert wie gut Sie in der Lage sind, Ihre üblichen Aktivitäten zu verrichten. " +
            "Beantworten Sie bitte jede Frage durch Ankreuzen des zugehörigen Kästchens. Bitte nur ein Kästchen pro Frage " +
            "Ankreuzen. Wenn Sie sich unsicher sind, wie Sie die Frage beantworten sollen, wählen Sie die Antwort aus, die Ihnen " +
            "am zutreffendsten erscheint.\n\n" +
            "Der FAOS ist ein Fragebogen, der bei annähernd allen Fuß-/Sprunggelenkspathologien unabhängig vom Leistungsniveau " +
            "verwendet werden kann. Der Fragebogen kann vom Patient selbst ausgeführt werden. Die benötigte Zeit beträgt ca. 10 " +
            "Minuten.\n\n" +
            "Sollte der Patient manche Aktivitäten nicht ausführen, weil die Probleme ihn daran hindern, sollte mit der " +
            "schlechtesten Punktzahl gewertet werden (0 Punkte). Ist der Patient nicht sportlich aktiv, so sollte die " +
            "entsprechende Subskala ausgelassen werden. Bei eher aktiven Patienten sollte die Subskala alltägliche Aktivitäten " +
            "ausgelassen werden. Sie hat mit 17 Items einen zu großen Einfluss auf das Ergebnis einer Patientengruppe, die bei " +
            "diesen Aktivitäten keine oder nur geringe Probleme hat. Es ist also nicht erwünscht, dass grundsätzlich immer alle " +
            "Subskalen benützt werden. Vielmehr sollte die Subskala ausgewählt werden, welche die höchste Wertigkeit für den " +
            "Patienten besitzt.",
        sections = listOf(
            Section("Subskala Symptome", "Diese Fragen beziehen sich auf Ihre Fuß-/Sprunggelenksbeschwerden während der vergangenen Woche.", symptoms),
            Section(
                "Subskala Steifigkeit",
                "Die nachfolgenden Fragen betreffen die Steifigkeit in Ihrem Fuß/Sprunggelenk während der letzten Woche. Unter " +
                    "Steifigkeit versteht man ein Gefühl der Einschränkung oder Verlangsamung der Fähigkeit Ihre Gelenke zu bewegen.",
                stiffness,
            ),
            Section("Subskala Schmerz", items = listOf(painHowOften)),
            Section(hint = "Wie ausgeprägt waren Ihre Schmerzen in der vergangenen Woche als Sie zum Beispiel:", items = painWhen),
            Section(
                "Subskala Aktivitäten des täglichen Lebens",
                "Die nachfolgenden Fragen beziehen sich auf Ihre körperliche Leistungsfähigkeit. Hierunter verstehen wir Ihre " +
                    "Fähigkeit, sich selbständig zu bewegen bzw. sich selbst zu versorgen. Für jede der nachfolgenden " +
                    "Aktivitäten geben Sie bitte das Ausmaß der Schwierigkeiten an, welche Sie aufgrund Ihres Fuß/Sprunggelenks " +
                    "innerhalb der letzten Wochen erfahren haben. Welche Schwierigkeiten hatten Sie in der letzten Woche als Sie " +
                    "zum Beispiel:",
                adl,
            ),
            Section(
                "Subskala Aktivitäten bei Sport und Freizeit",
                "Die folgenden Fragen beziehen sich auf Ihre körperliche Belastbarkeit im Rahmen von Sport- und " +
                    "Freizeitaktivitäten. Für jede der nachfolgenden Aktivitäten geben Sie bitte das Ausmaß der Schwierigkeiten " +
                    "an, welches Sie aufgrund Ihres Fußes/Sprunggelenkes innerhalb der letzten Woche erfahren haben.",
                sport,
            ),
            Section("Subskala Lebensqualität", items = quality),
        ),
        scores = listOf(
            // The stiffness counts to the symptoms, as the original sums it under "Gesamtscore Subskala Symptome".
            halfAnsweredSubscale("symptome", "Symptome", symptoms + stiffness),
            halfAnsweredSubscale("schmerz", "Schmerz", listOf(painHowOften) + painWhen),
            halfAnsweredSubscale("adl", "Aktivitäten des alltäglichen Lebens", adl),
            halfAnsweredSubscale("sport", "Aktivitäten bei Sport und Freizeit", sport),
            halfAnsweredSubscale("lebensqualitaet", "Lebensqualität", quality),
        ),
    )
}

/** Lower Extremity Functional Scale, German version, as in "Lower-Extremity-Functional-Scale_LEFS_11-2026.pdf". */
private val LEFS: Assessment = run {
    val options = scored(
        "Extreme Schwierigkeiten/Unmöglich" to 0, "Ziemlich große Schwierigkeiten" to 1, "Mäßige Schwierigkeiten" to 2,
        "Geringe Schwierigkeiten" to 3, "Keine Schwierigkeiten" to 4,
    )
    val items = listOf(
        "Ihre übliche Arbeit, Hausarbeit oder Schulaktivitäten verrichten", "Ihre üblichen Hobbys, Freizeit- oder Sportaktivitäten ausüben",
        "In eine oder aus einer Badewanne steigen", "Zwischen Räumen hin- und hergehen", "Schuhe oder Socken anziehen",
        "Tief in die Hocke gehen", "Einen Gegenstand wie eine Tasche mit Lebensmitteln vom Boden heben", "Leichte Aktivitäten zu Hause absolvieren",
        "Schwere Aktivitäten zu Hause absolvieren", "In ein oder aus einem Auto steigen", "Einen halben bis einen Kilometer gehen",
        "Über einen Kilometer gehen", "Zehn Stufen (etwa einen Treppenabsatz) hinauf- oder hinuntergehen", "Eine Stunde stehen",
        "Eine Stunde sitzen", "Auf ebenem Boden rennen", "Auf unebenem Boden rennen",
        "Haken schlagen/kurze Richtungswechsel beim schnellen Rennen", "Springen/Hüpfen", "Im Bett umdrehen",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", text, options) }
    Assessment(
        id = "lefs",
        name = "Lower Extremity Functional Scale",
        abbreviation = "LEFS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "LEFS, Stand 11/2026",
        hint = "Validierte deutsche Version.\n\nHaben oder hätten Sie heute mit einer der folgenden Aktivität Schwierigkeiten? Bitte " +
            "kreuzen Sie an!\n\n" +
            "Mit der Lower Extremity Functional Scale (LEFS) haben Physiotherapeuten einen Fragebogen zur Hand, der " +
            "Aktivitätseinschränkungen nach Verletzungen und Erkrankungen der unteren Extremität erfasst und den Therapieverlauf " +
            "objektiv abbildet. Die Antwortmöglichkeiten sind ohne Punktzahlen genannt, um den Patienten nicht zu beeinflussen. " +
            "Um das allgemeine Aktivitätsniveau des Patienten einzuschätzen, zählt der Therapeut von allen angekreuzten Items " +
            "die Punkte zusammen. Hat der Patient alle Items beantwortet, kann er maximal 80 Punkte erreichen. Er darf " +
            "höchstens vier Fragen unbeantwortet lassen, sonst lässt sich das Ergebnis nicht valide interpretieren. Bei " +
            "Menschen zwischen 68 und 80 Jahren spiegelt ein Ergebnis von 52 Punkten einen guten Aktivitätsstatus wider. Eine " +
            "grundlegende Hilfsbedürftigkeit zeigt sich in einem Ergebnis von unter 20 Punkten.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            higherBetter(
                "lefs", "LEFS-Wert", items,
                bands = listOf(Band.upTo(19, "Grundlegende Hilfsbedürftigkeit"), Band.upTo(80, null)),
                minAnswered = items.size - 4,
            ),
        ),
    )
}

/** Pelvic Girdle Questionnaire, own not validated translation after Stuge 2011, as in "Pelvic-Girdle-Questionnaire_11-2026.pdf". */
private val PGQ: Assessment = run {
    val problem = scored("überhaupt nicht (0)" to 0, "gering (1)" to 1, "etwas (2)" to 2, "sehr (3)" to 3)
    val activities = listOf(
        "…sich anzuziehen?", "…weniger als 10 Minuten zu stehen?", "…mehr als 60 Minuten zu stehen?", "…vornüberzubeugen?",
        "…weniger als 10 Minuten zu sitzen?", "…mehr als 60 Minuten zu sitzen?", "…weniger als 10 Minuten zu gehen?",
        "…mehr als 10 Minuten zu gehen?", "…Stufen zu gehen", "…Hausarbeit zu machen?", "…leichte Gegenstände zu tragen?",
        "…schwere Gegenstände zu tragen?", "…aufzustehen und/oder hinzusetzen?", "…einen Einkaufswagen zu schieben?", "…zu rennen?",
        "…sportlich aktiv zu sein (Bitte nur antworten wenn Sie Sport treiben)?", "…hinzuliegen?", "…im Bett zu drehen?",
        "…sexuell aktiv zu sein (Bitte nur antworten wenn Sie sexuell aktiv sind)?", "…mit dem Fuß etwas wegzustossen?",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", problem) }
    val pain = listOf("…am Morgen?", "…am Abend?")
        .mapIndexed { i, text -> ChoiceItem("${i + 21}", "${i + 21}. $text", scored("kein (0)" to 0, "gering (1)" to 1, "moderat (2)" to 2, "stark (3)" to 3)) }
    val extent = listOf("…Ihr Bein eingesackt/weggeknickt?", "…Ihre Aktivität verlangsamt?", "…Ihr Schlaf unterbrochen?")
        .mapIndexed { i, text -> ChoiceItem("${i + 23}", "${i + 23}. $text", scored("Überhaupt nicht (0)" to 0, "gering (1)" to 1, "etwas (2)" to 2, "sehr (3)" to 3)) }
    val all = activities + pain + extent
    Assessment(
        id = "pgq",
        name = "Pelvic Girdle Questionnaire",
        abbreviation = "PGQ",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "Pelvic Girdle Questionnaire, Stand 11/2026",
        hint = "Eigene, nicht validierte Übersetzung - nach Stuge 2011.\n\nSehr geehrte/r Patient/in, der folgende Fragebogen dient " +
            "der Erfassung von Beschwerden und Problemen, die durch Ihr Becken verursacht werden. Bitte kennzeichnen Sie für " +
            "jede Aktivität das Kästchen, das ihre Beschwerden am heutigen Tag am besten widerspiegelt.\n\n" +
            "Die Werte aller Fragen werden zu einem Gesamtscore addiert und durch die maximal mögliche Punktzahl (75) geteilt. " +
            "Der dadurch entstandene Wert wird mit 100% multipliziert und ergibt die Funktionseinschränkung durch " +
            "Beckenschmerzen: Disability = (Gesamtscore/75) x 100%. Ein höherer Wert spiegelt eine höhere Einschränkung wider. " +
            "Der Fragebogen wird im Kontext Beckenschmerzen („pelvic girdle pain“) häufig verwendet und sollte daher in dieser " +
            "Patientengruppe zum Einsatz kommen.",
        sections = listOf(
            Section(hint = "Wie problematisch ist es für Sie aufgrund Ihres Beckenschmerzes:", items = activities),
            Section(hint = "Wieviel Schmerz empfinden Sie:", items = pain),
            Section(hint = "In welchem Ausmaß ist aufgrund Ihres Beckenschmerzes:", items = extent),
        ),
        scores = listOf(
            // Always of 75, a question left out counting 0, with any number left out (08.11.2026).
            ItemScore("disability", "Disability", ids(all), rule = ScoreRule.PercentOfAll, unit = "%", direction = Direction.LowerIsBetter, minAnswered = 1),
        ),
    )
}

/** Running Readiness Scale, own translation, as in "Running-Readiness-Scale_11-2026.pdf": five tests, passed or not. */
private val RRS: Assessment = run {
    fun test(n: Int, title: String, instruction: String, criteria: List<String>): Section = Section(
        "$n. $title",
        "Anweisung: $instruction\n\nKriterien für gute Ausführung:\n" + criteria.joinToString("\n") { "• $it" },
        listOf(ChoiceItem("$n", "Ergebnis", scored("Bestanden" to 1, "Nicht bestanden" to 0))),
    )
    val tests = listOf(
        test(
            1, "Hüpfen (beidbeinig)",
            "Springen Sie im Rhythmus des Metronoms auf der Stelle. Stellen Sie sich vor die Wand, so dass keine " +
                "Ausweichbewegungen entstehen. Achten Sie bitte darauf, die Fußspitzen bei jedem Sprung vom Boden abzuheben.",
            listOf("Tempo: 160 Sprünge/Minute", "Absprung über die Fußgelenke", "Knie stabil, kein Einknicken zur Körpermitte (Knievalgus)"),
        ),
        test(
            2, "Plank (Unterarmstütz)",
            "Stützen Sie sich auf die Unterarme und Zehen, sodass eine gerade Linie ihres Körpers entsteht.",
            listOf("Körper in einer Linie", "Gleichmäßige Belastung auf beide Füße und Unterarme", "Kopf in neutraler Position"),
        ),
        test(
            3, "Stufen steigen (Step-ups)",
            "Steigen Sie im Takt des Metronoms auf eine Stufe und wieder herunter. Nach 30 Sekunden wird die Seite gewechselt.",
            listOf("Tempo: 160 Schritte/Minute", "Aufrechter Oberkörper, kein starkes Vor- oder Seitneigen", "Knie stabil, kein Einknicken (Knievalgus)"),
        ),
        test(
            4, "Einbeinige Kniebeuge",
            "Stehen Sie auf ein Bein und halten Sie das andere Bein nach vorne über dem Boden. Führen Sie im Takt des Metronoms " +
                "Kniebeugen auf 60° Kniebeugung durch. Nach 30 Sekunden wird die Seite gewechselt.",
            listOf(
                "Tempo: 80 Takte/Minute (Beuge auf 1. Takt, Streckung auf 2. Takt)", "Gleichgewicht halten", "Becken bleibt waagrecht",
                "Knie stabil, kein Einknicken (Knievalgus)",
            ),
        ),
        test(
            5, "Wand-Sitz (Wall Sit)",
            "Stellen Sie sich mit dem Gymnastikball im Rücken an die Wand. Gehen Sie in die Hocke, bis die Oberschenkel parallel " +
                "zum Boden ausgerichtet sind. Halten Sie die Position für eine Minute.",
            listOf("Oberschenkel parallel zum Boden", "Aufrechter Oberkörper", "Gleichmäßige Belastung beider Füße", "Knie stabil, kein Einknicken (Knievalgus)"),
        ),
    )
    val ready = YesNoItem("bereit", "Bereit für Laufaktivitäten")
    Assessment(
        id = "rrs",
        name = "Running Readiness Scale",
        abbreviation = "RRS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "Running Readiness Scale, Stand 11/2026",
        hint = "Eigene Übersetzung.\n\nDie Running Readiness Scale bewertet die Fähigkeit, bei laufähnlichen Aktivitäten, bestimmte " +
            "Kriterien für eine korrekte Ausführung einzuhalten. Sie hilft, Schwächen zu erkennen, um Verletzungen vorzubeugen " +
            "und die Leistung zu verbessern.\n\n" +
            "Bestanden: Der Patient kann alle Übungen 1 Minute lang ohne Unterbrechung unter Einhaltung der Kriterien " +
            "durchführen. Nicht bestanden: Der Patient kann die Kriterien nicht erfüllen oder unterbricht innerhalb der Minute.\n\n" +
            "Nach Harrison et al. (2023) korreliert die Übungsausführung im Test mit der Kinematik beim Laufen (weniger " +
            "Knievalgus in den Tests korreliert mit einem geringeren Knievalgus beim Laufen). Nach Luedke et al. (2025) ist eine " +
            "geringere Leistungsfähigkeit in der RRS mit einer höheren Inzidenz von laufbedingten Verletzungen assoziiert. Bei " +
            "einem Score ≤ 3 war die Verletzungswahrscheinlichkeit signifikant erhöht (OR 4,8 (2,1-11,3)). Bei der Analyse " +
            "individueller Tests waren folgende Tests mit einer erhöhten Inzidenz verbunden: Double leg-hop: OR 4,5 (1,4-14,7), " +
            "Wall-sit: OR 25,9 (1,4-482).",
        sections = tests + Section("Gesamtergebnis", items = listOf(ready)),
        scores = listOf(
            higherBetter(
                "gesamt", "Gesamtscore", tests.flatMap(Section::items),
                bands = listOf(
                    Band.upTo(3, "Geringe bzw. unzureichende Laufbereitschaft"),
                    Band.upTo(5, "Hohe Bereitschaft für das Joggen"),
                ),
            ),
        ),
    )
}

/** SEFAS, German version, as in "Self-Reported-Foot-and-Ankle-Score_SEFAS_11-2026.pdf". */
private val SEFAS: Assessment = run {
    val items = listOf(
        "Wie würden Sie die Schmerzen beschreiben, die Sie gewöhnlich im betroffenen Fuß/Sprunggelenk empfinden?" to
            listOf("keine", "sehr geringe", "geringe", "mäßige", "starke"),
        "Wie lange konnten Sie gehen, bis starke Schmerzen im betroffenen Fuß/Sprunggelenk aufgetreten sind?" to
            listOf("mehr als 30 min ohne Schmerzen", "16-30 min", "5-10 min", "kurze Strecken in und um das Haus", "aufgrund starker Schmerzen nicht möglich"),
        "War es Ihnen möglich, auf unebenem Boden zu gehen?" to
            listOf("ja, ohne Schwierigkeiten", "mit geringen Schwierigkeiten", "mit mäßigen Schwierigkeiten", "mit starken Schwierigkeiten", "nein, nicht möglich"),
        "Mussten Sie eine orthopädische Einlage, eine Fersenerhöhung oder spezielle Schuhe verwenden?" to
            listOf("nie", "gelegentlich", "oft", "meistens", "immer"),
        "Wie stark haben Sie die Schmerzen im betroffenen Fuß/Sprunggelenk bei Ihrer üblichen Arbeit, Hausarbeit und Hobbies, beeinträchtigt?" to
            listOf("gar nicht", "ein wenig", "mäßig", "stark", "vollständig"),
        "Hatten Sie aufgrund Ihres betroffenen Fußes/Sprunggelenks einen hinkenden Gang?" to
            listOf("an keinem Tag", "an ein bis zwei Tagen", "an einigen Tagen", "an den meisten Tagen", "jeden Tag"),
        // "mir geringen" in the original.
        "Konnten Sie Treppen steigen?" to
            listOf("ja, ohne Schwierigkeiten", "mit geringen Schwierigkeiten", "mit mäßigen Schwierigkeiten", "mit starken Schwierigkeiten", "nicht möglich"),
        "Haben Sie die Schmerzen im betroffenen Fuß/Sprunggelenk nachts im Bett beeinträchtigt?" to
            listOf("an keiner Nacht", "an ein bis zwei Nächten", "an einigen Nächten", "an den meisten Nächten", "jede Nacht"),
        "Wie stark haben die Schmerzen im betroffenen Fuß/Sprunggelenk Ihre Freizeitaktivitäten beeinträchtigt?" to
            listOf("gar nicht", "ein wenig", "mäßig", "stark", "vollständig"),
        "War Ihr Fuß geschwollen?" to listOf("gar nicht", "gelegentlich", "oft", "meistens", "immer"),
        "Wie stark waren Ihre Schmerzen im betroffenen Fuß/Sprunggelenk, wenn Sie von einem Stuhl aufgestanden sind (z.B. nach dem Essen)?" to
            listOf("keine Schmerzen", "geringe Schmerzen", "mäßige Schmerzen", "starke Schmerzen", "unerträgliche Schmerzen"),
        "Hatten Sie im betroffenen Fuß/Sprunggelenk plötzlich einschießende Schmerzen, ein Stechen oder Krämpfe?" to
            listOf("an keinem Tag", "an ein bis zwei Tagen", "an einigen Tagen", "an den meisten Tagen", "jeden Tag"),
    ).mapIndexed { i, (text, labels) -> ChoiceItem("${i + 1}", "${i + 1}. $text", fourToZero(*labels.toTypedArray())) }
    Assessment(
        id = "sefas",
        name = "SEFAS - Fragebogen zu Fuß- und Sprunggelenk",
        abbreviation = "SEFAS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "SEFAS, Stand 11/2026",
        hint = "Deutsche Version.\n\nWir möchten Sie bitten, die folgenden 12 Fragen zu beantworten. Jede Frage hat 5 " +
            "Antwortmöglichkeiten, die von 0-4 entsprechend ihres Schweregrades eingeteilt sind. 4 = geringster Schweregrad, " +
            "0 = höchster Schweregrad. Bitte kreuzen Sie die Antwort an, die Ihren Zustand während der letzten 4 Wochen am " +
            "besten beschreibt.\n\n" +
            "Der SEFAS (Self-reported foot and ankle score) ist ein Patientenfragebogen, welcher akzeptable Werte für " +
            "Reliabilität, Validität und Änderungssensitivität zeigt (Cöster 2012, Dawson 2012). Er eignet sich um Schmerz, " +
            "Einschränkungen, Disability und Lebensqualität zu beurteilen. Ursprünglich wurde er in Schweden für Patienten mit " +
            "Sprunggelenksprothese entwickelt (Cöster 2012). In weiteren Untersuchungen wurde die Reliabilität und Validität für " +
            "andere Operationen des Sprunggelenkes, des Vor- und Rückfußes nachgewiesen (Cöster 2014). " +
            "Grundsätzlich liegt der Score höher für Männer und jüngeren Patienten im Vergleich zu Frauen und Älteren. Die " +
            "Maximalpunktzahl beträgt 48. Frauen haben einen Median von 47, Männer von 48. In der 10. Perzentile beträgt der " +
            "Wert bei Frauen 31, bei Männern 40 (20-29 Jahre). In der höchsten getesteten Alterskategorie (80-89) liegt dieser " +
            "Wert bei Frauen bei 22, bei Männern bei 29 (Cöster 2018). Der minimale messbare Unterschied liegt bei 5 Punkten " +
            "(Cöster 2017).",
        sections = listOf(Section(items = items)),
        scores = listOf(higherBetter("gesamt", "Gesamtpunktzahl", items)),
    )
}

/** Victorian Institute of Sport Assessment – Achilles, German version (Lohrer et al. 2011), as in "…_VISA-A-Score_11-2026.pdf". */
private val VISA_A: Assessment = run {
    fun pain(id: String, text: String) = ScaleItem(id, text, 0, 10, "Starker heftiger Schmerz", "Kein Schmerz")
    val stiffness = ChoiceItem(
        "1", "1. Für wie viele Minuten verspüren Sie nach dem ersten Aufstehen ein Steifigkeitsgefühl in der Achillessehnenregion?",
        (0..10).map { Option("$it", "${100 - it * 10} Minuten", it) },
    )
    val scales = listOf(
        pain("2", "2. Nachdem Sie für den Tag aufgewärmt sind, haben Sie Schmerzen, wenn Sie die Achillessehne über der Kante einer Treppe dehnen (Knie gestreckt halten)?"),
        pain(
            "3",
            "3. Nachdem Sie 30 Minuten auf unebenem Untergrund gegangen sind, haben Sie in den darauffolgenden 2 Stunden " +
                "Schmerzen? (Wenn Sie wegen Schmerzen nicht auf unebenem Untergrund 30 Minuten gehen können, kreuzen Sie bei " +
                "dieser Frage 0 an).",
        ),
        pain("4", "4. Haben Sie Schmerzen, wenn Sie mit normaler Geschwindigkeit die Treppe heruntergehen?"),
        pain("5", "5. Haben Sie Schmerzen, während oder unmittelbar nachdem Sie 10 (einbeinige) Zehenstände auf einer flachen Unterlage ausgeführt haben?"),
    )
    val hops = ChoiceItem("6", "6. Wie oft können Sie ohne Schmerzen auf einem Bein hüpfen?", (0..10).map { Option("$it", "${it}x", it) })
    val sport = ChoiceItem(
        "7", "7. Üben Sie derzeit Sport oder andere körperliche Aktivitäten aus?",
        scored(
            "Nein, gar nicht; wegen der Achillessehnensymptome" to 0,
            "Verändertes Trainings und/oder Wettkampfverhalten, verglichen mit dem Zustand vor Beginn der Symptome" to 4,
            "Volle Trainings und/oder Wettkampfbelastung, aber nicht auf dem gleichen Niveau wie vor Beginn der Symptome" to 7,
            "Voll belastbar oder Traininigs- und/oder Wettkampfbelastung auf dem gleichen oder auf höherem Niveau als vor Beginn der Symptome" to 10,
        ),
    )
    val which = ChoiceItem(
        "8", "8. Bitte beantworten Sie entweder A, B oder C dieser Frage.",
        listOf(
            Option("a", "Wenn Sie bei der Durchführung achillessehnenbelastender Sportarten keine Schmerzen empfinden, füllen Sie bitte nur Frage 8a aus."),
            Option("b", "Wenn Sie bei der Durchführung achillessehnenbelastender Sportarten Schmerzen empfinden, die Sie aber nicht zum Belastungsabbruch der Aktivität zwingen, füllen Sie bitte nur Frage 8b aus."),
            Option("c", "Wenn Sie bei der Durchführung achillessehnenbelastender Sportarten Schmerzen empfinden, die Sie zum Abbruch der Aktivität zwingen, füllen Sie bitte nur Frage 8c aus."),
        ),
    )
    val training = visaTraining(
        listOf(
            "8a. Wie lange können Sie trainieren/üben, wenn Sie bei der Durchführung achillessehnenbelastender Sportarten keine Schmerzen empfinden?",
            "8b. Wie lange können Sie trainieren/üben, wenn Sie bei der Durchführung achillessehnenbelastender Sportarten Schmerzen empfinden, die Sie aber nicht zum Belastungsabbruch der Aktivität zwingen?",
            "8c. Wie lange können Sie trainieren/üben, wenn Sie bei der Durchführung achillessehnenbelastender Sportarten Schmerzen empfinden, die Sie zum Abbruch der Aktivität zwingen?",
        ),
        listOf("0 Minuten", "1-10 Minuten", "11-20 Minuten", "21-30 Minuten", ">30 Minuten"),
        listOf(listOf(0, 7, 14, 21, 30), listOf(0, 4, 10, 14, 20), listOf(0, 2, 5, 7, 10)),
    )
    val counted = listOf(stiffness) + scales + hops + sport + training
    Assessment(
        id = "visa-a",
        name = "Victorian Institute of Sport Assessment – Achilles",
        abbreviation = "VISA-A",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "VISA-A Score, Stand 11/2026",
        hint = "Validierte deutsche Version (Lohrer et al. 2011).\n\nSehr geehrter Patient, der folgende Fragebogen dient der " +
            "Erfassung von Beschwerden und Problemen, die durch Ihre Achillessehne verursacht werden. Kreuzen Sie für jede Frage " +
            "dasjenige Kästchen an, das Ihr derzeitiges Befinden wiedergibt.\n\n" +
            "Die Werte jeder Frage werden addiert und in Relation zur Gesamtpunktzahl dargestellt (x/100). In der Regel wird der " +
            "Wert dann in % umgewandelt. Der Fragebogen zeigt eine moderate bis sehr gute Reliabilität (ICC: 0,6-0,97). Er " +
            "korreliert mit anderen Bewertungsskalen von Curwin und Stanish und Percy und Conochie (Lohrer et al. 2011). Der Wert " +
            "kann sich durch Training verändern und kann daher in der Verlaufskontrolle verwendet werden (Silbernagel et al. " +
            "2007). Die minimal klinisch relevante Veränderung beträgt mindestens 12 Punkte (Sterkenburg 2012).",
        sections = listOf(Section(items = listOf(stiffness) + scales + hops + sport + which + training)),
        // Questions 1 to 7 and the one of 8a, 8b and 8c shown.
        scores = listOf(higherBetter("gesamt", "Gesamtscore", counted, minAnswered = counted.size - 2)),
    )
}

/** VISA-G, not validated German version, as in "…_VISA-G-Score_11-2026.pdf". */
private val VISA_G: Assessment = run {
    val pain = ScaleItem("1", "Frage 1: Wie stark sind Ihre Hüftschmerzen üblicherweise?", 0, 10, "kein Schmerz", "stärkster Schmerz", reversed = true)
    fun question(n: Int, text: String, vararg options: Pair<String, Int>) = ChoiceItem("$n", "Frage $n: $text", scored(*options))
    val questions = listOf(
        question(
            2, "Wie lange können Sie auf Ihrer betroffenen Hüfte liegen?",
            "Länger als eine 1h." to 10, "30 min - 1h, dann muss ich mich bewegen." to 7, "15 – 30 min, dann muss ich mich bewegen." to 5,
            "5 –15 min, dann muss ich mich bewegen." to 2, "Ich kann überhaupt nicht auf der betroffenen Seite liegen." to 0,
        ),
        question(
            3, "Wie gehen Sie eine Treppe nach oben oder unten?",
            "Ich kann eine Treppe normal gehen, ohne Hüftschmerzen." to 10,
            "Ich kann eine Treppe normal gehen, habe aber etwas Hüftschmerzen dabei." to 7,
            "Ich kann eine Treppe normal gehen, halte mich aber aufgrund meiner Hüftschmerzen an einem Geländer fest." to 5,
            "Ich gehe eine Treppe, indem ich eine Stufe nach der anderen nehme und mich aufgrund meiner Hüftschmerzen an einem Geländer festhalte." to 2,
            "Ich kann aufgrund meiner Hüftschmerzen überhaupt keine Treppen gehen." to 0,
        ),
        question(
            4, "Wie gehen Sie eine Rampe oder einen Hang hinauf oder hinunter?",
            "Ich kann einen Hang/eine Rampe ohne Hüftschmerzen normal hinauf oder hinunter gehen." to 10,
            "Ich kann einen Hang/eine Rampe normal hinauf oder hinunter gehen, habe aber leichte Hüftschmerzen dabei." to 7,
            "Ich habe aufgrund meiner Hüftschmerzen einige Schwierigkeiten, einen Hang/eine Rampe hinauf oder hinunter zu gehen." to 5,
            "Ich habe aufgrund meiner Hüftschmerzen erhebliche Schwierigkeiten, einen Hang/eine Rampe hinauf oder hinunter zu gehen." to 2,
            "Ich kann aufgrund meiner Hüftschmerzen keinen Hang/keine Rampe hinauf- oder hinunter gehen." to 0,
        ),
        question(
            5, "Wie ist der Übergang zum Stehen und Gehen, nachdem Sie 30 Minuten gesessen sind?",
            "Kein Problem." to 10, "Die ersten Schritte sind schwierig." to 7, "Ich muss einen Moment stehen bleiben, bevor ich losgehen kann." to 5,
            "Ich muss weniger als 20 Sekunden stehen bleiben, bevor ich losgehen kann." to 2,
            "Ich muss mehr als 20 Sekunden stehen bleiben, bevor ich losgehen kann." to 0,
        ),
        question(
            6, "Wie ergeht es Ihnen bei der Haus- und/oder Gartenarbeit (oder einer ähnlichen Aktivität)?",
            "Ich kann in meinem Haus und/oder Garten für eine Stunde oder länger arbeiten." to 10,
            "Aufgrund meiner Hüftschmerzen kann ich in Zeitabschnitten von je 30-60 Minuten in meinem Haus und/oder Garten arbeiten." to 7,
            "Aufgrund meiner Hüftschmerzen arbeite ich nur sehr eingeschränkt in Haus und Garten." to 5,
            "Aufgrund meiner Hüftschmerzen arbeite ich nur eingeschränkt in meinem Haus, arbeite aber nicht im Garten." to 2,
            "Aufgrund meiner Hüftschmerzen arbeite ich weder im Haus noch im Garten." to 0,
        ),
        question(
            7, "Führen Sie derzeit regelmäßig körperliche oder sportliche Aktivitäten aus?",
            "Ja - ich kann wie früher trainieren oder Sport treiben." to 10, "Etwas weniger als früher." to 7, "Deutlich weniger als früher." to 4,
            "Nein - ich kann nicht trainieren oder Sport treiben, ich möchte nicht oder ich habe keine Zeit." to 0,
        ),
    )
    val which = ChoiceItem(
        "8",
        "Frage 8 hat 3 Abschnitte. Bitte beantworten Sie NUR Abschnitt A, B oder C. Beeinflusst Ihr aktueller Hüftschmerz Ihre " +
            "Fähigkeit, Aktivitäten, bei denen Sie auf den Füßen sind, auszuführen (z.B. Gehen, Einkaufen, Joggen, Kniebeugen, Ausfallschritte)?",
        listOf(
            Option("a", "Abschnitt A: Meine Hüftschmerzen sind so stark, dass sie mich vom Gehen, Einkaufen, Joggen oder anderen Aktivitäten, bei denen ich auf den Füßen bin, abhalten."),
            Option("b", "Abschnitt B: Meine Hüftschmerzen sind bei körperlichen Aktivitäten vorhanden, aber sie halten mich nicht vom Gehen, Einkaufen, Joggen oder anderen Aktivitäten, bei denen ich auf den Füßen bin, ab."),
            Option("c", "Abschnitt C: Wenn Sie keine Schmerzen beim Gehen, Einkaufen, Joggen oder anderen Aktivitäten, bei denen Sie auf den Füßen sind, haben, wieviel von diesen Aktivitäten machen Sie jeden Tag?"),
        ),
    )
    val ifSo = "Falls dies der Fall ist, wieviel von diesen Aktivitäten machen Sie jeden Tag?"
    val training = visaTraining(
        listOf("8a. $ifSo", "8b. $ifSo", "8c. Wieviel von diesen Aktivitäten machen Sie jeden Tag?"),
        listOf(
            "Ich mache keine zusätzlichen Aktivitäten, bei denen ich auf den Füßen bin - ich bewege mich nur im Haus.",
            "Ich mache weniger als 10 Minuten.", "Ich mache zwischen 10 und 19 Minuten.", "Ich mache zwischen 20 und 29 Minuten.",
            "Ich mache mehr als 30 Minuten.",
        ),
        listOf(listOf(0, 2, 5, 7, 10), listOf(0, 5, 10, 15, 20), listOf(6, 12, 18, 24, 30)),
    )
    val counted = listOf(pain) + questions + training
    Assessment(
        id = "visa-g",
        name = "Victorian Institute of Sport Assessment – VISA-G Score",
        abbreviation = "VISA-G",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "VISA-G Score, Stand 11/2026",
        hint = "Nicht validierte deutsche Version/eigene Übersetzung.\n\nBitte kreuzen Sie bei jeder Frage nur ein Kästchen an. Wählen " +
            "Sie das Kästchen, das am besten auf Sie zutrifft, auch wenn die Aussage nicht ganz perfekt passt. Alle Fragen " +
            "beziehen sich auf Ihre Hüftschmerzen.\n\n" +
            "Ein höherer Score zeigt weniger Schmerz und eine bessere Funktion an. Die Intra-Tester Reliabilität beträgt 0,827 " +
            "und ist somit als gut bis sehr gut zu betrachten. Der Retest sollte nach ca. 6 Wochen erfolgen (Fearon et al. 2015).",
        sections = listOf(Section(items = listOf(pain) + questions + which + training)),
        // Questions 1 to 7 and the one of 8a, 8b and 8c shown; the pain of question 1 counts the other way round, 0 as 10.
        scores = listOf(higherBetter("gesamt", "Total Score", counted, minAnswered = counted.size - 2)),
    )
}
