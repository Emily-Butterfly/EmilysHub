package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Schmerz und Psychosoziales", its second part, the psychosocial (#72), each as in its
 * PDF of the date in its source. None of them says what of a missing answer: each result needs all of its items, but the
 * PHQ-15's question for women only (09.11.2026).
 */
internal val PSYCHOSOCIAL: List<Assessment> by lazy { listOf(FABQ, TSK, IES_R, I_PRRS, PHQ_15, PHQ_4, SCREENING, STRESS_LOAD, STRESS_TENSION) }

/** A sum of [items], better the lower, all of them needed. */
private fun lower(id: String, name: String, items: List<Item>, bands: List<Band> = emptyList()) =
    Score(id, name, ids(items), direction = Direction.LowerIsBetter, bands = bands)

/** Fear Avoidance Belief Questionnaire, German version, as in "Fear-Avoidance-Belief-Questionnaire_FABQ_09-2026.pdf". */
private val FABQ: Assessment = run {
    fun statement(n: Int, text: String) = ScaleItem("$n", "$n. $text", 0, 6, "stimme definitiv nicht zu", "volle Zustimmung")
    val activity = listOf(
        "Mein Schmerz wurde durch körperliche Betätigung verursacht.",
        "Körperliche Betätigung verschlimmert meinen Schmerz.",
        "Körperliche Betätigung könnte meinem Rücken schaden.",
        "Ich sollte keine körperlichen Tätigkeiten verrichten, die meinen Schmerz verstärken (könnten).",
        "Ich kann keine körperlichen Tätigkeiten verrichten, die meinen Schmerz verstärken (könnten).",
    ).mapIndexed { i, text -> statement(i + 1, text) }
    val work = listOf(
        "Mein Schmerz wurde durch meine Arbeit oder durch einen Unfall bei meiner Arbeit verursacht.",
        "Meine Arbeit verstärkte meinen Schmerz.",
        "Ich habe eine Forderung nach Schadensersatz bezüglich meines Schmerzes gestellt.",
        "Meine Arbeit ist für mich zu schwer.",
        "Meine Arbeit verstärkt (oder könnte) meine Schmerzen (verstärken).",
        "Meine Arbeit schadet meinem Rücken.",
        "Ich sollte meiner regulären Arbeit nicht mit meinen momentanen Schmerzen nachgehen.",
        "Ich kann meine normale Arbeit nicht mit meinen momentanen Schmerzen ausführen.",
        "Ich kann meine normale Arbeit nicht wieder aufnehmen, bis mein Schmerz behandelt wurde.",
        "Ich denke nicht, dass ich innerhalb von 3 Monaten zu meiner normalen Arbeit zurückkehren kann.",
        "Ich denke nicht, dass ich jemals wieder werde arbeiten können.",
    ).mapIndexed { i, text -> statement(i + 6, text) }
    val items = activity + work
    fun of(vararg numbers: Int) = numbers.map { items[it - 1] }
    Assessment(
        id = "fabq",
        name = "Fear Avoidance Belief Questionnaire",
        abbreviation = "FABQ",
        categories = listOf(AssessmentCategory.PainPsychosocial, AssessmentCategory.JawCmd),
        source = "FABQ, Stand 09/2026",
        hint = "Deutsche Version.\n\n" +
            "Hier sind ein paar Aussagen anderer Patienten über ihren Schmerz. Bitte kreuzen Sie für jede Aussage eine Nummer " +
            "zwischen 0 und 6 an. Bitte beurteilen Sie, inwieweit körperliche Betätigungen wie z.B. Vorneigen, Heben, Gehen " +
            "oder Fahren ihren Rückenschmerz beeinflussen oder beeinflussen würden. Die Wertung „6“ entspricht einer vollen " +
            "Zustimmung. Werten Sie mit „0“, dann stimmen Sie definitiv nicht zu. Die Zahlen im mittleren Bereich sind mit " +
            "einer unentschiedenen Haltung gleichzusetzen.\n\n" +
            "Bitte beantworten Sie alle Fragen gemäß Ihrem aktuellen Zustand. Sollten Sie momentan keine Beschwerden haben, " +
            "dann bewerten Sie die Fragen entsprechend Ihrem Zustand in der vergangenen Woche.\n\n" +
            "Dieser Fragebogen dient der Erfassung einer angstbedingten Vermeidungshaltung. Die Fragen 1 – 5 beziehen sich auf " +
            "körperliche Aktivitäten, die Fragen 6 – 16 auf die Arbeit.\n\n" +
            "Körperliche Aktivität (FABQ – PA) = Items 2, 3, 4, 5: maximal 24 Punkte, ein hoher Wert und damit ein höheres " +
            "Chronifizierungsrisiko besteht bei > 15. Die minimal messbare Veränderung beträgt 5,4 Punkte (George 2010).\n\n" +
            "Arbeitsbelastung (FABQ – W) = Items 6, 7, 9, 10, 11, 12, 15: maximal 42 Punkte, das erhöhte " +
            "Chronifizierungsrisiko besteht bei > 34 Punkten. Die minimal messbare Veränderung beträgt 6,8 Punkte (George " +
            "2010).\n\n" +
            "Die Fragen 1, 8, 13, 14 und 16 können zu Verzerrungen in der Aussage führen und werden nicht ausgewertet.",
        sections = listOf(
            Section(items = activity),
            Section(hint = "Die folgenden Aussagen beziehen sich darauf, wie sich Ihre normale Arbeit auf Ihren Rücken auswirkt oder auswirken könnte.", items = work),
        ),
        scores = listOf(
            lower("pa", "Körperliche Aktivität (FABQ – PA)", of(2, 3, 4, 5), listOf(Band.upTo(15, null), Band.upTo(24, "Höheres Chronifizierungsrisiko (> 15)"))),
            lower("w", "Arbeitsbelastung (FABQ – W)", of(6, 7, 9, 10, 11, 12, 15), listOf(Band.upTo(34, null), Band.upTo(42, "Erhöhtes Chronifizierungsrisiko (> 34)"))),
        ),
    )
}

