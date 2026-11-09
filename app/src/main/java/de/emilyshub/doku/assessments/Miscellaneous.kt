package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Verschiedenes" (#76), each as in its PDF of the date in its source. None of them
 * says what of a missing answer: each result needs all of its items, as in #72; the Wells-Scores count what is checked,
 * nothing checked being 0. Where a PDF falls short, as asked on 09.11.2026: the ICIQ names its bands after Klovning et
 * al. 2009 and has the two answers of its fourth question that the PDF prints in one row apart; each yes of the GAQ-PP
 * counts towards its recommendation, and that it was talked over is asked of those who said yes, changing nothing of it.
 */
internal val MISCELLANEOUS: List<Assessment> by lazy { listOf(GAQ_PP, ICIQ_UI_SF, WELLS_LE, WELLS_TVT) }

/**
 * A criterion of a Wells-Score, its [tenths] of points named after it as the PDF prints them, such as "1,5" or "3,0": with
 * its decimal, which TestNumber.format leaves out of a whole number.
 */
private fun criterion(id: String, label: String, tenths: Int): Option {
    val points = "%s%d,%d".format(if (tenths < 0) "−" else "", Math.abs(tenths) / 10, Math.abs(tenths) % 10)
    return Option(id, "$label · $points Punkte", tenths)
}

/** A Wells-Score of [criteria] to check, in tenths of points: their sum, better the lower, in [bands]. */
private fun wells(id: String, name: String, source: String, hint: String, criteria: List<Option>, bands: List<Band>): Assessment {
    val list = ChecklistItem("kriterien", "Klinische Charakteristik", criteria, scored = true)
    return Assessment(
        id = id,
        name = name,
        categories = listOf(AssessmentCategory.Miscellaneous),
        source = source,
        hint = hint,
        sections = listOf(Section(items = listOf(list))),
        // Points in tenths, such as 1,5, as an option's points are whole.
        scores = listOf(ItemScore("gesamt", "Gesamtpunktzahl", listOf(list.id), direction = Direction.LowerIsBetter, bands = bands, divisor = 10)),
    )
}

/** Wells-Score für Lungenembolie, as in "Wells-Score-Lungenembolie_11-2026.pdf". */
private val WELLS_LE: Assessment = wells(
    "wells-le", "Wells-Score Lungenembolie", "Wells-Score Lungenembolie, Stand 11/2026",
    "Bestimmung der klinischen Wahrscheinlichkeit einer Lungenembolie",
    listOf(
        criterion("tvt", "Symptome einer Tiefen Venenthrombose (TVT)", 30),
        criterion("keine-alternative", "Keine alternative Diagnose liefert eine bessere Erklärung für die Erkrankung", 10),
        criterion("tachykardie", "Tachykardie mit einem Puls von > 100", 15),
        criterion("immobilisation", "Immobilisation (≥ 3 Tage) oder Operation in den vorhergehenden vier Wochen", 15),
        criterion("anamnese", "TVT oder Lungenembolie in der Anamnese", 15),
        criterion("haemoptyse", "Hämoptyse vorhanden", 10),
        criterion("malignitaet", "Malignität vorhanden", 10),
    ),
    listOf(Band.below(2, "geringe Wahrscheinlichkeit"), Band.upTo(6, "mäßige Wahrscheinlichkeit"), Band.rest("hohe Wahrscheinlichkeit")),
)

