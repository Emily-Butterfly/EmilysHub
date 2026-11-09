package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Schlafverhalten" (#75), each as in its PDF of the date in its source. Each result
 * needs all of its items, as in #72; the PSQI says so itself, all but question 5j. Where a PDF falls short, as asked on
 * 09.11.2026: the PSQI scores its seven components after Buysse et al. 1989 and names its total after Riemann and Backhaus;
 * the ASBQ names its gap from 37 to 41 after Driller et al. 2018; the ASSQ reckons its modifiers too.
 */
internal val SLEEP: List<Assessment> by lazy { listOf(ASBQ, ASSQ, ISI, PSQI, RIS) }

/** A sum of the items [items] names, better the lower, all of them needed but those [optional]; counted by [steps], if given. */
private fun lowerOf(id: String, name: String, items: List<String>, bands: List<Band> = emptyList(), steps: Steps? = null, optional: List<String> = emptyList()) =
    Score(id, name, items, direction = Direction.LowerIsBetter, bands = bands, steps = steps, optional = optional)

/** Athlete Sleep Behavior Questionnaire, as in "Athlete-Sleep-Behavior-Questionnaire_ASBQ_10-2026.pdf". */
private val ASBQ: Assessment = run {
    val answers = scored("Niemals" to 1, "Selten" to 2, "Manchmal" to 3, "Häufig" to 4, "Immer" to 5)
    val items = listOf(
        "...schlafe ich am Nachmittag für zwei oder mehr Stunden.",
        "...verwende ich Stimulanzien, wenn ich trainiere/an Wettkämpfen teilnehme (z.B. Koffein).",
        "...trainiere ich spät in der Nacht (nach 19 Uhr).",
        "...konsumiere ich Alkohol, vor dem Schlafengehen (4h vor dem Schlaf).",
        "...gehe ich abends zu verschiedenen Zeiten ins Bett (mehr als 1 Stunde Differenz).",
        "...gehe ich durstig ins Bett.",
        "...gehe ich mit Muskelkater ins Bett.",
        "...benutze ich lichtabgebende Technologien in der letzten Stunde, vor dem Schlafengehen (z.B. Laptop, Telefon, Fernseher, Videospiele)",
        "...denke ich nach über/mache ich mir Sorgen um meine sportliche Leistung, wenn ich im Bett bin.",
        "...denke ich nach über/sorge ich mich um Probleme, die nichts mit meinem Sport zu tun haben, wenn ich im Bett bin.",
        "...benutze ich Schlaftabletten, um mir beim Schlafen zu helfen.",
        "...wache ich auf, um mehr als einmal pro Nacht ins Badezimmer zu gehen.",
        "...wecke ich mich und/oder meinen Bettpartner durch mein Schnarchen auf.",
        "...wecke ich mich und/oder meinen Bettpartner mit meinem Muskelzucken.",
        "...stehe ich jeden Morgen zu verschiedenen Zeiten auf (mehr als 1 Stunde Differenz).",
        "...schlafe ich Zuhause in einer weniger idealen Umgebung (z.B. zu hell, zu laut, unbequemes Bett/Kissen, zu heiß/kalt).",
        "...schlafe ich in fremden Umgebungen (z.B. Hotelzimmer).",
        "...behindern Reisen den Aufbau einer konstanten Schlaf-Wach-Routine.",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", answers) }
    Assessment(
        id = "asbq",
        name = "Athlete Sleep Behavior Questionnaire",
        abbreviation = "ASBQ",
        categories = listOf(AssessmentCategory.Sleep),
        source = "ASBQ, Stand 10/2026",
        hint = "Fragebogen zum Schlafverhalten von Sportlern. Ein Werkzeug zur Identifizierung von maladaptiven Schlafpraktiken bei " +
            "Spitzensportlern.\n\n" +
            "Im Folgenden finden Sie verschiedene Fragen vor. Bitte lesen Sie die Einleitung und füllen Sie anschließend alle Fragen " +
            "aus.\n\n" +
            "Aufgrund der bestehenden Wechselwirkung zwischen Schlaf und Leistungsfähigkeit, ist es sinnvoll das persönliche " +
            "Schlafverhalten des Einzelnen zu beobachten, um mögliche Anpassungen vorzunehmen, die die Leistung steigern könnten.\n\n" +
            "Die folgenden 18 Fragen wurden zusammengestellt, um nähere Informationen zu erhalten, in welchem Bereich das " +
            "Schlafverhalten beeinträchtigt wird. Da sich das Schlafverhalten schnell verändern kann, beziehen sich die folgenden " +
            "Fragen auf einen kurzen Zeitraum (max. 1 Monat), bitte beachten Sie dies bei der Beantwortung der Fragen.\n\n" +
            "Eine höhere Gesamtpunktzahl ist ein Hinweis auf ein schlechtes Schlafverhalten. Nach der Meinung der Autoren, entspricht " +
            "eine Gesamtpunktzahl von ≤36 einem „guten Schlafverhalten“ und ≥42 einem „schlechten Schlafverhalten“. Diese Annahme " +
            "basiert auf einer konservativen Bewertung der Schwellenwertbereich-Deskriptoren. Der untere Grenzwert von ≤36 würde eine " +
            "durchschnittliche Antwort von „selten“ für alle 18 Items darstellen, während der obere Grenzwert von ≥42 mehr als eine " +
            "Antwort von „manchmal“, „häufig“ oder „immer“ erfordern würde. Diese Grenzwerte werden jedoch nur als Richtlinien " +
            "vorgeschlagen und können in zukünftigen Studien zur Beurteilung der Sensitivität und Spezifität des ASBQ in " +
            "Sportlerpopulationen angepasst werden.",
        sections = listOf(Section(hint = "In der letzten Zeit (im letzten Monat)…", items = items)),
        scores = listOf(
            lowerOf(
                "gesamt", "Gesamtpunktzahl", ids(items),
                listOf(Band.upTo(36, "gutes Schlafverhalten"), Band.upTo(41, "mittelmäßiges Schlafverhalten"), Band.upTo(90, "schlechtes Schlafverhalten")),
            ),
        ),
    )
}

/**
 * Athlete Sleep Screening Questionnaire, as in "Athlete-Sleep-Screening-Questionnaire_ASSQ_10-2026.pdf": its score of sleep
 * difficulty and, as asked on 09.11.2026, its modifiers breathing, travel and chronotype, each with the recommendation of
 * its key. Its key's "20:15 – 0:30 Uhr" of question 10 is the question's "22:15 – 0:30 Uhr".
 */
private val ASSQ: Assessment = run {
    val perWeek = listOf("nie", "1–2-mal", "3–4-mal", "5–7-mal")
    val yesNo = scored("ja" to 1, "nein" to 0)
    val items = listOf(
        ChoiceItem(
            "1", "1. Wie viele Stunden Schlaf hattest du meist pro Nacht? Die Anzahl kann abweichend sein von der, die du im Bett verbracht hast.",
            fourToZero("5–6 Stunden", "6–7 Stunden", "7–8 Stunden", "8–9 Stunden", "mehr als 9 Stunden"),
        ),
        ChoiceItem("2", "2. Wie oft machtest du durchschnittlich pro Woche tagsüber ein Nickerchen?", unscored(*perWeek.toTypedArray())),
        ChoiceItem(
            "3", "3. Wie zufrieden/unzufrieden warst du gewöhnlich mit deiner Schlafqualität?",
            numbered("sehr zufrieden", "etwas zufrieden", "nicht zufrieden, aber auch nicht unzufrieden", "etwas unzufrieden", "sehr unzufrieden"),
        ),
        ChoiceItem(
            "4", "4. Wie lange dauerte es in der Regel bis du eingeschlafen bist?",
            numbered("15 Minuten oder weniger", "16–30 Minuten", "31–60 Minuten", "mehr als 60 Minuten"),
        ),
        ChoiceItem("5", "5. Wie oft hattest du pro Woche Schwierigkeiten am Tag wach zu bleiben?", numbered(*perWeek.toTypedArray())),
        ChoiceItem(
            "6", "6. Wie oft hast du pro Woche verschreibungspflichtige oder nicht verschreibungspflichtige Medikamente eingenommen, um besser zu schlafen?",
            numbered(*perWeek.toTypedArray()),
        ),
        ChoiceItem(
            "7", "7. Wenn du nur dein eigenes Wohlempfinden in Betracht ziehst, um wie viel Uhr würdest du aufstehen, wenn du deinen Tag völlig frei gestalten könntest?",
            fourToZero("5:00 – 6:30 Uhr", "6:30 – 7:45 Uhr", "7:45 – 9:45 Uhr", "9:45 – 11:00 Uhr", "11:00 – 12:00 Uhr"),
        ),
        ChoiceItem(
            "8", "8. Wie wach fühlst du dich gewöhnlich in der ersten halben Stunde, nach dem du wach geworden bist?",
            numbered("überhaupt nicht wach", "etwas wach", "ziemlich wach", "sehr wach"),
        ),
        ChoiceItem(
            "9", "9. Hältst du dich für einen Morgentyp oder einen Abendtyp?",
            fourToZero("definitiv einen Morgentyp", "eher ein Morgentyp als ein Abendtyp", "mehr ein Abendtyp als ein Morgentyp", "definitiv ein Abendtyp"),
        ),
        ChoiceItem(
            "10", "10. Wenn du nur dein eigenes Wohlempfinden in Betracht ziehst, um wie viel Uhr würdest du zu Bett gehen, wenn du deinen Abend völlig frei gestalten könntest?",
            fourToZero("20:00 – 21:00 Uhr", "21:00 – 22:15 Uhr", "22:15 – 0:30 Uhr", "0:30 – 1:45 Uhr", "1:45 – 3:00 Uhr"),
        ),
        ChoiceItem("11", "11. Wenn du bezüglich deines Sports reisen musst, hast du dann Schlafstörungen?", yesNo),
        ChoiceItem(
            "12", "12. Wenn du bezüglich deines Sports reisen musst, fühlst du dich dann tagsüber im Allgemeinen unwohl oder nimmst körperliche Leistungsminderungen wahr?",
            yesNo,
        ),
        ChoiceItem("13", "13. Bist du gewöhnlich ein lauter Schnarcher?", yesNo),
        ChoiceItem("14", "14. Wurde dir schon mal gesagt, dass du im Schlaf schwer atmest, nach Luft ringst oder zeitweise aufhörst zu atmen?", yesNo),
        ChoiceItem(
            "15", "15. Wie häufig nimmst du in der Regel pro Tag koffeinhaltige Produkte, wie Pillen, Kaffee, Tee, Limonaden, Energie-Drinks zu dir? " +
                "Bei Kaffee und Tee bezieht sich ein Getränk auf 170–230 ml; bei koffeinhaltigen Limonaden auf 350 ml.",
            unscored("seltener als 1-mal", "1–2-mal", "3-mal", "4-mal", "5-mal oder häufiger"),
        ),
        ChoiceItem(
            "16", "16. Wie häufig nutzt du elektronische Geräte wie Smartphone, Tablet, Computer, Fernseher innerhalb der Stunde, bevor du zu Bett gehst?",
            unscored("überhaupt nicht", "1–3-mal pro Woche", "4–6-mal pro Woche", "täglich"),
        ),
    )
    Assessment(
        id = "assq",
        name = "Athlete Sleep Screening Questionnaire",
        abbreviation = "ASSQ",
        categories = listOf(AssessmentCategory.Sleep),
        source = "ASSQ, Stand 10/2026",
        hint = "Die folgenden Fragen beziehen sich auf deine Schlafgewohnheiten. Kreuze jeweils die Antwort an, die am besten zu deinen " +
            "typischen Schlafgewohnheiten, allgemein oder in den letzten wenigen Wochen, passt.\n\n" +
            "Interventionsempfehlung\n" +
            "A = Allgemeines Informationsblatts bezüglich Schlaf speziell für Sportler und maßgeschneiderte Empfehlungen, je nach " +
            "Schlafmangel oder spezifischer Schlafstörung.\n" +
            "B = Überwachung und Nachverfolgung durch das Betreuungsteam\n" +
            "C = Beurteilung und Nachsorge durch den Sportarzt\n" +
            "D = Beurteilung und Empfehlungen durch Schlafmediziner oder qualifizierten Schlafexperten\n" +
            "E = Diagnostik für Schlafstörungen und entsprechende Behandlung\n\n" +
            "Die Modifikatoren sind nicht Bestandteil des ASSQ-Schlafschwierigkeiten-Score, da sie seltener vorkommen und nicht für " +
            "alle Sportler relevant sind (z. B. Reisen). Dennoch sind sie wichtig, um, wenn erforderlich, spezifische " +
            "Interventionsempfehlungen zu geben.\n\n" +
            "Atmung: Wenn der/die Sportler/in die Fragen 13 und/oder 14 mit “ja” beantwortet hat, dann sollte er/sie weiter " +
            "untersucht werden. Interventionsempfehlung: D + E\n\n" +
            "Reisen: Wenn der/die Sportler/in die Frage 11 mit “ja” beantwortet hat, dann ist eine Aufklärung über Reisemanagement " +
            "und Jetlag erforderlich. Interventionsempfehlung: A (spezifisch für Reisen)\n" +
            "Wenn der/die Sportler/in die Frage 12 mit “ja” beantwortet hat, dann ist die Schlafstörung schwerwiegender und " +
            "zusätzliche Interventionen, über die Aufklärung hinaus, sind erforderlich. Interventionsempfehlung: A + B + C (wenn " +
            "indiziert) + D (wenn indiziert)\n\n" +
            "Chronotyp: Die Sportler/innen, die Abendtypen sind, haben ein erhöhtes Risiko für Schlafstörungen. Addiere die " +
            "Punktzahlen aus den Fragen 7–10. Ist die Summe ≤ 4, dann deutet das darauf hin, dass der/die Sportler/in ein Abendtyp ist.\n\n" +
            "Die Fragen 2, 15 und 16 sind nicht Bestandteil des ASSQ-Schlafschwierigkeiten-Score und der Modifikatoren, dennoch sind sie " +
            "wichtig, um entsprechende Schlafoptimierungsstrategien zu empfehlen. Dazu zählen Nickerchen am Tag zu implementieren oder " +
            "deren Häufigkeit pro Woche zu erhöhen, Koffeineinnahme zu reduzieren und elektronische Geräte vor dem zu Bett gehen zu " +
            "vermeiden.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            lowerOf(
                "schwierigkeiten", "Schlafschwierigkeiten-Score", listOf("1", "3", "4", "5", "6"),
                listOf(
                    Band.upTo(4, "keine Schlafschwierigkeiten: A"),
                    Band.upTo(7, "geringe Schlafschwierigkeiten: A + B"),
                    Band.upTo(10, "mäßige Schlafschwierigkeiten: A + B + C + D + E (wenn indiziert)"),
                    Band.upTo(17, "starke Schlafschwierigkeiten: A + B + C + D + E"),
                ),
            ),
            lowerOf("atmung", "Atmung", listOf("13", "14"), listOf(Band.upTo(0, null), Band.upTo(2, "weiter untersuchen: D + E"))),
            lowerOf(
                "reisen-schlaf", "Reisen: Schlafstörungen", listOf("11"),
                listOf(Band.upTo(0, null), Band.upTo(1, "Aufklärung über Reisemanagement und Jetlag: A (spezifisch für Reisen)")),
            ),
            lowerOf(
                "reisen-tag", "Reisen: tagsüber unwohl oder Leistungsminderung", listOf("12"),
                listOf(Band.upTo(0, null), Band.upTo(1, "Schlafstörung schwerwiegender: A + B + C (wenn indiziert) + D (wenn indiziert)")),
            ),
            Score(
                "chronotyp", "Chronotyp", listOf("7", "8", "9", "10"), direction = Direction.HigherIsBetter,
                bands = listOf(Band.upTo(4, "Abendtyp"), Band.upTo(14, null)),
            ),
        ),
    )
}