/** Tampa Scale of Kinesiophobia, validated German version (TSK-GV, Rusu et al. 2014), as in "Tampa-Scale-of-Kinesiophobia-_09-2026.pdf". */
private val TSK: Assessment = run {
    val options = scored("A: überhaupt nicht einverstanden" to 1, "B: mehr oder weniger nicht einverstanden" to 2, "C: mehr oder weniger einverstanden" to 3, "D: völlig einverstanden" to 4)
    // The eleven of the TSK-GV, none of them reversed, as the PDF scores each A 1 to D 4.
    val items = listOf(
        "Ich habe Angst davor, dass ich mich möglicherweise verletze, wenn ich Sport treibe.",
        "Wenn ich versuchen würde, mich über die Schmerzen hinweg zu setzen, würden sie noch schlimmer.",
        "Mein Körper sagt mir, dass ich etwas sehr Schlimmes habe.",
        "Mein Gesundheitszustand wird von anderen nicht ernst genug genommen.",
        "Wegen des Schmerzproblems ist mein Körper für den Rest meines Lebens gefährdet.",
        "Schmerz bedeutet immer, dass ich mich verletzt habe.",
        "Die sicherste Art, zu verhindern, dass meine Schmerzen schlimmer werden, ist einfach darauf zu achten, dass ich keine unnötigen Bewegungen mache.",
        "Ich hätte nicht so viel Schmerzen, wenn nicht etwas Bedenkliches in meinem Körper vor sich ginge.",
        "Meine Schmerzen sagen mir, wann ich mit dem Training aufhören muss, um mich nicht zu verletzen.",
        "Ich kann nicht all die Dinge tun, die gesunde Menschen machen, da ich mich zu leicht verletzen könnte.",
        "Niemand sollte Sport treiben müssen, wenn er/sie Schmerzen hat.",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", options) }
    Assessment(
        id = "tsk",
        name = "Tampa Scale of Kinesiophobia",
        abbreviation = "TSK-GV",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "Tampa Scale of Kinesiophobia, Stand 09/2026",
        // "Versio" in the original, a slip mended.
        hint = "Validierte deutsche Version; TSK-GV, Rusu et al. 2014.\n\n" +
            "Mit den nachfolgenden Fragen möchten wir untersuchen, wie Sie selbst zu Ihren Schmerzen stehen.\n\n" +
            "Bitte geben Sie an, in welchem Maße Sie mit den vorgegebenen Aussagen einverstanden sind. Für die Durchführung " +
            "benötigen Sie ca. 5 Minuten. Bitte nehmen Sie sich die Zeit für die korrekte Beantwortung der Fragen. Sie können " +
            "für den weiteren Behandlungsverlauf sehr wichtig sein.\n\n" +
            "Für die Auswertung wird ca. 5 Minuten benötigt. Ein hohes Ergebnis steht für eine große Kinesiophobie. Es wird " +
            "empfohlen, das Gesamtergebnis zu verwenden. Einzelne Items sind nicht aussagekräftig.",
        sections = listOf(Section(items = items)),
        scores = listOf(lower("gesamt", "Gesamtergebnis", items)),
    )
}

/** Impact of Event Scale, revised German version, as in "Impact-of-Event-Scale_IES-R_09-2026.pdf". */
private val IES_R: Assessment = run {
    val options = scored("überhaupt nicht" to 0, "selten" to 1, "manchmal" to 3, "oft" to 5)
    val items = listOf(
        "Immer, wenn ich an das Ereignis erinnert wurde, kehrten die Gefühle wieder.",
        "Ich hatte Schwierigkeiten, nachts durchzuschlafen.",
        "Andere Dinge erinnerten mich immer wieder daran.",
        "Ich fühlte mich reizbar und ärgerlich.",
        "Ich versuchte, mich nicht aufzuregen, wenn ich daran dachte oder daran erinnert wurde.",
        "Auch ohne es zu beabsichtigen, musste ich daran denken.",
        "Es kam mir so vor, als ob es gar nicht geschehen wäre oder irgendwie unwirklich war.",
        "Ich versuchte, Erinnerungen daran aus dem Weg zu gehen.",
        "Bilder, die mit dem Ereignis zu tun hatten, kamen mir plötzlich in den Sinn.",
        "Ich war leicht reizbar und schreckhaft.",
        "Ich versuchte nicht daran zu denken.",
        "Ich merkte zwar, dass meine Gefühle durch das Ereignis noch sehr aufgewühlt waren, aber ich beschäftigte mich nicht mit ihnen.",
        "Die Gefühle, die das Ereignis in mir auslösten, waren ein bisschen wie abgestumpft.",
        "Ich stellte fest, dass ich handelte oder fühlte, als ob ich in die Zeit (des Ereignisses) zurückversetzt sei.",
        "Ich konnte nicht einschlafen.",
        "Es kam vor, dass die Gefühle, die mit dem Ereignis zusammenhingen, plötzlich für kurze Zeit viel heftiger wurden.",
        "Ich versuchte, es (das Ereignis) aus meiner Erinnerung zu streichen.",
        "Es fiel mir schwer, mich zu konzentrieren.",
        "Die Erinnerungen daran lösten bei mir körperliche Reaktionen aus wie Schwitzen, Atemnot, Schwindel oder Herzklopfen.",
        "Ich träumte davon.",
        "Ich empfand mich selbst als sehr vorsichtig, aufmerksam oder hellhörig.",
        "Ich versuchte, nicht darüber zu sprechen.",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", options) }
    fun of(vararg numbers: Int) = numbers.map { items[it - 1] }
    val intrusion = lower("intrusion", "Intrusion", of(1, 3, 6, 9, 14, 16, 20))
    val avoidance = lower("vermeidung", "Vermeidung", of(5, 7, 8, 11, 12, 13, 17, 22))
    val arousal = lower("hyperarousal", "Hyperarousal", of(2, 4, 10, 15, 18, 19, 21))
    Assessment(
        id = "ies-r",
        name = "Impact of Event Scale",
        abbreviation = "IES-R",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "IES-R, Stand 09/2026",
        hint = "Revidierte deutsche Version.\n\n" +
            "Geben Sie im Folgenden an, wie Sie in der vergangenen Woche zu diesem Ereignis gestanden haben, indem Sie für jede " +
            "der folgenden Reaktionen ankreuzen, wie häufig diese bei Ihnen aufgetreten ist.\n\n" +
            "Im Folgenden sind Aussagen aufgeführt, die Menschen nach belastenden Lebensereignissen formuliert haben. Bitte " +
            "beantworten Sie diese Fragen im Bezug auf ihr belastendes Erlebnis. Lesen Sie jede Aussage durch und kreuzen Sie " +
            "an, wie häufig sie innerhalb der letzten sieben Tage zutraf. Wenn ein solcher Vorfall während dieser Zeit nicht " +
            "aufgetreten ist, kreuzen Sie bitte die Spalte „überhaupt nicht“ an. Beantworten Sie bitte jede Frage.\n\n" +
            "Die Itemwerte werden traditioneller Weise wie folgt bewertet: überhaupt nicht = 0, selten = 1, manchmal = 3, oft = " +
            "5. Die Subskalenwerte entstehen durch Addition entsprechender Itemwerte: Intrusion 1, 3, 6, 9, 14, 16, 20; " +
            "Vermeidung 5, 7, 8, 11, 12, 13, 17, 22; Hyperarousal 2, 4, 10, 15, 18, 19, 21.\n\n" +
            "Verdachtsdiagnose auf PTB (nach Maercker und Schützwohl 1998), nach der Formel: X = (-0,02 x Intrusion) + " +
            "(0,07 x Vermeidung) + (0,15 x Übererregung) - 4,36. Wenn X > 0 --> Verdachtsdiagnose auf PTB.",
        sections = listOf(Section(items = listOf(TextItem("vorfall", "Nennen Sie bitte den (traumatischen) Vorfall:")) + items)),
        scores = listOf(
            intrusion, avoidance, arousal,
            // Exact in hundredths, as each weight is: X > 0 is from 0,01 on.
            Score(
                "x", "X (Verdachtsdiagnose auf PTB)", emptyList(), unit = "", direction = Direction.LowerIsBetter,
                sumOf = listOf(intrusion.id, avoidance.id, arousal.id), weights = listOf(-2, 7, 15), constant = -436,
                bands = listOf(Band.upTo(0, null), Band.upTo(4, "Verdachtsdiagnose auf PTB")),
            ),
        ),
    )
}

/** Injury-Psychological Readiness to Return to Sport Scale (Glazer 2009), as in "Injury-Psychological-Readiness-to-Return-to-Sport-Scale_I-PRRS-Scale_09-2026.pdf". */
private val I_PRRS: Assessment = run {
    // In steps of 10, as Glazer's original asks (09.11.2026).
    val items = listOf(
        "Meine allgemeine Zuversicht zu spielen liegt bei",
        "Meine Zuversicht, den Sport ohne Schmerzen auszuüben, liegt bei",
        "Meine Zuversicht, 100% Einsatz geben zu können, liegt bei",
        "Meine Zuversicht, dass das verletzte Körperteil den Belastungen/Anforderungen meiner Sportart standhält, liegt bei",
        "Meine Zuversicht, den Sport unter gegebenen Bedingungen auszuüben, liegt bei",
        "Meine Zuversicht, sich nicht auf die Verletzung zu konzentrieren, liegt bei",
    ).mapIndexed { i, text -> ScaleItem("${i + 1}", text, 0, 100, "wenig bis kein Selbstvertrauen", "höchstes Selbstvertrauen", step = 10) }
    Assessment(
        id = "i-prrs",
        name = "Injury-Psychological Readiness to Return to Sport Scale",
        abbreviation = "I-PRRS",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "I-PRRS-Scale, Stand 09/2026",
        // "valdierte" in the original, a slip mended.
        hint = "Bitte bewerten Sie Ihre Zuversicht in den Sport zurückzukehren auf einer Skala von 0-100. Eine Punktzahl von 0 " +
            "bedeutet wenig bis kein Selbstvertrauen, eine Punktzahl von 50 bedeutet gemäßigtes Selbstvertrauen und eine " +
            "Punktzahl von 100 bedeutet höchstes Selbstvertrauen (immer aktuellen Zeitpunkt bewerten).\n\n" +
            "Die I-PRRS-Scale wurde wie der ACL-RSI entwickelt, um den Einfluss von psychologischen Faktoren auf die Rückkehr " +
            "zum Sport zu beurteilen. Er ist einfach durchzuführen und wurde hinsichtlich der Validität und Reliabilität " +
            "überprüft (Slagers 2019, Glazer 2009). Er ist in verschiedene Sprachen übersetzt (z.B. Niederländisch, Spanisch " +
            "etc.). Bisher ist keine validierte deutsche Version vorhanden.\n\n" +
            "Zur Berechnung des Gesamtwertes müssen die 6 Werte addiert und dann durch 10 geteilt werden. 60 = äußerst " +
            "zuversichtlich, 40 = mäßig zuversichtlich, 20 = gering zuversichtlich, 0 = überhaupt nicht zuversichtlich.\n\n" +
            "Sportler sollten einen Wert von 50-60 erreichen. Liegt der errechnete Wert darunter, ist davon auszugehen, dass der " +
            "Athlet psychisch noch nicht für seine Sportart bereit ist.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            Score(
                "gesamt", "Gesamtwert (Summe/10)", ids(items), direction = Direction.HigherIsBetter, divisor = 10,
                bands = listOf(Band.upTo(49, "Psychisch noch nicht für seine Sportart bereit (unter 50)"), Band.upTo(60, null)),
            ),
        ),
    )
}