/** Wells-Score – Tiefe Venenthrombose (TVT), as in "Wells-Score-Tiefe-Venenthrombose_TVT_11-2026.pdf". */
private val WELLS_TVT: Assessment = wells(
    "wells-tvt", "Wells-Score tiefe Venenthrombose (TVT)", "Wells-Score Tiefe Venenthrombose, Stand 11/2026",
    "Bestimmung der klinischen Wahrscheinlichkeit einer tiefen Venenthrombose\n\n" +
        "Wichtiger Hinweis:\n" +
        "• D-Dimere sind nur sinnvoll, wenn der Wells-Score niedrig ist, da sie auch durch andere Faktoren erhöht sein können " +
        "(z. B. Infektion, Trauma, Schwangerschaft).\n" +
        "• Bei hohem Score ist eine direkte Bildgebung (Ultraschall) die Methode der Wahl.",
    listOf(
        criterion("krebs", "Aktive Krebserkrankung", 10),
        criterion("laehmung", "Lähmung oder kürzliche Immobilisation der Beine", 10),
        criterion("bettruhe", "Bettruhe (> 3 Tage); große Chirurgie (< 12 Wochen)", 10),
        criterion("schmerz", "Schmerz/Verhärtung entlang der tiefen Venen", 10),
        criterion("bein", "Schwellung ganzes Bein", 10),
        criterion("unterschenkel", "Unterschenkelschwellung > 3 cm gegenüber Gegenseite", 10),
        criterion("oedem", "Eindrückbares Ödem am symptomatischen Bein", 10),
        criterion("kollateralvenen", "Kollateralvenen", 10),
        criterion("frueher", "Frühere, dokumentierte tiefe Venenthrombose", 10),
        criterion("alternative", "Alternative Diagnose mindestens ebenso wahrscheinlich wie tiefe Venenthrombose", -20),
    ),
    listOf(
        Band.upTo(0, "TVT unwahrscheinlich (D-Dimer-Test sinnvoll)"),
        Band.upTo(2, "Mittleres Risiko (D-Dimere und evtl. Ultraschall empfohlen)"),
        Band.upTo(9, "TVT wahrscheinlich (Ultraschall/Phlebografie notwendig)"),
    ),
)

/**
 * International Consultation on Incontinence Questionnaire – Urinary Incontinence Short Form, as in
 * "International-Consultation-on-Incontinence-Modular-Questionnaire-Urinary-Incontinence-short-form_Deutsche-Version_ICIQ_11-2026.pdf":
 * the sum of its first three questions, its bands after Klovning et al. 2009, as the PDF's end at "Starke Inkontinenz: 11"
 * (09.11.2026).
 */