/** Insomnia Severity Index, the validated German version of Dieck et al. 2018, as in "Insomnia-Severity-Index_ISI_10-2026.pdf". */
private val ISI: Assessment = run {
    val strength = numbered("Keine", "Leichte", "Mäßige", "Starke", "Sehr starke")
    val extent = numbered("Überhaupt nicht", "Wenig", "Etwas", "Stark", "Sehr stark")
    val items = listOf(
        ChoiceItem("1", "1. Schwierigkeit, einzuschlafen.", strength),
        ChoiceItem("2", "2. Schwierigkeit, durchzuschlafen.", strength),
        ChoiceItem("3", "3. Das Problem, am Morgen früh aufzuwachen.", numbered("Kein", "Leicht", "Mäßig", "Stark", "Sehr stark")),
        ChoiceItem(
            "4", "4. Wie zufrieden/unzufrieden sind Sie mit Ihrem aktuellen Schlafverhalten?",
            numbered("Sehr zufrieden", "Zufrieden", "Neutral", "Unzufrieden", "Sehr unzufrieden"),
        ),
        ChoiceItem(
            "5", "5. In welchem Ausmaß beeinflussen Ihre Schlafprobleme Ihrer Ansicht nach Ihre Alltagsfunktionen (z.B. Tagesmüdigkeit, " +
                "Fähigkeit auf der Arbeit/bei Alltagstätigkeiten zu funktionieren, Konzentration, Erinnerung, Stimmung)?",
            extent,
        ),
        ChoiceItem(
            "6", "6. In welchem Ausmaß nehmen Ihrer Ansicht nach Ihre Mitmenschen Ihr Schlafproblem und die damit verbundene Beeinflussung Ihrer Lebensqualität wahr?",
            extent,
        ),
        ChoiceItem("7", "7. Wie besorgt/bedrückt sind Sie durch Ihre gegenwärtigen Schlafprobleme?", extent),
    )
    Assessment(
        id = "isi",
        name = "Insomnia Severity Index",
        abbreviation = "ISI",
        categories = listOf(AssessmentCategory.Sleep),
        source = "ISI, Stand 10/2026",
        hint = "Validierte deutsche Version (Dieck et al. 2018).\n\n" +
            "Bitte kreuzen Sie für jede der folgenden Fragen die Zahl an, die am besten Ihrem Schlafmuster im letzten Monat entspricht. " +
            "Bei den ersten 3 Fragen geht es um eine Beurteilung Ihrer Schlafschwierigkeiten.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            lowerOf(
                "gesamt", "Gesamt", ids(items),
                listOf(
                    Band.upTo(7, "keine Schlaflosigkeit"),
                    Band.upTo(14, "unterschwellige Schlaflosigkeit"),
                    Band.upTo(21, "moderate Schlaflosigkeit"),
                    Band.upTo(28, "schwere Schlaflosigkeit"),
                ),
            ),
        ),
    )
}