/** Patient Health Questionnaire 15, as in "Patient-Health-Questionnaire-15_PHQ-15_10-2026.pdf". */
private val PHQ_15: Assessment = run {
    val options = numbered("Nicht beeinträchtigt", "Wenig beeinträchtigt", "Stark beeinträchtigt")
    val items = listOf(
        "Bauchschmerzen",
        "Rückenschmerzen",
        "Schmerzen in Armen, Beinen oder Gelenken (Knie, Hüften usw.)",
        "Menstruationsschmerzen oder andere Probleme mit der Menstruation (nur Frauen)",
        "Schmerzen oder Probleme beim Geschlechtsverkehr",
        "Kopfschmerzen",
        "Schmerzen im Brustbereich",
        "Schwindel",
        "Ohnmachtsanfälle",
        "Herzklopfen oder Herzrasen",
        "Kurzatmigkeit",
        "Verstopfung, nervöser Darm oder Durchfall",
        "Übelkeit, Blähungen oder Verdauungsbeschwerden",
        "Schwierigkeiten, ein- oder durchzuschlafen oder vermehrter Schlaf",
        "Müdigkeit oder Gefühl, keine Energie zu haben",
    ).mapIndexed { i, text -> ('a' + i).let { letter -> ChoiceItem("$letter", "$letter) $text", options) } }
    Assessment(
        id = "phq-15",
        name = "Patient Health Questionnaire 15",
        abbreviation = "PHQ-15",
        categories = listOf(AssessmentCategory.PainPsychosocial, AssessmentCategory.JawCmd),
        source = "PHQ-15, Stand 10/2026",
        hint = "Gesundheitsfragebogen. Gemäß validierter deutscher Übersetzung: Asendorf A, Eberhard L, Universitätsklinikum " +
            "Heidelberg & Schierz O, Universitätsmedizin Leipzig. Version 12/2018. Copyright Pfizer Inc. Originalversion: " +
            "Spitzer, Kroenke & Williams.\n\n" +
            "Dieser Fragebogen ist ein wichtiges Hilfsmittel, um Ihnen die bestmögliche Behandlung zukommen zu lassen. Ihre " +
            "Antworten können Ihrem Arzt/Ärztin oder Therapeuten/in helfen, Ihre Beschwerden besser zu verstehen. Bitte " +
            "beantworten Sie jede Frage, so gut Sie können. Überspringen Sie Fragen bitte nur, wenn Sie dazu aufgefordert " +
            "werden.\n\n" +
            "Somatisierung: Summenbildung über Antwortpunkte der Fragen. Für den PHQ-15 wurde eine Sensitivität von 78 % und " +
            "eine Spezifität von 71 % für somatoforme Störungen gemäß DSM-IV festgestellt. Die interne Reliabilität beträgt " +
            "Cronbach α=0.80.\n\n" +
            "In der deutschen Validierungsstudie von Gräfe et al. wurden Summenwerte des PHQ-15 an allgemeinmedizinischen/" +
            "internistischen und psychosomatischen PatientInnen genauer untersucht. Diejenigen mit einer psychischen Störung " +
            "wiesen eine durchschnittliche somatische Symptomschwere von 9.8 (SD=5.4) auf, die PatientInnen ohne psychische " +
            "Störung hatten einen Durchschnittswert von 6.4 (SD = 3.9). Die Gruppen unterschieden sich hinsichtlich " +
            "Symptomschwere: Bei den psychosomatischen PatientInnen lag sie durchschnittlich bei 9.7 (SD = 5.5), bei den " +
            "medizinischen PatientInnen bei 7.1 (SD =4.4).",
        sections = listOf(
            Section(
                hint = "Wie stark fühlten Sie sich im Verlauf der letzten 4 Wochen durch die folgenden Beschwerden " +
                    "beeinträchtigt? (Bitte kreisen Sie die für Sie passende Antwort ein.)",
                items = items,
            ),
        ),
        scores = listOf(
            Score(
                "gesamt", "Punktzahl", ids(items), direction = Direction.LowerIsBetter,
                // Question d, for women only, may stay unanswered (09.11.2026).
                optional = listOf("d"),
                bands = listOf(
                    Band.upTo(4, "Normal bis minimale somatische Symptomstärke/Somatisierung"),
                    Band.upTo(9, "Milde somatische Symptomstärke/Somatisierung"),
                    Band.upTo(14, "Mittelgradig ausgeprägte Symptomstärke/Somatisierung"),
                    Band.upTo(30, "Schwer ausgeprägte Symptomstärke/Somatisierung"),
                ),
            ),
        ),
    )
}