private val ICIQ_UI_SF: Assessment = run {
    val items = listOf(
        ChoiceItem(
            "1", "1. Wie häufig verlieren Sie Harn?",
            scored(
                "niemals" to 0,
                "ungefähr 1 mal pro Woche oder weniger" to 1,
                "zwei oder drei mal pro Woche" to 2,
                "ungefähr 1 mal pro Tag" to 3,
                "mehrmals am Tag" to 4,
                "ständig" to 5,
            ),
        ),
        ChoiceItem(
            "2",
            "2. Wir würden gerne wissen, wieviel Harn Sie Ihrer Meinung nach verlieren. Wieviel Harn verlieren Sie gewöhnlich? " +
                "(unabhängig davon, ob Sie Vorlagen tragen oder nicht)",
            scored("kein Harnverlust" to 0, "eine kleine Menge Harn" to 2, "eine mittlere Menge Harn" to 4, "eine große Menge Harn" to 6),
        ),
        ScaleItem(
            "3",
            "3. Wie sehr beeinträchtigt generell der Harnverlust Ihren Alltag? Bitte markieren Sie eine Zahl zwischen 0 " +
                "(überhaupt nicht) und 10 (ein schwerwiegendes Problem)",
            minLabel = "überhaupt nicht", maxLabel = "schwerwiegend",
        ),
        ChecklistItem(
            "4", "4. Wann verlieren Sie Harn?",
            listOf(
                Option("niemals", "niemals - kein Harnverlust", exclusive = true),
                Option("toilette", "Harnverlust vor dem Erreichen der Toilette"),
                Option("husten", "Harnverlust beim Husten oder Niesen"),
                Option("schlaf", "Harnverlust während des Schlafes"),
                // One row of the PDF, two answers of the original ICIQ (09.11.2026).
                Option("sport", "Harnverlust bei körperlicher Betätigung/sportlicher Aktivität"),
                Option("nachher", "Harnverlust nach dem Wasserlassen und Wiederankleiden"),
                Option("ursache", "Harnverlust ohne erkennbare Ursache"),
                Option("staendig", "ständiger Harnverlust"),
            ),
        ),
    )
    Assessment(
        id = "iciq-ui-sf",
        name = "International Consultation on Incontinence Questionnaire – Urinary Incontinence Short Form, deutsche Version",
        abbreviation = "ICIQ-UI SF",
        categories = listOf(AssessmentCategory.Miscellaneous),
        source = "ICIQ, Deutsche Version, Stand 11/2026",
        hint = "Viele Menschen haben gelegentlich Probleme mit unwillkürlichem Harnverlust. Wir versuchen zu ermitteln, wie viele " +
            "Menschen ungewollt Harn verlieren und wie sehr dies ein Problem für sie ist.\n" +
            "Wir wären Ihnen sehr dankbar, wenn Sie die folgenden Fragen beantworten würden und dabei daran denken, wie es Ihnen in " +
            "den vergangenen 4 Wochen gegangen ist. Kreuzen Sie bitte jeweils ein Feld an.\n\n" +
            "Nach Nyström beträgt die minimale klinische Veränderung 2,52 Punkte.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            ItemScore(
                "gesamt", "Summe der Fragen 1. + 2. + 3.", listOf("1", "2", "3"), direction = Direction.LowerIsBetter,
                bands = listOf(
                    Band.upTo(0, "Keine Inkontinenz"),
                    Band.upTo(5, "Leichte Inkontinenz"),
                    Band.upTo(12, "Mäßige Inkontinenz"),
                    Band.upTo(18, "Starke Inkontinenz"),
                    Band.upTo(21, "Sehr starke Inkontinenz"),
                ),
            ),
        ),
    )
}

/**
 * Get Active Questionnaire for Postpartum, the German version not validated, as in
 * "Get-Active-Questionnaire-for-Postpartum_GAQ-PP_11-2026.pdf": a screening without a sum of points, its recommendation by
 * the count of its yeses, each of questions 1a to 1p, 2 and 3 counting (09.11.2026).
 */