/**
 * Pittsburgh Schlafqualitäts-Index, as in "Pittsburgh-Schlafqualitaets-Index_PSQI_10-2026.pdf". Its PDF gives no rule for
 * the components; as asked on 09.11.2026, those of Buysse et al. 1989, each 0 to 3, the total 0 to 21 named after Riemann
 * and Backhaus. Questions 5 to 9 make 0 to 3, the PDF's 1 to 4 less one; 5j's frequency left out counts as "gar nicht",
 * as its PDF says. Questions 10 and those of the person count nowhere.
 */
private val PSQI: Assessment = run {
    val frequency = listOf("Während der letzten vier Wochen gar nicht", "Weniger als einmal pro Woche", "Einmal oder zweimal pro Woche", "Dreimal oder häufiger pro Woche")
    val often = numbered(*frequency.toTypedArray())
    val times = listOf(
        TimeItem("1", "1. Wann sind Sie während der letzten vier Wochen gewöhnlich abends zu Bett gegangen? übliche Uhrzeit:"),
        NumberItem(
            "2", "2. Wie lange hat es während der letzten vier Wochen gewöhnlich gedauert, bis Sie nachts eingeschlafen sind?", unit = "in Minuten",
            steps = Steps(listOf(16 to 0, 31 to 1, 61 to 2), otherwise = 3),
        ),
        TimeItem("3", "3. Wann sind Sie während der letzten vier Wochen gewöhnlich morgens aufgestanden? übliche Uhrzeit:"),
        TimeItem(
            "4", "4. Wieviele Stunden haben Sie während der letzten vier Wochen pro Nacht tatsächlich geschlafen? (Das muss nicht mit der " +
                "Anzahl der Stunden, die Sie im Bett verbracht haben, übereinstimmen.) Effektive Schlafzeit (Stunden) pro Nacht",
            duration = true,
        ),
    )
    val reasons = listOf(
        "a" to "a) ... weil Sie nicht innerhalb von 30 Minuten einschlafen konnten?",
        "b" to "b) ... weil Sie mitten in der Nacht oder früh morgens aufgewacht sind?",
        "c" to "c) ... weil Sie aufstehen mussten, um zur Toilette zu gehen?",
        "d" to "d) ... weil Sie Beschwerden beim Atmen hatten?",
        "e" to "e) ... weil Sie husten mussten oder laut geschnarcht haben?",
        "f" to "f) ... weil Ihnen zu kalt war?",
        "g" to "g) ... weil Ihnen zu warm war?",
        "h" to "h) ... weil Sie schlecht geträumt hatten?",
        "i" to "i) ... weil Sie Schmerzen hatten?",
    ).map { (letter, text) -> ChoiceItem("5$letter", text, often) } + listOf(
        TextItem("5j-text", "j) ... aus anderen Gründen? Bitte beschreiben:"),
        ChoiceItem("5j", "Und wie oft während des letzten Monats konnten Sie aus diesem Grund schlecht schlafen?", often),
    )
    val rest = listOf(
        ChoiceItem("6", "6. Wie würden Sie insgesamt die Qualität Ihres Schlafes während der letzten vier Wochen beurteilen?", numbered("Sehr gut", "Ziemlich gut", "Ziemlich schlecht", "Sehr schlecht")),
        ChoiceItem("7", "7. Wie oft haben Sie während der letzten vier Wochen Schlafmittel eingenommen (vom Arzt verschriebene oder frei verkäufliche)?", often),
        ChoiceItem(
            "8", "8. Wie oft hatten Sie während der letzten vier Wochen Schwierigkeiten wachzubleiben, etwa beim Autofahren, beim Essen oder bei gesellschaftlichen Anlässen?",
            often,
        ),
        ChoiceItem(
            "9", "9. Hatten Sie während der letzten vier Wochen Probleme, mit genügend Schwung die üblichen Alltagsaufgaben zu erledigen?",
            numbered("Keine Probleme", "Kaum Probleme", "Etwas Probleme", "Große Probleme"),
        ),
        ChoiceItem(
            "10", "10. Schlafen Sie allein in Ihrem Zimmer?",
            unscored(
                "Ja", "Ja, aber ein Partner/Mitbewohner schläft in einem anderen Zimmer", "Nein, der Partner schläft im selben Zimmer, aber nicht im selben Bett",
                "Nein, der Partner schläft im selben Bett",
            ),
        ),
    )
    val noticed = listOf(
        "a" to "a) Lautes Schnarchen",
        "b" to "b) Lange Atempausen während des Schlafes",
        "c" to "c) Zucken oder ruckartige Bewegungen der Beine während des Schlafes",
        "d" to "d) Nächtliche Phasen von Verwirrung oder Desorientierung während des Schlafes",
    ).map { (letter, text) -> ChoiceItem("10$letter", text, unscored(*frequency.toTypedArray())) } +
        TextItem("10e", "e) Oder andere Formen von Unruhe während des Schlafes. Bitte beschreiben:")
    val person = listOf(
        NumberItem("alter", "Alter", unit = "Jahre"),
        NumberItem("groesse", "Körpergröße", unit = "cm"),
        NumberItem("gewicht", "Gewicht", unit = "kg", decimals = true),
        ChoiceItem("geschlecht", "Geschlecht", unscored("weiblich", "männlich")),
        ChoiceItem("beruf", "Beruf", unscored("Schüler/-in, Student/-in", "Angestellte/-r", "selbständig", "Rentner/-in", "arbeitslos, Hausfrau/-mann", "Arbeiter/-in")),
    )
    val disturbances = listOf("5b", "5c", "5d", "5e", "5f", "5g", "5h", "5i", "5j")
    Assessment(
        id = "psqi",
        name = "Pittsburgh Schlafqualitäts-Index",
        abbreviation = "PSQI",
        categories = listOf(AssessmentCategory.Sleep),
        source = "PSQI, Stand 10/2026",
        hint = "Schlafqualitäts-Fragebogen\n\n" +
            "Die folgenden Fragen beziehen sich auf Ihre üblichen Schlafgewohnheiten und zwar nur während der letzten vier Wochen. Ihre " +
            "Antworten sollten möglichst genau sein und sich auf die Mehrzahl der Tage und Nächte während der letzten vier Wochen " +
            "beziehen. Beantworten Sie bitte alle Fragen.\n\n" +
            "Die Auswertung ist nur gültig bei vollständig ausgefülltem Fragebogen!",
        sections = listOf(
            Section(items = times),
            Section(
                hint = "Kreuzen Sie bitte für jede der folgenden Fragen die für Sie zutreffende Antwort an. Beantworten Sie bitte alle Fragen.\n\n" +
                    "5. Wie oft haben Sie während der letzten vier Wochen schlecht geschlafen, ...",
                items = reasons,
            ),
            Section(items = rest),
            Section(
                hint = "Falls Sie einen Mitbewohner/Partner haben, fragen Sie sie/ihn bitte, ob und wie oft er/sie bei Ihnen folgendes bemerkt hat.\n\n" +
                    "Fragen 10 a-e gehen nicht in die quantitative Auswertung ein.",
                items = noticed,
            ),
            Section("Machen Sie bitte noch folgende Angaben zu Ihrer Person:", items = person),
        ),
        scores = listOf(
            lowerOf("k1", "Subjektive Schlafqualität", listOf("6")),
            lowerOf("k2", "Schlaflatenz", listOf("2", "5a"), steps = Steps(listOf(1 to 0, 3 to 1, 5 to 2), otherwise = 3)),
            lowerOf("k3", "Schlafdauer", listOf("4"), steps = Steps(listOf(5 * 60 to 3, 6 * 60 to 2, 7 * 60 to 1), otherwise = 0)),
            Score(
                "k4", "Schlafeffizienz", listOf("4", "1", "3"), rule = ScoreRule.ShareOfSpan, direction = Direction.LowerIsBetter,
                steps = Steps(listOf(65 to 3, 75 to 2, 85 to 1), otherwise = 0),
            ),
            lowerOf("k5", "Schlafstörungen", disturbances, steps = Steps(listOf(1 to 0, 10 to 1, 19 to 2), otherwise = 3), optional = listOf("5j")),
            lowerOf("k6", "Schlafmittelkonsum", listOf("7")),
            lowerOf("k7", "Tagesmüdigkeit", listOf("8", "9"), steps = Steps(listOf(1 to 0, 3 to 1, 5 to 2), otherwise = 3)),
            Score(
                "gesamt", "Gesamtwert", emptyList(), direction = Direction.LowerIsBetter, sumOf = listOf("k1", "k2", "k3", "k4", "k5", "k6", "k7"),
                bands = listOf(Band.upTo(5, "gute Schläfer"), Band.upTo(10, "schlechte Schläfer"), Band.upTo(21, "chronische Schlafstörung")),
            ),
        ),
    )
}