/** Patient Health Questionnaire 4 (Löwe et al. 2010), as in "Patient-Health-Questionnaire-4_PHQ-4_10-2026.pdf". */
private val PHQ_4: Assessment = run {
    val options = numbered("Überhaupt nicht", "An einzelnen Tagen", "An mehr als der Hälfte der Tage", "Beinahe jeden Tag")
    val items = listOf(
        "Wenig Interesse oder Freude an Ihren Tätigkeiten",
        "Niedergeschlagenheit, Schwermut oder Hoffnungslosigkeit",
        "Nervosität, Ängstlichkeit oder Anspannung",
        "Nicht in der Lage sein, Sorgen zu stoppen oder zu kontrollieren.",
    ).mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", options) }
    val positive = listOf(Band.upTo(2, null), Band.upTo(6, "Positives Screeningergebnis (3 oder größer)"))
    Assessment(
        id = "phq-4",
        name = "Patient Health Questionnaire 4",
        abbreviation = "PHQ-4",
        categories = listOf(AssessmentCategory.PainPsychosocial, AssessmentCategory.JawCmd),
        source = "PHQ-4, Stand 10/2026",
        hint = "PHQ-4 Gesamtscore 0 bis 12, wobei der psychologische Disstress in folgende Kategorien eingeteilt wird: Keiner " +
            "0-2, Gering 3-5, Moderat 6-8, Stark 9-12. Depression Subskala = Summe der Items 1 und 2 (Score range, 0 bis 6); " +
            "Angst Subskala = Summe der Items 3 und 4 (Score range, 0 bis 6). Auf jeder Subskala entspricht ein Score von 3 " +
            "oder größer einem positiven Screeningergebnis.",
        sections = listOf(Section(hint = "Wie oft fühlten Sie sich im Verlauf der letzten 2 Wochen durch die folgenden Beschwerden beeinträchtigt?", items = items)),
        scores = listOf(
            lower("gesamt", "Summenwert", items, listOf(Band.upTo(2, "Keiner"), Band.upTo(5, "Gering"), Band.upTo(8, "Moderat"), Band.upTo(12, "Stark"))),
            lower("depression", "Depression Subskala", items.take(2), positive),
            lower("angst", "Angst Subskala", items.drop(2), positive),
        ),
    )
}

/** Short psychological screening questions (Vaegter, Handberg & Kent), as in "Psychologische-Screeningfragen_09-2026.pdf". */
private val SCREENING: Assessment = run {
    fun question(n: Int, text: String, least: String, most: String) = ScaleItem("$n", "Frage $n: $text", 0, 10, least, most)
    val agree = "stimme überhaupt nicht zu" to "stimme voll zu"
    val items = listOf(
        question(1, "Haben Sie Angst?", "überhaupt nicht", "ziemlich ängstlich"),
        question(2, "Körperliche Aktivität könnte mir schaden.", agree.first, agree.second),
        question(3, "Ich sollte keine körperlichen Aktivitäten durchführen, die meinen Schmerz verschlimmern könnten.", agree.first, agree.second),
        question(4, "Fühlen Sie sich gestresst?", "gar nicht gestresst", "sehr gestresst"),
        question(5, "Wenn ich den Schmerz verspüre, ist es schrecklich und ich fühle, dass es niemals besser wird.", "niemals", "immer"),
        question(6, "Wenn ich Schmerzen verspüre, fühle ich, dass ich es nicht ertragen kann.", "niemals", "immer"),
        // "hatte sie" as in the original.
        question(7, "Haben Sie sich im vergangenen Monat oft traurig oder deprimiert gefühlt oder hatte sie oft ein Gefühl der Hoffnungslosigkeit?", "nie", "die ganze Zeit"),
        question(8, "Haben Sie im letzten Monat unter Interessenlosigkeit oder Antriebslosigkeit gelitten?", "nie", "die ganze Zeit"),
    )
    /** A result of questions [numbers], summed (09.11.2026), positive from [cutoff] on. */
    fun screen(id: String, name: String, cutoff: Int, positive: String, negative: String, vararg numbers: Int) = Score(
        id, name, numbers.map { "$it" }, direction = Direction.LowerIsBetter,
        bands = listOf(
            Band.upTo(cutoff - 1, "Negativ: Wahrscheinlichkeit einer Problematik $negative"),
            Band.upTo(10 * numbers.size, "Positiv: Wahrscheinlichkeit einer Problematik $positive"),
        ),
    )
    Assessment(
        id = "psychologische-screeningfragen",
        name = "Psychologische Screeningfragen",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "Psychologische Screeningfragen, Stand 09/2026",
        hint = "Kurze psychologische Screeningfragen. Henrik Bjarke Vaegter, PhD, Gitte Handberg, MD, Peter Kent, PhD.\n\n" +
            "Kurze psychologische Screening-Fragen können hilfreich sein, um psychische Erkrankungen bei Patienten mit " +
            "chronischen Schmerzen auszuschließen.\n\n" +
            "Frage 1, Angst: 5 oder mehr Punkte, Sens (80,8%), Spez (72,2%). Frage 2 und 3, Angst vor Bewegung: 5 oder mehr " +
            "Punkte, Sens (75,7%), Spez (74,4%). Frage 4, Stress: 7 oder mehr Punkte, Sens (71,2%), Spez (71,6%). Frage 5 und 6, " +
            "Katastrophisierung: 7 oder mehr Punkte, Sens (90,7%), Spez (60,9%). Frage 7 und 8, Depressionen: 6 oder mehr " +
            "Punkte, Sens (77,2%), Spez (73,1%).",
        sections = listOf(Section(items = items)),
        scores = listOf(
            screen("angst", "Angst (Frage 1)", 5, "43,3 %", "6,5 %", 1),
            screen("bewegung", "Angst vor Bewegung (Frage 2 und 3)", 5, "87,4 %", "43,3 %", 2, 3),
            screen("stress", "Stress (Frage 4)", 7, "30,0 %", "6,7 %", 4),
            screen("katastrophisierung", "Katastrophisierung (Frage 5 und 6)", 7, "57,4 %", "8,1 %", 5, 6),
            screen("depressionen", "Depressionen (Frage 7 und 8)", 6, "46,5 %", "8,6 %", 7, 8),
        ),
    )
}