private val GAQ_PP: Assessment = run {
    val symptoms = listOf(
        "Bewusstlosigkeit aus irgendeinem Grund?",
        "Neurologische Symptome wie z. B. schlechte Koordination oder Muskelschwäche mit Einfluss auf das Gleichgewicht?",
        "Tiefe Venenthrombose (Blutgerinnsel in den Beinen; kann Schmerzen, Schwellung oder warme/rote Haut verursachen) oder " +
            "Lungenembolie (Blutgerinnsel in der Lunge; kann Kurzatmigkeit und Schwindel verursachen)?",
        "Hoher Blutdruck (>140/90 mmHg), der nicht stabil ist?",
        "Essstörung(en) oder Mangelernährung?",
        "Postpartale Kardiomyopathie (Herzerkrankung nach der Geburt)?",
        "Neue Symptome einer Herzerkrankung (z. B. Brustschmerzen) oder eines Schlaganfalls (z. B. Gesichtslähmung, verwaschene " +
            "Sprache) im Alltag oder in Ruhe?",
        "Starke Bauchschmerzen?",
        "Brustschmerzen, Schwindel oder Benommenheit während körperlicher Betätigung?",
        "Atembeschwerden wie z. B. Kurzatmigkeit in Ruhe, die nicht durch Medikamente besser werden?",
        "Nierenerkrankung?",
        "Übermäßige Müdigkeit (z. B. Erschöpfung, die sich durch Ruhe nicht verbessert)?",
        "Schwere Infektion mit Fieber, Gliederschmerzen oder geschwollenen Lymphknoten?",
        "Knochenbrüche oder andere schwerwiegende Verletzungen?",
        "Schmerzen nach Kaiserschnitt, die sich bei Bewegung verstärken (z. B. an der Schnittstelle)?",
        "Vaginale Blutung, die nicht mit der Menstruation zusammenhängt?",
    ).mapIndexed { i, text -> ('a' + i).let { letter -> YesNoItem("1$letter", "$letter. $text") } }
    val illness = YesNoItem(
        "2", "2. Haben Sie eine andere Erkrankung, die Ihre Fähigkeit zur körperlichen Aktivität nach der Geburt beeinträchtigen " +
            "könnte? Welche Erkrankung?",
    )
    val worries = YesNoItem("3", "3. Machen Sie sich Sorgen darüber, nach der Geburt wieder körperlich aktiv zu werden oder Ihre Aktivität zu steigern?")
    val questions = symptoms + illness + worries
    val anyYes = questions.map { Condition.ifYes(it.id) }.reduce(Condition::or)

    /** The frequency, intensity and duration of one row of the table, by [id], each asked with its [question]. */
    fun activity(id: String, question: String) = listOf(
        ChoiceItem("$id-haeufigkeit", "$question – Häufigkeit (pro Woche)", unscored("0", "1–2", "3–4", "5–7")),
        ChoiceItem("$id-intensitaet", "$question – Intensität", unscored("leicht", "moderat", "intensiv")),
        ChoiceItem("$id-dauer", "$question – Dauer (Minuten pro Einheit)", unscored("< 20", "20–30", "31–60", "> 60")),
    )
    Assessment(
        id = "gaq-pp",
        name = "Get Active Questionnaire for Postpartum",
        abbreviation = "GAQ-PP",
        categories = listOf(AssessmentCategory.Miscellaneous),
        source = "GAQ-PP, nicht validierte deutsche Version, Stand 11/2026",
        hint = "Körperliche Aktivität nach der Geburt bringt viele gesundheitliche Vorteile mit sich und ist in der Regel sowohl " +
            "für Sie als auch Ihr Baby unbedenklich.\n" +
            "Bei bestimmten gesundheitlichen Bedingungen wird körperliche Aktivität jedoch nicht empfohlen. Dieser Fragebogen soll " +
            "Ihnen dabei helfen zu entscheiden, ob Sie vor Beginn oder Fortsetzung Ihrer körperlichen Aktivität mit Ihrer " +
            "behandelnden medizinischen Fachkraft (z. B. Ärztin/Arzt oder Hebamme) sprechen sollten.\n\n" +
            "Bitte beantworten Sie jede Frage nach bestem Wissen mit Ja oder Nein. Kreuzen Sie an!\n" +
            "Wenn sich Ihr Gesundheitszustand innerhalb der ersten 12 Monate nach der Geburt ändert, füllen Sie bitte diesen " +
            "Fragebogen erneut aus.",
        sections = listOf(
            Section(
                items = listOf(
                    TextItem("entbindungsdatum", "Entbindungsdatum"),
                    NumberItem("wochen", "Anzahl der Wochen nach der Entbindung", unit = "Wochen"),
                ),
            ),
            Section("1. Haben Sie im ersten Jahr nach der Geburt eines oder mehrere der folgenden Symptome erlebt?", items = symptoms),
            Section(
                items = listOf(
                    illness,
                    TextItem("2-angabe", "Bitte angeben:", showIf = Condition.ifYes(illness.id)),
                    worries,
                    TextItem("3-erlaeuterung", "Bitte erläutern Sie:", showIf = Condition.ifYes(worries.id)),
                ),
            ),
            Section(
                items = listOf(
                    TextItem(
                        "aktivitaeten",
                        "Welche körperlichen Aktivitäten machen Sie typischerweise in einer Woche? (z. B. Schwimmen, Gehen, Krafttraining, Yoga)",
                    ),
                ),
            ),
            Section(
                hint = "Wie oft und wie lange betreiben Sie in einer typischen Woche körperliche Aktivität mit leichter, moderater oder " +
                    "intensiver Intensität?\n\n" +
                    "Definitionen\n\n" +
                    "Körperliche Aktivität mit leichter Intensität:\n" +
                    "• Sie bewegen sich, aber schwitzen nicht und atmen nicht schwer – zum Beispiel beim Gehen zum Briefkasten oder beim " +
                    "leichten Gärtnern.\n\n" +
                    "Körperliche Aktivität mit mittlerer Intensität:\n" +
                    "• Ihr Herzschlag wird schneller, Sie könnten schwitzen oder schwerer atmen. Sie können noch sprechen, aber nicht " +
                    "mehr singen. Beispiele: zügiges Gehen.\n\n" +
                    "Körperliche Aktivität mit hoher Intensität:\n" +
                    "• Ihr Herzschlag steigt deutlich an, Sie fühlen sich heiß und schwitzen. Sie können nur noch wenige Worte sprechen, " +
                    "ohne eine Pause zum Atmen machen zu müssen. Beispiele: schnelles Radfahren auf dem Heimtrainer oder Laufen.",
                items = activity("vorher", "Wie aktiv waren Sie in den sechs Monaten vor der Schwangerschaft?") +
                    activity("derzeit", "Wie aktiv sind Sie derzeit?") +
                    activity("ziele", "Was sind Ihre Ziele für die nächsten 6 Monate?"),
            ),
            Section(
                "Allgemeine Empfehlungen für körperliche Aktivität im ersten Jahr nach der Geburt:",
                hint = "Befolgen Sie die kanadischen Richtlinien 2025 für körperliche Aktivität, sitzendes Verhalten und Schlaf im ersten " +
                    "Jahr nach der Geburt.\n" +
                    "Empfohlen wird ein individuelles, schrittweises, symptombasiertes Vorgehen mit dem Ziel: mindestens 120 Minuten " +
                    "moderate bis intensive körperliche Aktivität pro Woche (z. B. Krafttraining, zügiges Gehen, Schwimmen, " +
                    "Gartenarbeit), verteilt auf vier oder mehr Tage pro Woche.\n\n" +
                    "Empfohlen wird außerdem ein Screening (und ggf. Behandlung) möglicher Hindernisse für Bewegung: psychische " +
                    "Gesundheit, Beckenboden- und Bauchmuskelfunktion, Schmerzen im Bewegungsapparat, Wundheilung, übermäßige " +
                    "Müdigkeit, Schlafstörungen, Angst vor Bewegung, Stillstatus, soziales/emotionales Umfeld, Essstörungen.\n\n" +
                    "Bei Fragen wenden Sie sich bitte an einen Therapeut oder eine medizinische Fachkraft, um sicherzustellen, dass Ihre " +
                    "körperliche Aktivität sicher und für Sie geeignet ist.\n\n" +
                    "Ich habe alle Angaben in diesem Fragebogen nach bestem Wissen und Gewissen korrekt beantwortet.\n" +
                    "Sobald sich mein Gesundheitszustand ändert, werde ich den Fragebogen erneut ausfüllen.",
                items = listOf(
                    YesNoItem(
                        "abgesprochen",
                        "Ich habe bereits mit meinem Arzt/Ärztin oder meiner Hebamme gesprochen und mir wurde körperliche Aktivität " +
                            "während der Zeit nach der Geburt empfohlen. Wenn sich mein Gesundheitszustand ändert, werde ich erneut " +
                            "Rücksprache halten.",
                        showIf = anyYes,
                    ),
                ),
            ),
        ),
        scores = listOf(
            ItemScore(
                "empfehlung", "Empfehlung", ids(questions), unit = "× Ja", direction = Direction.LowerIsBetter,
                bands = listOf(
                    Band.upTo(0, "Körperliche Aktivität wird empfohlen."),
                    Band.upTo(questions.size, "Vor Beginn oder Fortsetzung körperlicher Aktivität mit Arzt/Ärztin oder Hebamme sprechen."),
                ),
            ),
        ),
    )
}