/** Regensburger Insomnie Skala, as in "Regensburger-Insomnie-Skala_RIS_10-2026.pdf". */
private val RIS: Assessment = run {
    val frequency = listOf("immer", "meistens", "manchmal", "selten", "nie")
    val statements = listOf(
        "Ich kann nicht durchschlafen.",
        "Ich wache zu früh auf.",
        "Ich wache schon bei leichten Geräuschen auf.",
        "Ich habe das Gefühl, die ganze Nacht kein Auge zugetan zu haben.",
        "Ich denke viel über meinen Schlaf nach.",
        "Ich habe Angst ins Bett zu gehen, da ich befürchte, nicht schlafen zu können.",
        "Ich fühle mich voll leistungsfähig.",
        "Ich nehme Schlafmittel, um einschlafen zu können.",
    ).mapIndexed { i, text ->
        val n = i + 3
        // "Ich fühle mich voll leistungsfähig" makes 4 for "nie".
        ChoiceItem("$n", "$n. $text", if (n == 9) numbered(*frequency.toTypedArray()) else fourToZero(*frequency.toTypedArray()))
    }
    val items = listOf(
        ChoiceItem("1", "1. Wie viele Minuten brauchen Sie in der Regel um einzuschlafen?", numbered("1-20 min.", "20-40 min.", "40-60 min.", "60-90 min.", "mehr als 90 min.")),
        ChoiceItem("2", "2. Wie viele Stunden glauben Sie, durchschnittlich nachts zu schlafen?", numbered("7 und mehr", "5-6", "4", "2-3", "0-1")),
    ) + statements
    Assessment(
        id = "ris",
        name = "Regensburger Insomnie Skala",
        abbreviation = "RIS",
        categories = listOf(AssessmentCategory.Sleep),
        source = "RIS, Stand 10/2026",
        hint = "Die folgenden Fragen sollen Ihrem behandelnden Therapeuten eine Einschätzung Ihrer Schlafstörung ermöglichen. Bitte " +
            "beantworten Sie die Fragen, indem Sie nachfolgend bei der zutreffenden Antwort ein Kreuz machen. Es darf nur ein Kreuz pro " +
            "Frage bzw. Aussage gemacht werden. Die Fragen beziehen sich auf die letzten vier Wochen.\n\n" +
            "Bei 13 Punkten sollten Hinweise zum Schlafmanagement gegeben werden (siehe Datei „Schlafhygiene in der Physiotherapie“). Ab " +
            "25 Punkten sollte ein Spezialist konsultiert werden.",
        sections = listOf(
            Section(
                "0. Meine üblichen Bettzeiten sind:",
                items = listOf(TimeItem("0-von", "von … Uhr nachts"), TimeItem("0-bis", "bis … Uhr am nächsten Tag")),
            ),
            Section(items = items),
        ),
        scores = listOf(
            lowerOf("gesamt", "Gesamt", ids(items), listOf(Band.upTo(12, "Unauffällig"), Band.upTo(24, "Auffällig"), Band.upTo(40, "Ausgeprägt"))),
        ),
    )
}