/** Ermittlung der Stressbelastung (Strobel 2015), as in "Ermittlung-der-Stressbelastung_09-2026.pdf". */
private val STRESS_LOAD: Assessment = run {
    /** Factor [id]'s [statements], each "Ist fast immer so" [most], "Ist oft so" [often], "Manchmal" [sometimes], "Stimmt nicht" 0. */
    fun factor(id: String, most: Int, often: Int, sometimes: Int, vararg statements: String): List<Item> {
        val options = scored("Ist fast immer so" to most, "Ist oft so" to often, "Manchmal" to sometimes, "Stimmt nicht" to 0)
        return statements.mapIndexed { i, text -> ChoiceItem("$id-${i + 1}", "${i + 1}. $text", options) }
    }
    val food = factor(
        "ernaehrung", 6, 3, 2,
        "Ich lasse mir beim Essen wenig Zeit (weniger als 20 Minuten).", "Ich schlinge das Essen runter.", "Ich esse zu viel/zu wenig.",
        "Ich esse unregelmäßig.", "Ich vergesse zu essen.", "Ich esse viel Süßigkeiten.", "Ich trinke weniger als 1 Liter am Tag.",
        "Ich trinke mehr als 3 Tassen Kaffee am Tag.", "Ich esse irgendwo zwischendurch etwas.",
        "Ich esse oft beim Schnellimbiss oder mache mir Fertiggerichte.", "Ich esse viel Fleisch- und Wurstprodukte.", "Ich esse gerne fettreich.",
        "Essen ist mir nicht so wichtig.", "Wenn ich zu viel gegessen habe, dann erbreche ich mich.", "Ich denke den ganzen Tag ans Essen.",
        "Ich stehe nachts auf und esse.", "Ich bin zu dick/zu dünn.", "Ich nehme Appetitzügler zu mir.", "Mein Magen rebelliert nach dem Essen.",
        "Ich habe eine ernährungsbedingte Krankheit.",
    )
    val habits = factor(
        "gewohnheiten", 12, 6, 3,
        "Ich rauche mehr als 5 Zigaretten am Tag.", "Ich sitze mehr als 1 Stunde am Tag vor dem Fernseher.",
        "Ich surfe in meiner Freizeit mehr als 1 Stunde am Tag im Internet.", "Ich bewege mich wenig.", "Ich trinke täglich Alkohol.",
        "Ich nehme Medikamente (Aufputschmittel, Beruhigungsmittel).", "Ich treibe keinen Sport.", "Ich entspanne mich nicht aktiv.",
        "Ich betreibe ein riskantes Hobby.", "Ich habe neben meiner Arbeit wenig Interessen/Hobbys.",
    )
    val work = factor(
        "arbeit", 4, 2, 1,
        "Ich habe Angst um meinen Arbeitsplatz.", "Mit meinem Chef/meiner Chefin kann ich nicht reden.",
        "Mein Chef/meine Chefin findet keine Anerkennung für meine Arbeit.", "Meine Leistung wird nicht ausreichend honoriert.",
        "Mein Chef/meine Chefin weiß nicht was ich leiste.", "Mein Chef/meine Chefin ist ein(e) Choleriker(in).", "Für Urlaub habe ich keine Zeit.",
        "Ich verstehe mich nicht mit meinen KollegInnen.", "Ich fühle mich gemobbt.", "Ich fühle mich mit meinem Aufgabengebiet überfordert.",
        "Die Arbeit wächst mir über den Kopf.", "Die Arbeit langweilt mich; ich fühle mich unterfordert.",
        "Meine Arbeitsstelle ist laut und/oder schmutzig.", "Ich bin unangenehmen Gerüchen (Gestank) ausgesetzt.",
        "Ich arbeite die meiste Zeit am Bildschirm.", "Ich bin gesundheitlichen Risiken ausgesetzt.", "Ich arbeite mehr als 50 Stunden in der Woche.",
        "Ich muss im Schichtdienst arbeiten.", "Mein Arbeitsplatz ist unbequem (eng, kein natürliches Tageslicht).", "Ich fühle mich oft unter Druck.",
        "Ich habe keinen/zuwenig Handlungsspielraum.", "Ich werde in meinem Engagement ausgebremst.",
        "Ich muss alles unter Kontrolle haben, sonst habe ich das Gefühl, dass alles aus dem Ruder läuft.",
        "Ich bekomme nicht die Informationen, die ich benötige.", "Ich fühle mich nicht ernst genommen.", "Für Pausen habe ich keine Zeit.",
        "Meine Tätigkeit macht mir keinen Spaß.", "Ich bin ständig in Zeitnot.", "Die Kommunikation untereinander ist schlecht.",
        "Weiterbildungsmaßnahmen sind für mich Stress.",
    )
    val everyday = factor(
        "alltag", 12, 6, 3,
        "Die Hausarbeit wächst mir über den Kopf.", "Ich muss die Hausarbeit alleine machen. Niemand hilft mir aus der Familie.",
        "Ich rege mich beim Autofahren auf.", "Einkäufe und Behördengänge stressen mich.", "Ich kann meine Termine nicht einhalten oder komme zu spät.",
        "Ich habe Streit mit dem Vermieter.", "Ich habe Streit mit Nachbarn.", "Ich bin lästigem Lärm (Gestank) ausgesetzt.",
        "Ich muss mich um private Dinge kümmern, für die ich keine Zeit aufwenden will.", "Ich lebe in unerträglichen Wohnverhältnissen.",
    )
    val money = factor(
        "finanzen", 12, 6, 3,
        "Ich muss jeden Euro 2-mal umdrehen.", "Das Geld reicht hinten und vorne nicht.", "Ich kann mit Geld nicht gut umgehen.",
        "Ich weiß nicht, wie ich mein Geld anlegen soll.", "Ich bin finanziell von meinem/meiner PartnerIn abhängig.",
        "Ich habe Angst im Alter nicht richtig abgesichert zu sein.", "Meine Partnerin/mein Partner gibt mehr Geld aus als wir haben.",
        "Es gibt oft Streit wegen des Geldes.", "Die Schulden erdrücken mich.", "Ich habe Angst vor einer Zwangsvollstreckung.",
    )
    val cognitive = factor(
        "kognition", 4, 2, 1,
        "Ich weiß nicht, wie ich meine Probleme lösen soll.", "Ich habe das Gefühl den Anforderungen nicht gerecht zu werden (Versagensängste).",
        "Ich bin unzufrieden mit meinem Leben.", "Ich habe Schmerzen.", "Ich möchte alles möglichst perfekt machen.", "Ich bin in Trauer.",
        "Ich kann meine Gedanken nicht bremsen.", "Ich brauche lange zum Einschlafen/ich habe Probleme beim Durchschlafen.",
        "Ich plage mich mit Neid (Wut, Hass).", "Ich habe keinen Antrieb.", "Nur wenn ich alles selbst mache, kann ich sicher sein, dass es erledigt ist.",
        "Ich fühle mich nicht geliebt.", "Ich habe Angst Fehler zu machen.", "Mir gehen andere auf die Nerven.", "Ich finde mich nicht attraktiv.",
        "Ich denke an das Schlimmste.", "Ich will besser sein als andere.", "Ich komme nur schlecht damit klar, wenn jemand mehr Erfolg hat als ich.",
        "Ich möchte am liebsten alles sofort erledigt haben.", "Ich habe Angst vor/in Prüfungssituationen.",
        "Ich habe Angst an einer schlimmen Krankheit zu leiden.", "Ich will es allen recht machen.", "Ich kann mich nicht wehren.",
        "Ich raste schnell aus.", "Kritik verletzt mich.", "Ich kann mit Problemen nicht umgehen.", "Ich habe das Gefühl zu versagen.",
        "Ich habe Probleme mit meinem Selbstbewusstsein.", "Ich mache alles falsch.", "Andere machen die Dinge besser als ich.",
    )
    val family = factor(
        "familie", 6, 3, 2,
        "Ich pflege keine Freundschaften/soziale Kontakte.", "Unser Kind/unsere Kinder macht/en Probleme.", "Ich habe Streit mit meinen Geschwistern.",
        "Was die Familie betrifft, muss ich mich um alles kümmern.", "Ich muss jemanden aus der Familie pflegen.",
        "Meine Eltern/Schwiegereltern mischen sich in unsere Ehe/Kindererziehung ein.", "Ich habe zu wenig Zeit für meine Familie/Freunde.",
        "Ich bin alleinerziehend.", "Ich habe Streit mit meinen Eltern/Schwiegereltern.", "Meine Familie ist verstritten.",
        "Meine Freunde verstehen mich nicht.", "In Erziehungsfragen sind mein(e) Partner/in und ich uns nicht einig.",
        "Für meine Familie bin ich nur der Ernährer/die Ernährerin.", "Ich habe keine Zeit/keinen Freiraum für mich.",
        "Ich bin immer für die gesamte Familie da, wenn sie mich braucht/meine Interessen stehen hinten an.",
        "Ich werde von der Familie meines Partners/meiner Partnerin nicht akzeptiert (oder nur geduldet).",
        "Ich bin die (der) Putzfrau(mann) der Familie.", "Ich bin in meiner Familie nicht sonderlich beliebt.",
        "Ich lasse mir von Familienmitgliedern zu viel gefallen.", "Ich lasse mir von anderen zu viel gefallen.",
    )
    val partnership = factor(
        "partnerschaft", 4, 2, 1,
        "Ich kann meinem Partner/meiner Partnerin nichts recht machen.", "Ich fühle mich von meinem Partner/meiner Partnerin nicht geliebt.",
        "Mein Partner/meine Partnerin macht viel im Alleingang.", "Ich fühle mich von meinem Partner/meiner Partnerin nicht verstanden.",
        "Ich fühle mich von meinem Partner/meiner Partnerin nicht wahrgenommen.", "Meine Partnerin/mein Partner behandelt mich respektlos.",
        // "Partnerin/mein Partner ist" in the original, the "Meine" lost.
        "Meine Partnerin/mein Partner ist für andere da, aber nicht für mich.", "Ich kann mit meinem Partner/meiner Partnerin nicht reden.",
        "Wir streiten uns oft wegen Kleinigkeiten.", "Ich liebe meinen Partner/meine Partnerin, aber er/sie liebt mich nicht mehr.",
        "Ich liebe meinen Partner/meine Partnerin nicht mehr.", "Ich bin in meiner Partnerschaft unzufrieden, weiß aber nicht warum.",
        "Unsere Beziehung ist leblos und langweilig.", "Meine Partnerin/mein Partner hängt ständig an mir dran und macht nie etwas alleine.",
        "Meine Partnerin/mein Partner betrügt mich.", "Ich wäre gerne wieder Single.", "Ich habe kein Vertrauen zu meinem Partner/meiner Partnerin.",
        "Ich habe keinen Respekt vor meinem Partner/meiner Partnerin.", "Ich bin sehr eifersüchtig.", "Meine Partnerin/mein Partner ist sehr eifersüchtig.",
        "Ich werde von meinem Partner/meiner Partnerin geschlagen.", "Meine Partnerin/mein Partner ist süchtig (Alkohol, Drogen, Spielsucht etc.).",
        "Ich bin von meinem Partner/meiner Partnerin abhängig.", "Wir streiten wegen der Kinder.", "Meine Partnerin/mein Partner erfüllt nicht meine Erwartungen.",
        "Ich schlage meinen Partner/meine Partnerin, obwohl ich das nicht will.", "Wir haben zu wenig (überhaupt keinen, schlechten) Sex.",
        // "mein Partnerin" in the original, a slip mended.
        "Ich bin meinem Partner/meiner Partnerin untreu.", "Meine Partnerin/mein Partner ist nicht zärtlich zu mir.", "Meine Partnerin/mein Partner misstraut mir.",
    )
    Assessment(
        id = "stressbelastung",
        name = "Ermittlung der Stressbelastung",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "Ermittlung der Stressbelastung, Stand 09/2026",
        hint = "Strobel I. Stressbewältigung und Burnoutprävention. Stuttgart: Haug; 2015.\n\n" +
            "Je Stressfaktor eine Summe, bis 120 Punkte; im Original als Netz der acht Stressfaktoren ausgewertet.",
        sections = listOf(
            Section("Persönliche Stressoren – Stressfaktor: Ernährung", items = food),
            Section("Persönliche Stressoren – Stressfaktor: Schädliche Gewohnheiten", items = habits),
            Section("Arbeits- und Alltags-Stressoren – Stressfaktor: Arbeit", items = work),
            Section("Arbeits- und Alltags-Stressoren – Stressfaktor: Alltag", items = everyday),
            Section("Arbeits- und Alltags-Stressoren – Stressfaktor: Finanzen", items = money),
            Section("Kognitive Stressoren – Kognitive Stressfaktoren", items = cognitive),
            Section("Soziale Stressoren – Stressfaktor: Familie/Freunde", items = family),
            Section("Soziale Stressoren – Stressfaktor: Partnerschaft", items = partnership),
        ),
        scores = listOf(
            lower("ernaehrung", "Ernährung", food),
            lower("gewohnheiten", "Schädliche Gewohnheiten", habits),
            lower("arbeit", "Arbeit", work),
            lower("alltag", "Alltag", everyday),
            lower("finanzen", "Finanzen", money),
            lower("kognition", "Persönliche Stressfaktoren (Kognition)", cognitive),
            lower("familie", "Familie", family),
            lower("partnerschaft", "Partnerschaft", partnership),
        ),
    )
}

/** Stress- und Spannungsfragebogen, as in "Stress-und-Spannungsfragebogen_09-2026.pdf": two questionnaires, each its own result. */
private val STRESS_TENSION: Assessment = run {
    val often = scored("häufig" to 2, "manchmal" to 1, "nie" to 0)
    fun rated(id: String, text: String) = ChoiceItem(id, text, often)
    val stress = listOf(
        "Ich bin schnell ungeduldig.", "KollegenInnen bezeichnen mich als ehrgeizig.", "Ich versuche mehrere Dinge zur gleichen Zeit zu tun.",
        "Ich muss Anderen mein Können beweisen.", "Ich bin leicht erregbar.", "Ich fühle mich leicht in die Enge getrieben.",
        "Ich kann schwer „nein“ sagen.", "Nach der Arbeit kann ich keine Ruhe finden.", "Freunde sagen mir, dass ich zuviel arbeite.",
        "Mein Lebensstandard ist mir wichtig.", "Es ist mir wichtig, was Andere von mir denken.", "Vorwürfe verkrafte ich schwer.",
        "In Gegenwart eines Vorgesetzten bin ich unsicher.", "Ich habe Angst Fehler zu machen.", "Ich arbeite im Wechsel- oder Nachtdienst.",
        "Ich bin geräuschempfindlich.", "Mein Tatendrang ist schwer zu bremsen.", "Ich fühle mich überlastet.", "Ich fühle mich wie ausgelaugt.",
        "Mein Lebensrhythmus ist gestört.", "Über meine Gesundheit mache ich mir Sorgen.", "Es fällt mir schwer Ruhe zu genießen.",
        "Familiäre Schwierigkeiten belasten mich.", "Trennungserlebnisse belasten mich.", "Folgende weitere Stresssituationen belasten mich:",
    ).mapIndexed { i, text -> rated("s${i + 1}", "${i + 1}. $text") }
    val tension = listOf(
        "Innere Unruhe", "Reizbarkeit/Nervosität", "Einschlafschwierigkeiten", "Durchschlafprobleme", "Starkes Schlafbedürfnis",
        "Mattigkeit/Schwächegefühl", "Abgespanntheit", "Muskelverspannungen", "Kreuz-/Rückenschmerzen", "Zittrigkeit",
        "Konzentrationsprobleme", "Aufsteigende Hitze", "Schweißausbrüche", "Schwindelgefühle", "Kopfschmerzen", "Echte Migräne",
        "Berufliche Sorgen", "Private Sorge", "Seelische Konflikte", "Angstgefühle", "Schwere Träume", "Krampfhaftes Weinen",
        "Beklemmungsgefühle", "Würgegefühl im Hals", "Übelkeit", "Herzschmerzen", "Herzjagen/Herzklopfen", "Kreislaufbeschwerden",
        "Kalte Hände und Füße", "Magenbeschwerden", "Verdauungsbeschwerden", "Atembeschwerden", "Grübeln über Krankheiten",
    ).mapIndexed { i, text -> rated("t${i + 1}", "${i + 1}. $text") }
    // Question 34 in words for each setting, and its reaction to choose; 35 as how many problems burden how much (09.11.2026).
    val reaction = scored("a) besonnene/überlegte Reaktion" to 0, "b) erregte/hektische Reaktion" to 2, "c) sich zurückziehende/aufstauende Reaktion" to 4)
    val conflict = listOf(
        TextItem("t34-betrieb", "Im Betrieb/Amt:"),
        ChoiceItem("t34-betrieb-reaktion", "Reaktion im Betrieb/Amt", reaction),
        TextItem("t34-zuhause", "Zu Hause:"),
        ChoiceItem("t34-zuhause-reaktion", "Reaktion zu Hause", reaction),
    )
    val problems = listOf(
        TextItem("t35", "35. Welche Probleme belasteten Sie in letzter Zeit?"),
        NumberItem("t35-etwas", "Anzahl der Probleme: a) etwas belastend (je 1 Punkt)", max = 10, each = 1),
        NumberItem("t35-belastend", "Anzahl der Probleme: b) belastend (je 2 Punkte)", max = 10, each = 2),
        NumberItem("t35-stark", "Anzahl der Probleme: c) stark belastend (je 3 Punkte)", max = 10, each = 3),
    )
    Assessment(
        id = "stress-spannung",
        name = "Stress- und Spannungsfragebogen",
        categories = listOf(AssessmentCategory.PainPsychosocial),
        source = "Stress- und Spannungsfragebogen, Stand 09/2026",
        hint = "Die beiden folgenden Fragebögen sind einfach gestaltet und sollen grob Ihre Stress- und Spannungsbelastungen " +
            "widerspiegeln. Bitte beantworten Sie die Fragen immer in Bezug auf Ihre momentane Befindlichkeit bzw. " +
            "Lebenssituation (letzten Tage).\n\n" +
            "Auswertung Stressfragebogen: Grobe Einschätzung Ihrer momentanen Lebenssituation. Addieren Sie die angekreuzten " +
            "Zahlen.\n\n" +
            "Auswertung Spannungsfragebogen: 1.) Addieren Sie die angekreuzten Zahlen zu den Fragen 1 – 33. 2.) Bewerten Sie " +
            "Ihr Verhalten in Konfliktsituationen bzw. bei Auseinandersetzungen nach folgendem Schema (Frage 34): a) " +
            "besonnene/überlegte Reaktion je 0 Punkte, b) erregte/hektische Reaktion je 2 Punkte, c) sich zurückziehende/" +
            "aufstauende Reaktion je 4 Punkte. 3.) Bewerten Sie jedes Problem je nach Belastungsstärke (Frage 35): a) etwas " +
            "belastend je 1 Punkt, b) belastend je 2 Punkte, c) stark belastend je 3 Punkte.",
        sections = listOf(
            Section("Stressfragebogen", "Kreuzen Sie die für Sie zutreffenden Felder an.", stress + TextItem("s25-text", "Weitere Stresssituationen")),
            Section(
                "Spannungsfragebogen",
                "Im Folgenden finden Sie eine Liste von Leiden und Beschwerden. Kreuzen Sie jeweils die zutreffenden Felder an bzw. " +
                    "beantworten Sie die Fragen. Auch hier bezugnehmend auf Ihre momentane Befindlichkeit (letzten Tage).",
                tension,
            ),
            Section("34. Wie verhalten Sie sich in Konfliktsituationen bzw. bei Auseinandersetzungen?", items = conflict),
            Section(items = problems),
        ),
        scores = listOf(
            lower(
                "stress", "Stressfragebogen", stress,
                listOf(
                    Band.upTo(
                        9,
                        "Mit Stresssituationen, die relativ selten bei Ihnen vorkommen, können Sie gut umgehen. Mit Ihren Mitmenschen " +
                            "und mit sich selbst haben Sie zufriedenstellende Arrangements getroffen. Sie sind flexibel und können sich " +
                            "auf Ihre Mitmenschen und die Anforderungen des Alltags gut einstellen.",
                    ),
                    Band.upTo(
                        19,
                        "Sie sind stressgefährdet. Sie stehen zwar nicht unter Dauerstress, haben aber immer wieder Stressmomente, " +
                            "wodurch Ihre notwendigen Entlastungs- und Entspannungszeiten zu kurz kommen. Sie sind jedoch genügend " +
                            "flexibel, um Arrangements mit sich selbst und Ihren Mitmenschen zu treffen und der Entspannung etwas mehr " +
                            "Raum in Ihrem Leben zu geben.",
                    ),
                    Band.upTo(
                        29,
                        "Ihre Stressmomente in den verschiedenen Lebensbereichen sind bereits so ausgeprägt, dass " +
                            "Gesundheitsstörungen psychischer oder körperlicher Art aufgetreten sind. Ein Entspannungstraining kann " +
                            "Ihnen helfen, einen Teil der Spannungen wieder abzubauen. Davon unabhängig sollten Sie den Ursachen Ihres " +
                            "Stresses nachgehen, Änderungsansätze überlegen und in die Tat umsetzen. Auch hier kann Ihnen ein " +
                            "Entspannungstraining nützlich sein, weil in relativer Gelassenheit die in den Aussagen des " +
                            "Stressfragebogens angesprochenen Themen leichter angegangen werden können.",
                    ),
                    Band.upTo(
                        50,
                        "Sie leben in dauerndem Stress. Ihr Organismus befindet sich in einer ständigen Alarmsituation. Akute oder " +
                            "chronische Gesundheitsstörungen machen Ihnen zu schaffen. Zu viele Ihrer Lebensbereiche sind mit " +
                            "emotionalen Belastungen verbunden, sodass zumindest emotionale Entlastung dringend geboten ist. Sie sind " +
                            "wahrscheinlich schon so tief im sich selbstverstärkenden Spiralprozess des Stresses, dass " +
                            "psychotherapeutische Hilfe zur Auflösung des Stressstrudels anzuraten ist. Ein gezieltes " +
                            "Entspannungstraining kann Ihnen eine Linderung und wichtige Hilfe bei der Aufarbeitung der " +
                            "problematischen Themen bringen.",
                    ),
                ),
            ),
            Score(
                "spannung", "Spannungsfragebogen", ids((tension + conflict + problems).filter(Item::scorable)),
                direction = Direction.LowerIsBetter,
                bands = listOf(
                    Band.upTo(
                        14,
                        "Sie können sich über Ihre relativ gute psychovegetative Stabilität freuen. Ein Tiefenmuskelentspannungstraining " +
                            "(z.B. Progressive Muskelrelaxation) wird bei Ihnen vor allem vorbeugende Wirkung zeigen. Sie können damit " +
                            "rechnen, dass sich die von Ihnen angegebenen Beschwerden bessern oder ganz beseitigen lassen. Die Einübung " +
                            "von bestimmten Entspannungstechniken ist natürlich Voraussetzung.",
                    ),
                    Band.upTo(
                        29,
                        "Die Kettenreaktionen der psychovegetativen bzw. psychosomatischen Störungen finden bei Ihnen bereits statt. " +
                            "Sie sollten möglichst bald mit Entspannungsmaßnahmen beginnen. Nach etwa 4 – 6 Wochen konsequenten " +
                            "Trainings, verbunden mit gezielter Problembearbeitung, können Sie eine wesentliche Besserung bzw. " +
                            "Beseitigung der Beschwerden erreichen.",
                    ),
                    Band.upTo(
                        134,
                        "Sie stecken bereits tief im Kreisprozess der Verspannungen und emotionalen Belastungen. Sie sollten sich " +
                            "besonders aufmerksam und intensiv mit Entspannung sowie mit Problem- und Konfliktlösungen beschäftigen. " +
                            "Daneben sollten Sie konsequent Entspannungsübungen durchführen. Auch Sie können eine Linderung bzw. die " +
                            "Beseitigung mancher Beschwerden erreichen.",
                    ),
                ),
            ),
        ),
    )
}
