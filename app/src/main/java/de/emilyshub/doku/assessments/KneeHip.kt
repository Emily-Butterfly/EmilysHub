package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/**
 * The forms of DIGOTOR's subcategory "Untere Extremität", first part: knee, hip and return to sport (#68), each as in its
 * PDF of the date in its source. The "Checkliste ACL-Prävention" is left out: it rates a team's training programme, not
 * the patient (08.11.2026).
 */
internal val KNEE_HIP: List<Assessment> by lazy {
    listOf(ACL_RSI, HARRIS, HOOS, HOS, ICRS, KOOS, KOOS_CHILD, KOS_ADL, KUJALA, LESS, LYSHOLM, TEGNER, VISA_P_G)
}

/** Options with the [points] given, in the order of the original, their ids numbered from 0. */
private fun scored(vararg options: Pair<String, Int>): List<Option> =
    options.mapIndexed { i, (label, points) -> Option("$i", label, points) }

/** Five answers scored 0 for the first to 4 for the last, as the KOOS scores them: 0 for few symptoms. */
private fun zeroToFour(vararg labels: String): List<Option> = labels.mapIndexed { i, label -> Option("$i", label, i) }

/** An item of the KOOS or KOOS-Kind: scored as the original, 0 for few symptoms, and counted the other way round. */
private fun koosItem(id: String, text: String, options: List<Option>) = ChoiceItem(id, text, options, reversed = true)

/** Five answers scored 4 for the first to 0 for the last, as the HOOS scores them: 4 for few symptoms. */
private fun fourToZero(vararg labels: String): List<Option> = labels.mapIndexed { i, label -> Option("$i", label, labels.size - 1 - i) }

/** Each of [items]' ids. */
private fun ids(items: List<Item>): List<String> = items.map(Item::id)

/** Half of [n] items, rounded up: the HOOS's least to answer. */
private fun half(n: Int): Int = (n + 1) / 2

/** Anterior Cruciate Ligament – Return to Sport after Injury, as in "…_ACL-RSI-Skala_11-2026.pdf". */
private val ACL_RSI: Assessment = run {
    val questions = listOf(
        Triple("Sind Sie zuversichtlich, dass Sie den Sport auf demselben Niveau wie vorher ausüben können?", "Überhaupt nicht zuversichtlich", "völlig zuversichtlich"),
        Triple("Halten Sie es für wahrscheinlich, Ihr Knie durch die Teilnahme an Ihrem Sport wieder verletzen zu können?", "Sehr wahrscheinlich", "völlig unwahrscheinlich"),
        Triple("Macht Sie der Gedanke an die Ausübung Ihres Sports nervös?", "Sehr nervös", "gar nicht nervös"),
        Triple("Sind Sie zuversichtlich, dass Ihr Knie bei der Ausübung Ihres Sports nicht nachgeben wird?", "Überhaupt nicht zuversichtlich", "völlig zuversichtlich"),
        Triple("Sind Sie zuversichtlich, dass Sie Ihren Sport ausüben können, ohne Bedenken wegen Ihres Knies zu haben?", "Überhaupt nicht zuversichtlich", "völlig zuversichtlich"),
        Triple("Finden Sie es frustrierend, dass Sie in Bezug auf Ihren Sport Ihr Knie berücksichtigen müssen?", "Sehr frustrierend", "Überhaupt nicht frustrierend"),
        Triple("Befürchten Sie, dass Sie Ihr Knie bei der Ausübung Ihres Sports wieder verletzen könnten?", "Habe große Sorge", "Habe überhaupt keine Sorge"),
        Triple("Sind Sie zuversichtlich, dass Ihr Knie unter Belastung standhält?", "Überhaupt nicht zuversichtlich", "völlig zuversichtlich"),
        Triple("Haben Sie Angst, dass Sie Ihr Knie bei der Ausübung Ihres Sports versehentlich wieder verletzen könnten?", "Habe große Angst", "Habe überhaupt keine Angst"),
        Triple("Hält Sie der Gedanke daran, nochmals operiert und nachbehandelt werden zu müssen, davon ab, Ihren Sport auszuüben?", "Die ganze Zeit", "Zu keinem Zeitpunkt"),
        Triple("Sind Sie zuversichtlich, Ihren Sport gut ausüben zu können?", "Überhaupt nicht zuversichtlich", "völlig zuversichtlich"),
        Triple("Sind Sie gelassen, wenn es um die Ausübung Ihres Sports geht?", "Gar nicht gelassen", "völlig gelassen"),
    )
    val items = questions.mapIndexed { i, (text, least, most) ->
        ScaleItem("${i + 1}", "${i + 1}. $text", min = 0, max = 100, minLabel = least, maxLabel = most, step = 10)
    }
    Assessment(
        id = "acl-rsi",
        name = "Anterior Cruciate Ligament - Return to Sport after Injury",
        abbreviation = "ACL-RSI",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "ACL-RSI-Skala, Stand 11/2026",
        hint = "Validierte deutsche Version, Müller et al. 2014b.\n\nSehr geehrte/-r Patient/-in, der folgende Fragebogen dient " +
            "der Erfassung von Beschwerden und Problemen, die durch Ihr Kniegelenk verursacht werden. Bitte beantworten Sie " +
            "folgende Fragen zu der Sportart, die Sie als Hauptsportart vor Ihrer Verletzung ausgeübt haben. Kreuzen Sie für " +
            "jede Frage dasjenige Kästchen zwischen den beiden beschriebenen Extremen an, das Ihr derzeitiges Befinden " +
            "wiedergibt.\n\n" +
            "Die Werte jeder Frage werden addiert und durch die Anzahl der Fragen geteilt. Der Wert kann sich durch Training " +
            "verändern und kann daher in der Verlaufskontrolle verwendet werden. Ein Zusammenhang zu funktionellen Werten " +
            "kann, muss aber nicht bestehen. Patienten, die in physischen Messverfahren gute bis sehr gute Werte erreichen, " +
            "können daher zusätzlich mit Informationen oder verhaltenstherapeutischen Ansätzen behandelt werden. Zusammen mit " +
            "dem Einbeinsprungtest können Patienten, die nicht in den Sport zurückkehren erkannt werden (Prädiktiv, Müller " +
            "2014a). Der Fragebogen hat eine prognostische Aussagekraft, d.h. Patienten mit einem höheren Wert vor einem " +
            "Trainingsprogramm haben nach dem Training eine bessere Kniefunktion (White et al. 2014).",
        sections = listOf(Section(items = items)),
        scores = listOf(
            Score(
                id = "wert",
                name = "Wert",
                items = ids(items),
                rule = ScoreRule.Mean,
                unit = "%",
                direction = Direction.HigherIsBetter,
                bands = listOf(
                    // "Unter 51 %": up to 50,99, as means of twelve tens fall between.
                    Band(5099, "Personen, die ihrem Kniegelenk noch kein Vertrauen schenken und eine signifikante Angst vor einer Retraumatisierung haben. Diese Patienten kehren seltener in den Sport zurück."),
                    Band.upTo(100, null),
                ),
            ),
        ),
    )
}

/** Harris Hip Score, German version, as in "Harris-Hip-Score_11-2026.pdf": questions to the patient and findings of the examiner. */
private val HARRIS: Assessment = run {
    val pain = ChoiceItem(
        "schmerzen", "Schmerzen",
        scored(
            "kein Schmerz" to 44,
            "leichter Schmerz: gelegentliche Beschwerden oder geringgradige Schmerzwahrnehmung, die Aktivität ist nicht behindert." to 40,
            "geringe Schmerzen: keine Auswirkung auf die durchschnittliche Aktivität, selten mäßige Schmerzen nach ungewohnten Tätigkeiten, gelegentlich z.B. Aspirin." to 30,
            "mäßige Schmerzen: Schmerz erträglich, regelmäßige Arbeit möglich, jedoch Behinderung bei gewöhnlicher Aktivität, gelegentlich stärkere Analgetika erforderlich" to 20,
            "deutliche Schmerzen: starke gelegentlich auftretende und wieder vergehende Schmerzen, ernsthafte Einschränkung des Aktivitätsniveaus, stärkere Schmerzmittel" to 10,
            "schwere Schmerzen: starker Schmerz auch im Bett, der Schmerz zwingt den Patienten überwiegend im Bett zu bleiben, schwerste Beeinträchtigung" to 0,
        ),
    )
    val adl = listOf(
        ChoiceItem(
            "treppen", "Treppen steigen",
            scored(
                "eine Stufe nach der anderen ohne Nachziehen des Beines und ohne den Gebrauch eines Geländers" to 4,
                "Stufe für Stufe ohne Nachziehen eines Beines, aber mit Geländer" to 2,
                "Treppengehen ist noch möglich mit beliebigen Hilfsmitteln." to 1,
                "Patient ist nicht in der Lage, Treppen zu steigen." to 0,
            ),
        ),
        // The original gives a point for being able, and none else.
        YesNoItem("verkehrsmittel", "Öffentliche Verkehrsmittel: Patient ist in der Lage, öffentliche Verkehrsmittel zu benutzen."),
        ChoiceItem(
            "sitzen", "Sitzen",
            scored(
                "Patient kann bequem auf jedem Stuhl für 1 Stunde sitzen." to 5,
                "Patient kann bequem auf einem hohen Stuhl für eine halbe Stunde sitzen." to 3,
                "Patient ist nicht in der Lage, auf irgendeinem Stuhl bequem zu sitzen." to 0,
            ),
        ),
        ChoiceItem(
            "anziehen", "Schuhe und Strümpfe anziehen",
            scored(
                "Patient kann ohne weiteres Strümpfe anziehen und Schuhe binden." to 4,
                "Patient kann unter Schwierigkeiten Strümpfe anziehen und Schuhe binden." to 2,
                "Patient ist nicht in der Lage, Schuhe oder Strümpfe anzuziehen." to 0,
            ),
        ),
    )
    val gait = listOf(
        ChoiceItem("hinken", "Hinken", scored("kein Hinken" to 11, "leichtes Hinken" to 8, "mäßiges Hinken" to 5, "schweres Hinken" to 0)),
        ChoiceItem(
            "gehhilfen", "Gehhilfen",
            scored(
                "keine" to 11,
                "einzelner Stock für längere Strecken" to 7,
                "einzelner Stock für die meiste Zeit" to 5,
                "eine Unterarmgehstütze" to 3,
                "zwei Stöcke" to 2,
                "zwei Unterarmgehstützen oder Gehunfähigkeit" to 0,
            ),
        ),
        ChoiceItem(
            "entfernungen", "Entfernungen",
            scored("unbegrenzt" to 11, "zwei Kilometer" to 8, "200 bis 500 Meter" to 5, "nur in der Wohnung" to 2, "Bett oder Stuhl" to 0),
        ),
    )
    val deformities = listOf(
        ChoiceItem("adduktion", "Adduktionskontraktur", scored("weniger als 10 Grad" to 1, "10 Grad oder mehr" to 0)),
        ChoiceItem("innenrotation", "Innenrotationskontraktur", scored("weniger als 10 Grad" to 1, "10 Grad oder mehr" to 0)),
        ChoiceItem("beugung", "Beugekontraktur", scored("weniger als 15 Grad" to 1, "15 Grad oder mehr" to 0)),
        ChoiceItem("beinlaenge", "Beinlängendifferenz", scored("weniger als 3 cm" to 1, "3 cm oder mehr" to 0)),
    )
    val mobility = ChoiceItem(
        "mobilitaet", "Mobilität",
        scored("300-210°" to 5, "209-160°" to 4, "159-100" to 3, "99-60°" to 2, "59-30°" to 1, "29-0°" to 0),
    )
    val all = listOf(pain) + adl + gait + deformities + mobility
    Assessment(
        id = "harris-hip",
        name = "Harris Hip Score",
        abbreviation = "HHS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "Harris Hip Score, Stand 11/2026",
        hint = "Deutsche Version.\n\nDer folgende Fragebogen dient der Erfassung von Beschwerden und Problemen bei alltäglichen " +
            "Aktivitäten, die durch Ihr Hüftgelenk verursacht werden. Bitte beantworten Sie alle Fragen gemäß Ihrem aktuellen " +
            "Zustand. Sollten Sie momentan keine Beschwerden haben, dann bewerten Sie die Fragen entsprechend Ihrem Zustand in " +
            "der vergangenen Woche.\n\n" +
            "Der Harris Hip Score (HHS) ist ein Fragebogen, der bei annähernd allen Hüftpathologien unabhängig vom " +
            "Leistungsniveau verwendet werden kann. Er evaluiert hauptsächlich Beschwerden im alltäglichen Leben (ca. 90%). " +
            "Des Weiteren werden Symptome aus dem Bereich Körperfunktion (z.B. Gelenkmobilität und Deformität ca. 10%) " +
            "abgefragt. Es ist allerdings zu bedenken, dass jüngere und insbesondere sportlich sehr ambitionierte Patienten " +
            "nicht voll evaluiert werden können. Dies ist dadurch begründet, dass sehr basale Aktivitäten abgeprüft werden " +
            "und dementsprechend ADL´s, welche größere Kraftleistungen beinhalten, weniger Beachtung finden. Hierfür müssten " +
            "andere Assessmenttools hinzugefügt werden.\n\n" +
            "Der HHS kann nicht vom Patienten alleine ausgefüllt werden. Die Anteile Mobilität und Deformität werden vom " +
            "Behandler gemessen und hinzugefügt. Die Unterpunkte Schmerz, ADL und Gehfähigkeit dagegen können vom Patient " +
            "selbst evaluiert werden.\n\n" +
            "Wird der Fragebogen nach einer Endoprothesenversorgung eingesetzt, so wird eine postoperative Verbesserung von 20 " +
            "Punkten mit einem stabilen Implantat ohne weitere Eingriffe mit einem guten bis sehr guten Ergebnis gleichgesetzt. " +
            "Die minimal klinisch relevante Veränderung wird zwischen 10%-20% angegeben.\n\n" +
            "Da dieser Fragebogen nicht selten auch telefonisch eingesetzt wird, kommt manchmal der modifizierte HHS zum " +
            "Einsatz. Dabei werden die vom Therapeut erhobenen Daten (Beweglichkeit und Deformität) entfernt. Die maximale " +
            "Punktzahl kann so auf 95 bzw. 91 sinken. Die Bewertung erfolgt dann ebenfalls in 10 Schritten ausgehend von einer " +
            "geringeren Gesamtpunktzahl (siehe oben).",
        sections = listOf(
            Section("Schmerzen", items = listOf(pain)),
            Section("ADL", items = adl),
            Section("Gehfähigkeit", items = gait),
            Section("Deformitäten", items = deformities),
            Section("Mobilität (in allen Ebenen)", items = listOf(mobility)),
        ),
        scores = listOf(
            Score(
                id = "gesamt",
                name = "Gesamtergebnis",
                items = ids(all),
                direction = Direction.HigherIsBetter,
                bands = listOf(
                    Band.upTo(69, "Schlechtes Ergebnis (oder Funktionsstatus)"),
                    Band.upTo(79, "Durchschnittliches Ergebnis (oder Funktionsstatus)"),
                    Band.upTo(89, "Gutes Ergebnis (oder Funktionsstatus)"),
                    Band.upTo(100, "Exzellentes Ergebnis (oder Funktionsstatus)"),
                ),
            ),
            // Each part as the original reckons it, so that the patient's parts read without the examiner's (08.11.2026).
            Score("schmerzen", "Schmerzen", listOf(pain.id), direction = Direction.HigherIsBetter),
            Score("adl", "ADL", ids(adl), direction = Direction.HigherIsBetter),
            Score("gehfaehigkeit", "Gehfähigkeit", ids(gait), direction = Direction.HigherIsBetter),
            Score("deformitaeten", "Deformitäten", ids(deformities), direction = Direction.HigherIsBetter),
            Score("mobilitaet", "Mobilität", listOf(mobility.id), direction = Direction.HigherIsBetter),
        ),
    )
}

/** Hip Osteoarthritis Outcome Score, German version (Blasimann 2014), as in "…_HOOS_11-2026.pdf". */
private val HOOS: Assessment = run {
    val difficulty = fourToZero("keine", "wenig", "mäßige", "große", "sehr große")
    val painfulness = fourToZero("keine", "wenig", "mäßige", "starke", "sehr starke")
    val howOften = fourToZero("nie", "monatlich", "wöchentlich", "täglich", "immer")
    val howMuch = fourToZero("gar nicht", "wenig", "mäßig", "stark", "sehr stark")
    val symptoms = listOf(
        ChoiceItem(
            "s1", "1. Spüren Sie ein Reiben, hören Sie ein Klicken, Knirschen, Knacken oder ein anderes Geräusch in der Hüfte?",
            fourToZero("nie", "selten", "manchmal", "oft", "immer"),
        ),
        ChoiceItem("s2", "2. Haben Sie Schwierigkeiten, die Beine zu spreizen?", difficulty),
        ChoiceItem("s3", "3. Haben Sie Schwierigkeiten, beim Gehen große Schritte zu machen?", difficulty),
    )
    val stiffness = listOf(
        ChoiceItem("s4", "4. Wie ausgeprägt ist die Steifigkeit in Ihrem Hüftgelenk wenn Sie morgens aufwachen?", howMuch),
        ChoiceItem(
            "s5", "5. Wie ausgeprägt ist die Steifigkeit in Ihrem Hüftgelenk nach dem Sitzen, Liegen oder Ausruhen im Verlauf des Tages?",
            fourToZero("niemals", "selten", "manchmal", "oft", "immer"),
        ),
    )
    val painHowOften = ChoiceItem("p1", "1. Wie oft haben Sie Schmerzen in der Hüfte?", howOften)
    val painDuring = listOf(
        "Vollständiges Strecken der Hüfte (Aufrichten)?",
        "Maximales Beugen der Hüfte?",
        "Gehen in unebenem Gelände?",
        "Treppen hinauf- oder hinuntersteigen?",
        "Nachts im Bett?",
        "Sitzen oder Liegen?",
        "Aufrecht stehen?",
        "Gehen auf hartem Boden?",
    ).mapIndexed { i, text -> ChoiceItem("p${i + 2}", "${i + 2}. $text", painfulness) }
    val adl = listOf(
        "Treppen hinuntersteigen?",
        "Treppen hinaufsteigen?",
        "Vom Sitzen aufstehen?",
        "Stehen?",
        "Sich bücken/einen Gegenstand vom Boden aufheben?",
        "Gehen in unebenem Gelände?",
        "Einsteigen in ein Auto/Aussteigen aus einem Auto?",
        "Einkaufen?",
        "Socken/Strümpfe anziehen?",
        "Vom Bett aufstehen?",
        "Socken/Strümpfe ausziehen?",
        "Im Bett liegen (Drehen mit Beibehalten der Hüftposition)?",
        "Einsteigen in die Badewanne/Aussteigen aus der Badewanne?",
        "Sitzen?",
        "Sich auf die Toilette hinsetzen und wieder aufstehen?",
        "Schwere Hausarbeit verrichten (Kisten tragen, Boden wischen…)?",
        "Leichte Hausarbeit verrichten (Kochen, Staub wischen…)?",
    ).mapIndexed { i, text ->
        // The original reads "einige" in place of "mäßige" for this one.
        val options = if (i + 1 == 12) fourToZero("keine", "wenig", "einige", "große", "sehr große") else difficulty
        ChoiceItem("a${i + 1}", "${i + 1}. $text", options)
    }
    val sport = listOf("Kauern/in die Hocke gehen?", "Rennen?", "Drehen auf dem belasteten Bein?", "Gehen in unebenem Gelände?")
        .mapIndexed { i, text -> ChoiceItem("sp${i + 1}", "${i + 1}. $text", difficulty) }
    val quality = listOf(
        ChoiceItem("q1", "1. Wie oft sind Sie sich Ihres Hüftproblems bewusst?", howOften),
        ChoiceItem("q2", "2. Haben Sie Ihren Lebensstil verändert, um Tätigkeiten zu vermeiden, die Ihrer Hüfte schaden könnten?", howMuch),
        ChoiceItem("q3", "3. Wie sehr belastet Sie Ihr fehlendes Vertrauen in Ihre Hüfte?", howMuch),
        ChoiceItem("q4", "4. Wie groß sind die Schwierigkeiten, die Sie im Allgemeinen mit Ihrer Hüfte haben?", difficulty),
    )
    val function = "Geben Sie bitte für jede der folgenden Tätigkeiten an, wie große Schwierigkeiten Ihnen Ihre Hüfte während der letzten Woche bereitet hat."
    // Each subscale from 0 to 100 of what is answered, once half of its questions are (08.11.2026).
    fun subscale(id: String, name: String, items: List<Item>) =
        Score(id, name, ids(items), rule = ScoreRule.ZeroTo100, unit = "%", direction = Direction.HigherIsBetter, minAnswered = half(items.size))
    Assessment(
        id = "hoos",
        name = "Hip Osteoarthritis Outcome Score",
        abbreviation = "HOOS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "HOOS, Stand 11/2026",
        hint = "Validierte deutsche Version, Blasimann 2014.\n\nDer folgende Fragebogen dient der Erfassung von Beschwerden und " +
            "Problemen, die durch Ihr Hüftgelenk verursacht werden. Die dadurch gewonnenen Informationen werden uns helfen " +
            "einzuschätzen, wie es Ihnen mit Ihrer Hüfte geht und wie gut Sie in der Lage sind, Ihre üblichen Aktivitäten zu " +
            "verrichten. Beantworten Sie bitte jede Frage durch Ankreuzen des zugehörigen Kästchens. Bitte nur ein Kästchen " +
            "pro Frage ankreuzen. Wenn Sie sich unsicher sind, wie Sie die Frage beantworten sollen, wählen Sie die Antwort " +
            "aus, die Ihnen am zutreffendsten erscheint. Bitte beantworten Sie alle Fragen gemäß Ihrem aktuellen Zustand. " +
            "Sollten Sie momentan keine Beschwerden haben, dann bewerten Sie die Fragen entsprechend Ihrem Zustand in der " +
            "vergangenen Woche.\n\n" +
            "Sollte der Patient manche Aktivitäten nicht ausführen, weil die Probleme ihn daran hindern, sollte mit der " +
            "schlechtesten Punktzahl gewertet werden (0 Punkte). Sollte der Patient nicht sportlich aktiv sein, so sollte die " +
            "entsprechende Subskala ausgelassen werden. Bei eher aktiven Patienten sollte die Subskala alltägliche Aktivitäten " +
            "ausgelassen werden. Vielmehr sollte die Subskala ausgewählt werden, welche die höchste Wertigkeit für den " +
            "Patienten besitzt.\n\n" +
            "Die minimal messbare Veränderung bzw. klinisch relevante Veränderung beträgt für die einzelnen Subskalen nach " +
            "Hüftarthroskopien (Kemp et al. 2013): Symptome: 14/9, Schmerz: 10/9, Aktivitäten des alltäglichen Lebens: 9/6, " +
            "Sportliche Aktivitäten/Freizeit: 17/10, Lebensqualität: 15/11.",
        sections = listOf(
            Section("Subskala Symptome", items = symptoms),
            Section(
                hint = "Die folgenden Fragen betreffen die Steifigkeit im Hüftgelenk, die Sie in der letzten Woche wahrgenommen " +
                    "haben. Steifigkeit ist ein Gefühl von Einschränkung oder Langsamkeit in den Bewegungen („harzige " +
                    "Bewegungen“) des Hüftgelenks.",
                items = stiffness,
            ),
            Section("Subskala Schmerz", items = listOf(painHowOften)),
            Section(hint = "Wie starke Schmerzen verspürten Sie in der Hüfte in der letzten Woche während der folgenden Tätigkeiten:", items = painDuring),
            Section(
                "Subskala Aktivitäten des alltäglichen Lebens",
                "Die nachfolgenden Fragen beziehen sich auf Ihre körperliche Funktion, das heißt, Ihre Fähigkeit, sich zu " +
                    "bewegen und für sich zu sorgen. $function",
                adl,
            ),
            Section(
                "Subskala sportliche Aktivitäten/Freizeit",
                "Die folgenden Aktivitäten beziehen sich auf Ihre körperliche Funktion bei anspruchsvolleren Tätigkeiten. $function",
                sport,
            ),
            Section("Subskala Lebensqualität", items = quality),
        ),
        scores = listOf(
            subscale("symptome", "Symptome", symptoms + stiffness),
            subscale("schmerz", "Schmerz", listOf(painHowOften) + painDuring),
            subscale("adl", "Aktivitäten des alltäglichen Lebens", adl),
            subscale("sport", "Sportliche Aktivitäten/Freizeit", sport),
            subscale("lebensqualitaet", "Lebensqualität", quality),
        ),
    )
}

/** Hüft Outcome Score, German version (Naal 2011), as in "Hueft-Outcome-Score_HOS_11-2026.pdf". */
private val HOS: Assessment = run {
    val options = scored(
        "Keinerlei Schwierigkeiten" to 4,
        "Geringe Schwierigkeiten" to 3,
        "Mäßige Schwierigkeiten" to 2,
        "Extreme Schwierigkeiten" to 1,
        "Unmöglich" to 0,
    ) + Option("na", "Nicht zutreffend", notApplicable = true)
    val adl = listOf(
        "15 Minuten stehen",
        "In ein gewöhnliches Auto ein- und aussteigen",
        "Socken und Schuhe anziehen*",
        "Steile Hügel hinauf gehen",
        "Steile Hügel hinunter gehen",
        "Eine Etage hinauf gehen",
        "Eine Etage hinunter gehen",
        "Bordsteine rauf und runter gehen",
        "Tief in die Hocke gehen",
        "In eine oder aus einer Badewanne steigen",
        "15 Minuten sitzen*",
        "Die ersten Schritte gehen",
        "Etwa 10 Minuten gehen",
        "15 Minuten oder länger gehen",
        "Sich auf dem betroffenen Bein drehen",
        "Sich im Bett umdrehen",
        "Leichte bis mittelschwere Arbeit (Stehen, Gehen)",
        "Schwere Arbeit (Schieben, Ziehen, Klettern, Tragen)",
        "Freizeitaktivitäten",
    ).mapIndexed { i, text -> ChoiceItem("adl${i + 1}", text, options) }
    val sport = listOf(
        "1-2 km Joggen",
        "Springen/Hüpfen",
        "Gegenstände wie z.B. einen Golfschläger schwingen",
        "Landen",
        "Schnell losrennen und abstoppen",
        "Haken schlagen/seitliche Bewegungen",
        "Wenig belastende Aktivitäten wie schnelles Gehen",
        "Aktivitäten so ausüben, wie Sie es normalerweise tun",
        "Einen gewünschten Sport so lange ausüben, wie Sie es wollen",
    ).mapIndexed { i, text -> ChoiceItem("sport${i + 1}", text, options) }
    // The third question is not scored.
    val grade = ChoiceItem(
        "funktionsgrad", "3. Wie würden Sie den momentanen Funktionsgrad Ihrer Hüfte beurteilen?",
        listOf("Normal", "Fast normal", "Unnormal", "Sehr unnormal").mapIndexed { i, label -> Option("$i", label) },
    )
    // The two starred are not scored, "wie von den Entwicklern hervorgehoben wurde".
    val scoredAdl = adl.filterNot { it.text.endsWith("*") }
    val question = "Wie groß sind Ihre Schwierigkeiten wegen Ihrer Hüfte bei folgenden Aktivitäten?"
    // In percent of the items answered with points, "Nicht zutreffend" left out; with any number answered (08.11.2026).
    fun percent(id: String, name: String, items: List<Item>) =
        Score(id, name, ids(items), rule = ScoreRule.Percent, unit = "%", direction = Direction.HigherIsBetter, minAnswered = 1)
    Assessment(
        id = "hos",
        name = "Hüft Outcome Score",
        abbreviation = "HOS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "HOS, Stand 11/2026",
        hint = "Validierte deutsche Version, Naal 2011.\n\nBitte kreuzen Sie an! Beantworten Sie jede Frage mit der Antwort, die " +
            "Ihren Zustand während der letzten Woche am besten beschreibt. Falls die betreffende Aktivität durch etwas anderes " +
            "als durch Ihre Hüfte eingeschränkt ist, markieren Sie ‘Nicht zutreffend‘.\n\n" +
            "Je höher der Wert, desto besser ist auch das funktionelle Endergebnis. Die MCID beträgt bei Patienten mit einem " +
            "femoroacetabulären Impingement für die HOS-ADL Subskala 8,8 (1 Jahr FU), 9,7 (2 Jahr FU), 10,2 (5 Jahr FU) und die " +
            "HOS-Sport Subskala 13,9 (1 Jahr FU), 14,2 (2 Jahr FU), 15,2 (5 Jahr), Nwachuku 2020). Die PASS-Kriterien (Patient " +
            "accepted symptom state) betragen für Patienten mit einem femoroacetabulären Impingement nach einem Jahr FU " +
            "postoperativ für die HOS-ADL Subskala 87 und die HOS-Sportsubskala 75 Punkte (Chahal 2015).",
        sections = listOf(
            Section("1. Alltagsaktivitäten", "$question\n\n*Diese 2 Punkte werden nicht bewertet, wie von den Entwicklern hervorgehoben wurde.", adl),
            Section("2. Sport", question, sport),
            Section(items = listOf(grade)),
        ),
        scores = listOf(
            percent("adl", "Alltagsaktivitäten", scoredAdl),
            percent("sport", "Sportaktivitäten", sport),
            percent("alle", "Alle Aktivitäten", scoredAdl + sport),
        ),
    )
}

/** International Cartilage Repair Society, as in "International-Cartilage-Repair-Society_ICRS_11-2026.pdf": a grade, without a result. */
private val ICRS: Assessment = run {
    val treatment3 = "Debridement der Wundränder und Behandlung des Schadens: - Abrasion, Drilling, MF, ACT, Mosaik"
    val grade = ChoiceItem(
        "grad", "ICRS-Grad",
        listOf(
            Option("0", "0 – Histologie: Normal. Behandlung: keine"),
            Option("1a", "1a – Histologie: Intakte Oberfläche aber Fibrillation und oder Erweichung. Behandlung: keine"),
            Option("1b", "1b – Histologie: zusätzlich Mikrorisse in der oberflächlichen Knorpelzone. Behandlung: keine"),
            Option(
                "2",
                "2 – Histologie: Instabile Knorpelläsionen, die weniger als 50% der Knorpeltiefe einnehmen. Behandlung: Gute " +
                    "Prognose mit einer Glättung der Ränder ohne eigentliche Behandlung des Schadens",
            ),
            Option("3a", "3a – Histologie: >50% der Knorpeltiefe mit intakter kalzifizierter Knorpelzone. Behandlung: $treatment3"),
            Option("3b", "3b – Histologie: >50% der Knorpeltiefe mit verletzter kalzifizierter Knorpelzone. Behandlung: $treatment3"),
            Option("3c", "3c – Histologie: >50 der Knorpeltiefe bis zum subchondralen Knochen (Knochenplatte intakt). Behandlung: $treatment3"),
            Option("3d", "3d – Histologie: Zusätzlich Blistering (Erhebung auf der Oberfläche). Behandlung: $treatment3"),
            Option(
                "4",
                "4 – Histologie: Subchondraler Knochen mit betroffen full thickness, aber keine Osteochondrosis dissecans. " +
                    "Behandlung: s.o. eventuell Knochentransplantation",
            ),
        ),
    )
    Assessment(
        id = "icrs",
        name = "International Cartilage Repair Society",
        abbreviation = "ICRS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "ICRS, Stand 11/2026",
        hint = "Evaluationsbogen für Knorpelschäden.",
        sections = listOf(Section(items = listOf(grade))),
    )
}

/** Knee Injury and Osteoarthritis Outcome Score, German version (Kessler et al. 2003), as in "…_KOOS_11-2026.pdf". */
private val KOOS: Assessment = run {
    val strength = zeroToFour("keine", "schwach", "mäßig", "stark", "sehr stark")
    // Typed "einge" for some items and "Sehr große" for one in the original; read as the rest.
    val difficulty = zeroToFour("keine", "wenig", "einige", "große", "sehr große")
    val often = zeroToFour("niemals", "selten", "manchmal", "oft", "immer")
    val can = zeroToFour("immer", "oft", "manchmal", "selten", "niemals")
    val painWhen = "Wie ausgeprägt waren Ihre Schmerzen in der vergangenen Woche, als Sie z.B.:"
    val trouble = "Welche Schwierigkeiten hatten Sie letzte Woche, als Sie z.B.:"
    val pain = listOf(koosItem("1", "1. Wie oft tut Ihnen Ihr Knie weh?", zeroToFour("niemals", "monatlich", "wöchentlich", "täglich", "immer"))) +
        listOf(
            "sich im Knie drehten?",
            "Ihr Knie ganz ausstreckten?",
            "Ihr Knie ganz beugten?",
            "auf ebenem Boden gingen?",
            "Treppen herauf oder herunter gingen?",
            "sich nachts im Bett drehten?",
            "saßen oder lagen, z.B. auf der Couch?",
            "aufrecht standen?",
        ).mapIndexed { i, text -> koosItem("${i + 2}", "${i + 2}. $painWhen $text", strength) }
    val symptoms = listOf(
        koosItem("10", "10. Haben Sie Schwellungen an Ihrem Knie?", often),
        koosItem("11", "11. Fühlen Sie manchmal ein Mahlen, hören Sie manchmal ein Klicken oder irgendein Geräusch, wenn Sie Ihr Knie bewegen?", often),
        koosItem("12", "12. Bleibt Ihr Knie manchmal hängen, oder blockiert es, wenn Sie es bewegen?", often),
        koosItem("13", "13. Können Sie Ihr Knie ganz ausstrecken?", can),
        koosItem("14", "14. Können Sie Ihr Knie ganz beugen?", can),
        koosItem("15", "15. Wie stark ist Ihre Kniesteifigkeit morgens direkt nach dem Aufstehen?", strength),
        koosItem("16", "16. Wie stark ist Ihre Kniesteifigkeit, nachdem Sie saßen, lagen, oder sich ausruhten im Verlauf des Tages?", strength),
    )
    val adl = listOf(
        "Treppen herunterstiegen?",
        "Treppen heraufstiegen?",
        "vom Sitzen aufstanden?",
        "standen?",
        "sich bückten, um etwas vom Boden aufzuheben?",
        "auf ebenem Boden gingen?",
        "ins Auto ein- oder ausstiegen?",
        "einkaufen gingen?",
        "Strümpfe oder Socken anzogen?",
        "vom Bett aufstanden?",
        "Strümpfe – Socken auszogen?",
        "im Bett lagen und sich drehten, ohne das Knie dabei zu beugen?",
        "in die oder aus der Badewanne kamen?",
        "saßen?",
        "sich auf die Toilette setzten oder aufstanden?",
        "schwere Hausarbeit verrichteten (Gartenarbeit…)?",
        "leichte Hausarbeit verrichteten (Staub wischen…)?",
    ).mapIndexed { i, text -> koosItem("${i + 17}", "${i + 17}. $trouble $text", difficulty) }
    val sport = listOf(
        koosItem("34", "34. $trouble in die Hocke gingen?", difficulty),
        koosItem("35", "35. $trouble rannten?", difficulty),
        koosItem("36", "36. $trouble hüpften?", difficulty),
        koosItem("37", "37. $painWhen sich auf ihrem kranken Knie umdrehten?", difficulty),
        koosItem("38", "38. $trouble sich hinknieten?", difficulty),
    )
    val quality = listOf(
        koosItem("39", "39. Wie oft spüren Sie Ihr erkranktes Knie?", zeroToFour("nie", "monatlich", "wöchentlich", "täglich", "immer")),
        koosItem(
            "40", "40. Haben Sie Ihre Lebensweise verändert, um eventuell Ihrem Knie schadende Tätigkeiten zu vermeiden?",
            zeroToFour("nicht", "wenig", "etwas", "stark", "vollständig"),
        ),
        koosItem(
            "41", "41. Wie sehr macht es Ihnen zu schaffen, daß Ihr Knie nicht stabil ist?",
            zeroToFour("Gar nicht", "wenig", "einiges", "schlimm", "sehr schlimm"),
        ),
        koosItem("42", "42. Wie würden Sie insgesamt die Schwierigkeiten bewerten, die Sie durch das Knie haben?", difficulty),
    )
    Assessment(
        id = "koos",
        name = "Knee Injury and Osteoarthritis Outcome Score",
        abbreviation = "KOOS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "KOOS, Stand 11/2026",
        hint = "Deutsche Version (Kessler et al. 2003).\n\n" + KOOS_INSTRUCTIONS + "\n\n" + KOOS_SUBSCALES + "\n\n" +
            "Die minimal messbare Veränderung beträgt für die einzelnen Subskalen nach Knieverletzungen (Collins et al. 2011): " +
            "Schmerz: 6-6,1, Symptome: 5-8,5, Alltägliche Aktivitäten: 7-8, Sportliche Aktivitäten: 5,8-12, Lebensqualität: " +
            "7,2. Bei Patienten mit einer Kniearthrose liegen die Werte für die minimal messbare Veränderung höher (Collins et " +
            "al. 2011). Die minimal klinisch relevante Veränderung sollte über 8-10 Punkte insgesamt betragen (Collins et al. " +
            "2011).",
        sections = listOf(
            Section("Subskala Schmerz", items = pain),
            Section("Subskala Symptome", items = symptoms),
            Section(
                "Subskala Aktivitäten des alltäglichen Lebens",
                "Die nachfolgenden Fragen beziehen sich auf Ihre körperliche Leistungsfähigkeit. Hierunter verstehen wir Ihre " +
                    "Fähigkeit, sich selbständig zu bewegen bzw. sich selbst zu versorgen. Für jede der nachfolgenden " +
                    "Aktivitäten sollen Sie das Ausmaß der Schwierigkeiten angeben, welche Sie durch Ihr Kniegelenk innerhalb " +
                    "der letzten Woche erfahren haben.",
                adl,
            ),
            Section(
                "Subskala sportliche Aktivitäten/Freizeit",
                "Die nachfolgenden Fragen beziehen sich auf Ihre körperliche Belastbarkeit im Rahmen eher sportlicher Aktivitäten.",
                sport,
            ),
            Section("Subskala Lebensqualität", items = quality),
        ),
        scores = listOf(
            koosSubscale("schmerz", "Schmerz", pain),
            koosSubscale("symptome", "Symptome", symptoms),
            koosSubscale("adl", "Aktivitäten des alltäglichen Lebens", adl),
            koosSubscale("sport", "Sportliche Aktivitäten/Freizeit", sport),
            koosSubscale("lebensqualitaet", "Lebensqualität", quality),
        ),
    )
}

private const val KOOS_INSTRUCTIONS = "Der folgende Fragebogen dient der Erfassung von Beschwerden und Problemen, die durch Ihr " +
    "Kniegelenk verursacht werden. Die dadurch gewonnenen Informationen werden uns helfen einzuschätzen, wie es Ihnen mit " +
    "Ihrem Knie geht und wie gut Sie in der Lage sind, Ihre üblichen Aktivitäten zu verrichten. Beantworten Sie bitte jede " +
    "Frage durch Ankreuzen des zugehörigen Kästchens. Bitte nur ein Kästchen pro Frage Ankreuzen. Wenn Sie sich unsicher " +
    "sind, wie Sie die Frage beantworten sollen, wählen Sie die Antwort aus, die Ihnen am zutreffendsten erscheint. Bitte " +
    "beantworten Sie alle Fragen gemäß Ihrem aktuellen Zustand. Sollten Sie momentan keine Beschwerden haben, dann bewerten " +
    "Sie die Fragen entsprechend Ihrem Zustand in der vergangenen Woche."

private const val KOOS_SUBSCALES = "Sollte der Patient manche Aktivitäten nicht ausführen, weil die Probleme ihn daran " +
    "hindern, sollte mit der schlechtesten Punktzahl gewertet werden (0 Punkte). Sollte der Patient nicht sportlich aktiv " +
    "sein, so sollte die entsprechende Subskala ausgelassen werden. Bei eher aktiven Patienten sollte die Subskala " +
    "alltägliche Aktivitäten ausgelassen werden. Sie hat mit 17 Items einen zu großen Einfluss auf das Ergebnis einer " +
    "Patientengruppe, die bei diesen Aktivitäten keine oder nur geringe Probleme hat. Es ist also nicht erwünscht, dass grundsätzlich immer alle Subskalen " +
    "benützt werden. Vielmehr sollte die Subskala ausgewählt werden, welche die höchste Wertigkeit für den Patienten besitzt."

/**
 * A subscale of the KOOS or KOOS-Kind: 100 − mean × 100 / 4 of its items, scored 0 for few symptoms to 4; as 0 to 100 of
 * the points the other way round, so that 100 is the best. At most two of its items may be left out (08.11.2026).
 */
private fun koosSubscale(id: String, name: String, items: List<Item>) = Score(
    id, name, ids(items),
    rule = ScoreRule.ZeroTo100, unit = "%", direction = Direction.HigherIsBetter, minAnswered = maxOf(1, items.size - 2),
)

/** KOOS-Kind Knie-Fragebogen, German version (Neuhaus et al. 2022), as in "…_KOOS-child_11-2026.pdf". */
private val KOOS_CHILD: Assessment = run {
    val often = zeroToFour("nie", "selten", "manchmal", "oft", "immer")
    val hard = zeroToFour("überhaupt nicht", "wenig schwierig", "mäßig schwierig", "schwierig", "sehr schwierig")
    val symptoms = listOf(
        koosItem("s1", "S1. Wie oft war Dein Knie in den letzten 7 Tagen geschwollen?", often),
        koosItem("s2", "S2. Wie oft hat Dein Knie in den letzten 7 Tagen irgendwelche Geräusche (z.B. ein Knirschen, ein Klicken) gemacht?", often),
        koosItem("s3", "S3. Wie oft war Dein Knie in den letzten 7 Tagen blockiert, wenn Du es bewegt hast?", often),
        koosItem("s4", "S4. Wie oft konntest Du Dein Knie in den letzten 7 Tagen selbständig ganz strecken?", zeroToFour("immer", "oft", "manchmal", "selten", "nie")),
        koosItem("s5", "S5. Wie oft konntest Du Dein Knie in den letzten 7 Tagen selbständig ganz beugen?", zeroToFour("immer", "oft", "manchmal", "selten", "nie")),
        koosItem("s6", "S6. Wie schwierig war es in den letzten 7 Tagen für Dich, direkt nach dem Aufwachen am Morgen Dein Knie zu bewegen?", hard),
    )
    val painHowOften = koosItem("p1", "P1. Wie oft hattest Du im letzten Monat Knieschmerzen?", zeroToFour("nie", "selten", "manchmal", "oft", "die ganze Zeit"))
    val painfulness = zeroToFour("keine Schmerzen", "ein wenig Schmerzen", "mäßige Schmerzen", "starke Schmerzen", "sehr starke Schmerzen")
    val painDuring = listOf(
        "p2" to "P2. Bei Drehbewegungen auf Deinem verletzten Knie während dem Gehen/Stehen/Rennen.",
        "p3" to "P3. Beim vollen Strecken des verletzten Knies.",
        "p4" to "P4. Beim vollen Beugen des verletzten Knies.",
        "p6a" to "P6a. Beim Treppen Hinaufsteigen.",
        "p6b" to "P6b. Beim Treppen Hinuntergehen.",
        "p8a" to "P8a. Beim Sitzen mit Deinem verletzten Knie in gebeugter Position.",
        "p9" to "P9. Beim Aufrechtstehen auf beiden Beinen für eine gewisse Zeit.",
    ).map { (id, text) -> koosItem(id, text, painfulness) }
    val adl = listOf(
        "a1" to "A1. Wie schwierig war es in den letzten 7 Tagen für Dich, Treppen hinunterzusteigen?",
        "a2" to "A2. Wie schwierig war es in den letzten 7 Tagen für Dich, Treppen hinaufzusteigen?",
        "a3" to "A3. Wie schwierig war es in den letzten 7 Tagen für Dich, von einem Stuhl aufzustehen?",
        "a5" to "A5. Wie schwierig war es für Dich in den letzten 7 Tagen, Dich zu bücken und etwas vom Boden aufzuheben?",
        "a7" to "A7. Wie schwierig war es in den letzten 7 Tagen für Dich, in ein Auto ein- und auszusteigen?",
        "a10" to "A10. Wie schwierig war es in den letzten 7 Tagen für Dich, aus dem Bett aufzustehen?",
        "a12" to "A12. Wie schwierig war es in den letzten 7 Tagen für Dich, Deine Knieposition zu verändern, wenn Du im Bett lagst?",
        "a13" to "A13. Wie schwierig war es in den letzten 7 Tagen für Dich, in die Badewanne/ Dusche ein- oder auszusteigen?",
        "a14" to "A14. Wie schwierig war es in den letzten 7 Tagen für Dich, mit gebeugtem verletzten Knie auf einen Stuhl zu sitzen?",
        "a16" to "A16. Wie schwierig war es in den letzten 7 Tagen für Dich, schwere Taschen oder einen Rucksack etc. zu tragen?",
        "a17" to "A17. Wie schwierig war es in den letzten 7 Tagen für Dich, kleinere Hausarbeiten zu erledigen wie Dein Zimmer " +
            "aufräumen, die Abwaschmaschine ein- oder ausräumen, Dein Bett machen etc.?",
    ).map { (id, text) -> koosItem(id, text, hard) }
    val sport = listOf(
        "sp1" to "SP1. Wie schwierig war es für Dich in den letzten 7 Tagen, Dich bei Sport- und Freizeitaktivitäten zu bücken/knien?",
        "sp2" to "SP2. Wie schwierig war es in den letzten 7 Tagen für Dich, während Sport und Freizeitaktivitäten zu rennen?",
        "sp3" to "SP3. Wie schwierig war es in den letzten 7 Tagen für Dich, während Sport und Freizeitaktivitäten in die Höhe zu springen?",
        "sp4" to "SP4. Wie schwierig war es in den letzten 7 Tagen für Dich aufgrund Deines verletzten Knies, Dich während Sport und Freizeitaktivitäten zu drehen?",
        "sp5" to "SP5. Wie viel Schwierigkeiten hattest Du aufgrund Deines verletzten Knies in den letzten 7 Tagen dabei, zu knien?",
        "spn6" to "SPN6. Wie viel Schwierigkeiten hattest Du in den letzten 7 Tagen dabei, beim Gehen/Rennen auf unebenem Boden Dein Gleichgewicht zu behalten?",
        "spn7" to "SPN7. Wie viel Schwierigkeiten hattest Du aufgrund Deines verletzten Knies in den letzten 7 Tagen dabei, Sport zu treiben?",
    ).map { (id, text) -> koosItem(id, text, hard) }
    val quality = listOf(
        koosItem("q1", "Q1. Wie oft denkst Du über Deine Knieprobleme nach?", zeroToFour("nie", "wenig", "manchmal", "oft", "die ganze Zeit")),
        koosItem("q2", "Q2. Wie stark hast Du Deine Lebensgewohnheiten aufgrund der Knieverletzung geändert?", zeroToFour("überhaupt nicht", "ein wenig", "etwas", "stark", "sehr stark")),
        koosItem("q3", "Q3. Wie sehr vertraust Du Deinem verletzten Knie?", zeroToFour("vollständig", "weitgehend", "teilweise", "kaum", "überhaupt nicht")),
        koosItem("q4", "Q4. Wie viele Probleme hast Du mit Deinem verletzten Knie insgesamt?", zeroToFour("keine", "wenig", "an und zu", "viel", "extrem")),
        koosItem(
            "qn5",
            "QN5. Wie schwierig war es für Dich aufgrund Deines verletzten Knies, in die Schule zu kommen oder in der Schule " +
                "herumzugehen (Treppensteigen, Türe öffnen, Bücher tragen, in der Pause mitspielen)?",
            hard,
        ),
        koosItem("qn6", "QN6. Wie schwierig war es für Dich aufgrund Deines verletzten Knies, mit Freunden etwas zu unternehmen?", hard),
    )
    Assessment(
        id = "koos-child",
        name = "KOOS-Kind Knie-Fragebogen",
        abbreviation = "KOOS-Child",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "KOOS-child, Stand 11/2026",
        hint = "Deutsche Version (Neuhaus et al. 2022).\n\nDiese Fragen sammeln Informationen darüber, wie Dich Dein verletztes " +
            "Knie beeinflusst. Bitte beantworte jede Frage, indem Du das für Dich passende Kästchen ankreuzt. Mache pro Frage " +
            "nur ein Kreuz. Wenn Du Dir unsicher bist, wie Du eine Frage beantworten sollst, wähle die für Dich am besten " +
            "passende Antwort aus.\n\n" + KOOS_SUBSCALES,
        sections = listOf(
            Section("Knieprobleme", items = symptoms),
            Section(items = listOf(painHowOften)),
            Section(
                "Wie schmerzhaft",
                "Wie starke Knieschmerzen hattest Du in den letzten 7 Tagen während den folgenden Aktivitäten? Kreuze die am " +
                    "meisten passende Antwort für jede Frage an.",
                painDuring,
            ),
            Section("Schwierigkeiten bei täglichen Aktivitäten", items = adl),
            Section("Schwierigkeiten bei Sport- und Freizeitaktivitäten", items = sport),
            Section("Inwiefern ist Dein Leben durch Dein verletztes Knie beeinträchtigt?", items = quality),
        ),
        scores = listOf(
            koosSubscale("symptome", "Symptome", symptoms),
            koosSubscale("schmerz", "Schmerz", listOf(painHowOften) + painDuring),
            koosSubscale("adl", "Alltägliche Aktivitäten", adl),
            koosSubscale("sport", "Sportliche Aktivitäten", sport),
            koosSubscale("lebensqualitaet", "Lebensqualität", quality),
        ),
    )
}

/** Knee Outcome Survey – Activities of Daily Living, German version (Bizzini 2007), as in "Knee-Outcome-Survey_KOS-ADL_11-2026.pdf". */
private val KOS_ADL: Assessment = run {
    val effect = scored(
        "Kein Symptom" to 5,
        "Ich habe das Symptom aber es beeinträchtigt mich nicht." to 4,
        "Das Symptom beeinträchtigt meine Aktivität leicht." to 3,
        "Das Symptom beeinträchtigt meine Aktivität mäßig." to 2,
        "Das Symptom beeinträchtigt meine Aktivität stark." to 1,
        "Das Symptom hindert mich an allen täglichen Aktivitäten." to 0,
    )
    val hard = scored(
        "Nicht schwierig" to 5, "Kaum schwierig" to 4, "Etwas schwierig" to 3, "Ziemlich schwierig" to 2, "Sehr schwierig" to 1, "Nicht auszuführen" to 0,
    )
    val symptoms = listOf("Schmerz", "Steifigkeit", "Schwellung", "Nachgeben/Einknicken des Knies", "Schwäche", "Hinken")
        .mapIndexed { i, text -> ChoiceItem("s${i + 1}", text, effect) }
    val function = listOf(
        "Zu Gehen", "Treppe hoch gehen", "Treppe runter gehen", "Zu stehen", "Zu knien", "In die Hocke zu gehen",
        "Mit gebeugtem Knie zu sitzen", "Von einem Stuhl aufzustehen",
    ).mapIndexed { i, text -> ChoiceItem("f${i + 1}", text, hard) }
    Assessment(
        id = "kos-adl",
        name = "Knee Outcome Survey (alltägliche Aktivitäten)",
        abbreviation = "KOS-ADL",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "KOS-ADL, Stand 11/2026",
        hint = "Validierte deutsche Version (Bizzini 2007).\n\nDer folgende Fragebogen dient der Erfassung von Beschwerden und " +
            "Problemen bei alltäglichen Aktivitäten, die durch Ihr Kniegelenk verursacht werden. Es sind dabei Symptome von " +
            "Bedeutung, die Sie in den vergangenen 1-2 Tagen am deutlichsten wahrgenommen haben. Machen Sie bitte nur ein " +
            "Kreuz in jeder Zeile, auch wenn mehrere Symptome auf Sie zu treffen.\n\n" +
            "Je höher das Ergebnis, desto besser ist die Kniefunktion. Wird der Fragebogen in der Verlaufskontrolle verwendet, " +
            "gilt eine Veränderung von 10 Punkten als geringster Wert für eine relevante Veränderung. Bei Patienten mit einem " +
            "patellofemoralen Schmerzsyndrom beträgt die klinisch relevante Veränderung 7,1 Punkte (Collins et al. 2011). Nach " +
            "Collins et al. (2011) und Esculier et al. (2013) beträgt die minimal messbare Veränderung 11 Punkte bzw. 8,3% " +
            "(beide Untersuchungen wurden bei Patienten mit einem Patellofemoralen Schmerzsyndrom durchgeführt).",
        sections = listOf(
            Section("Symptome", "In welchem Ausmaß beeinträchtigt jedes der folgenden Symptome Ihre Alltagsaktivität?", symptoms),
            Section("Funktionelle Einschränkung bei Aktivitäten des täglichen Lebens", "Wie beeinträchtigt Ihr Knie Ihre Fähigkeit…", function),
        ),
        scores = listOf(
            Score("score", "Score", ids(symptoms + function), rule = ScoreRule.Percent, unit = "%", direction = Direction.HigherIsBetter),
        ),
    )
}

/** Kujala Anterior Knee Pain Scale, German version (Dammerer et al. 2018), as in "Kujala-Anterior-Knee-Pain-Scale_11-2026.pdf". */
private val KUJALA: Assessment = run {
    val items = listOf(
        "Hinken beim Gehen" to scored("Nein" to 5, "Leicht / gelegentlich" to 3, "Ständig" to 0),
        "Gewichtsbelastung" to scored("Vollbelastung ohne Schmerzen" to 5, "Schmerzhaft" to 3, "Belastung unmöglich" to 0),
        "Gehen" to scored("Uneingeschränkt möglich" to 5, "Mehr als 2 Km" to 3, "1-2 km" to 2, "Unmöglich" to 0),
        "Treppen steigen" to scored(
            "Keine Schwierigkeiten" to 10,
            "Leichte Schmerzen beim Hinuntergehen" to 8,
            "Schmerz sowohl beim Hinuntergehen als auch beim Hinaufgehen" to 5,
            "Unmöglich" to 0,
        ),
        "In die Knie gehen" to scored(
            "Keine Schwierigkeiten" to 5,
            "Wiederholtes in-die-Knie-gehen schmerzhaft" to 4,
            "Jedes Mal schmerzhaft" to 3,
            "Möglich mit Teil-Entlastung (z.B. Aufstützen oder Hilfsperson)" to 2,
            "Unmöglich" to 0,
        ),
        "Laufen" to scored(
            "Keine Schwierigkeiten" to 10,
            "Schmerzen nach mehr als 2 km" to 8,
            "Leichte Schmerzen von Anfang an" to 6,
            "Starke Schmerzen" to 3,
            "Unmöglich" to 0,
        ),
        "Springen" to scored("Keine Schwierigkeiten" to 10, "Leichte Schwierigkeiten" to 7, "Immer schmerzhaft" to 2, "Unmöglich" to 0),
        "Längeres Sitzen mit gebeugten Knien" to scored(
            "Keine Schwierigkeiten" to 10,
            "Schmerzhaft nur nach vorangegangener körperlicher Belastung" to 8,
            "Immer schmerzhaft" to 6,
            "Schmerzen so stark, dass die Knie von Zeit zu Zeit gestreckt werden müssen" to 4,
            "Unmöglich" to 0,
        ),
        "Knieschmerzen allgemein" to scored(
            "Keine" to 10, "Leicht und gelegentlich" to 8, "Stören den Schlaf" to 6, "Zeitweise sehr stark" to 3, "Ständig und sehr stark" to 0,
        ),
        "Schwellung" to scored(
            "Keine" to 10, "Nach körperlicher Belastung" to 8, "Nach alltäglichen Aktivitäten" to 6, "Jeden Abend" to 4, "Ständig" to 0,
        ),
        "Abnormale, schmerzhafte Kniescheiben-Bewegungen (Subluxationen)" to scored(
            "Keine" to 10,
            "Gelegentlich bei sportlicher Aktivität" to 6,
            "Gelegentlich bei alltäglichen Aktivitäten" to 4,
            "Ein oder zwei dokumentierte Kniescheiben-Luxationen (Ausrenkungen)" to 2,
            "Drei oder mehr Kniescheiben-Luxationen" to 0,
        ),
        "Muskelschwund am Oberschenkel" to scored("Nein" to 5, "Leicht" to 3, "Stark" to 0),
        "Einschränkung beim Beugen des Kniegelenks" to scored("Nein" to 5, "Leicht" to 3, "Stark" to 0),
    ).mapIndexed { i, (text, options) -> ChoiceItem("${i + 1}", "${i + 1}. $text", options) }
    Assessment(
        id = "kujala",
        name = "Kujala Anterior Knee Pain Scale",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "Kujala Anterior Knee Pain Scale, Stand 11/2026",
        hint = "Validierte deutsche Version (Dammerer et al. 2018).\n\nSehr geehrter Patient, der folgende Fragebogen dient der " +
            "Erfassung von Beschwerden und Problemen, die durch Ihr Kniegelenk verursacht werden. Die dadurch gewonnenen " +
            "Informationen werden uns helfen einzuschätzen, wie es Ihnen mit Ihrem Knie geht und wie gut Sie in der Lage sind, " +
            "Ihre üblichen Aktivitäten zu verrichten. Beantworten Sie bitte jede Frage durch Ankreuzen des zugehörigen " +
            "Kästchens. Bitte beantworten Sie alle Fragen gemäß Ihrem aktuellen Zustand. Sollten Sie momentan keine " +
            "Beschwerden haben, dann bewerten Sie die Fragen entsprechend Ihrem Zustand in der vergangenen Woche.\n\n" +
            "Der Gesamtscore beträgt 100 Punkte für ein maximal gutes Ergebnis. Der schlechteste Wert und damit eine starke " +
            "Einschränkung liegt bei 0 Punkten.",
        sections = listOf(Section(items = items)),
        scores = listOf(Score("gesamt", "Gesamtscore", ids(items), direction = Direction.HigherIsBetter)),
    )
}

/** Landing Error Scoring System, modified German version, as in "Landing-Error-Scoring-System_LESS_11-2026.pdf": an examination. */
private val LESS: Assessment = run {
    val sagittal = listOf(
        "Der Knieflexionswinkel beträgt beim initialen Kontakt >30°.",
        "Der Hüftflexionswinkel ist beim initialen Kontakt größer als der Knieflexionswinkel.",
        "Der Vorfuß hat beim initialen Kontakt zuerst mit dem Boden Kontakt.",
        "Alle Gelenke der unteren Extremität haben sich bis zum tiefsten Punkt weiter gebeugt.",
        "Allgemeiner Eindruck: Die Landung erfolgt kontrolliert und weich.",
    ).mapIndexed { i, text -> YesNoItem("${i + 1}", "${i + 1}. $text") }
    val frontal = listOf(
        "Das Kniegelenk weist beim initialen Kontakt keine Valgusstellung auf.",
        "Der Rumpf ist beim initialen Kontakt nicht zur Seite geneigt.",
        "Die Füße werden symmetrisch aufgesetzt.",
        "In der tiefsten Beugestellung werden die Kniegelenke nicht weiter nach innen bewegt (valgus).",
        "In der tiefsten Beugestellung sind die Füße weder nach innen noch nach außen gedreht.",
    ).mapIndexed { i, text -> YesNoItem("${i + 6}", "${i + 6}. $text") }
    Assessment(
        id = "less",
        name = "Landing Error Scoring System",
        abbreviation = "LESS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "LESS, Stand 11/2026",
        hint = "Modifizierte deutsche Version.\n\nDas LESS verfolgt das Ziel, die Landung des Patienten zu beurteilen. Besteht ein " +
            "gutes Ergebnis, kann der Patient gefahrlos in dynamische Sportarten entlassen werden. Ist die Landung defizitär, so " +
            "besteht im Zusammenhang mit anderen Risikofaktoren ein erhebliches Risiko für eine Retraumatisierung.\n\n" +
            "Ausführung: Der Patient springt von einer Erhöhung (30cm) nach unten und springt so schnell wie möglich nach oben " +
            "wieder ab (vertikal drop jump). Der Patient wird dabei von der Seite und von vorne mit einer Kamera aufgenommen. " +
            "Geübte Therapeuten sind in der Lage, ohne optische Hilfsmittel eine Beurteilung vorzunehmen. Es werden jeweils " +
            "drei Versuche durchgeführt.\n\n" +
            "Beurteilung: Von besonderem Interesse ist insbesondere der initiale Kontakt der Landung sowie die Endposition der " +
            "Gelenke (an der Umkehr bzw. dem tiefsten Punkt der Amplitude). Für jede erfüllte Kondition erhält der Proband 1 " +
            "Punkt. Dies ergibt im Maximum einen Score von 10 Punkten. Je höher der Score, desto besser ist die Qualität beim " +
            "Landen. Es sollte mindestens ein gutes Ergebnis erreicht werden.\n\n" +
            "Eine defizitäre Landetechnik korreliert mit einer höheren Belastung für das vordere Kreuzband und andere passive " +
            "Strukturen. Das Assessment der Landetechnik allein reicht aber für eine Risikobeurteilung des Patienten nicht aus. " +
            "Darüberhinaus müssen Kraftwerte der unteren Extremität, die Ergebnisse aus Sprungtests und anderen Fragebögen " +
            "hinzugezogen werden.",
        sections = listOf(
            Section("Sagittale Ebene", items = sagittal),
            Section("Frontale Ebene", items = frontal),
            Section(items = listOf(TextItem("empfehlung", "Trainingsempfehlung (Ebene, Dynamik, begleitende Maßnahmen)"))),
        ),
        scores = listOf(
            Score(
                id = "gesamt",
                name = "Gesamtscore LESS",
                items = ids(sagittal + frontal),
                direction = Direction.HigherIsBetter,
                // "Exzellent: 10, Gut: 8-10, Moderat: <8, Defizitär: <5": each from the narrower, 10 as excellent.
                bands = listOf(
                    Band.upTo(4, "Defizitär"),
                    Band.upTo(7, "Moderat"),
                    Band.upTo(9, "Gut"),
                    Band.upTo(10, "Exzellent"),
                ),
            ),
            Score("sagittal", "Gesamtpunktzahl – sagittale Ebene", ids(sagittal), direction = Direction.HigherIsBetter),
            Score("frontal", "Gesamtpunktzahl frontale Ebene", ids(frontal), direction = Direction.HigherIsBetter),
        ),
    )
}

/** Lysholm Score, modified after Lysholm and Gillquist, as in "Lysholm-Score_11-2026.pdf". */
private val LYSHOLM: Assessment = run {
    val items = listOf(
        "Hinken" to scored("nein" to 5, "wenig oder zeitweise" to 3, "stark oder immer" to 0),
        "Belastung" to scored("Vollbelastung" to 5, "Gehstützen oder Stock" to 2, "Belastung nicht möglich" to 0),
        "Blockierung" to scored(
            "keine Blockierung und kein Gefühl der Einklemmung" to 15,
            "Gefühl der Einklemmung aber keine Blockierung" to 10,
            "gelegentliche Blockierung" to 6,
            "häufige Blockierung" to 2,
            "blockiertes Gelenk bei Untersuchung" to 0,
        ),
        "Instabilität" to scored(
            "Niemals „giving way“ Phänomen" to 25,
            "„giving way“ selten während des Sports oder anderer schwerer Anstrengung" to 20,
            "„giving way“ häufig während des Sports oder anderer schwerer Anstrengung (oder unmöglich, daran teilzunehmen)" to 15,
            "„giving way“ gelegentlich während Tätigkeiten des Alltags" to 10,
            "„giving way“ oft während Tätigkeiten des Alltags" to 5,
            "„giving way“ bei jedem Schritt" to 0,
        ),
        "Schmerzen" to scored(
            "keine" to 25,
            "unregelmäßig and gering während schwerer Anstrengung" to 20,
            "deutlich/ausgeprägt während schwerer Anstrengung" to 15,
            "deutlich während oder nach dem Gehen von mehr als 2km" to 10,
            "deutlich während oder nach dem Gehen von weniger als 2km" to 5,
            "ständig" to 0,
        ),
        "Schwellung" to scored("keine" to 10, "bei schwere Anstrengung" to 6, "bei gewöhnlicher Anstrengung" to 2, "ständig" to 0),
        "Treppensteigen" to scored("kein Problem" to 10, "ein wenig beeinträchtigt" to 6, "Schritt für Schritt" to 2, "nicht möglich" to 0),
        "Hocken" to scored("kein Problem" to 5, "wenig beeinträchtigt" to 4, "nicht über 90°" to 2, "nicht möglich" to 0),
    ).map { (text, options) -> ChoiceItem(text.lowercase().replace('ä', 'a'), text, options) }
    Assessment(
        id = "lysholm",
        name = "Lysholm Score",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "Lysholm Score, Stand 11/2026",
        hint = "Modifizierter Score nach Lysholm und Gillquist.",
        sections = listOf(Section(items = items)),
        scores = listOf(Score("gesamt", "Gesamtpunktezahl", ids(items), direction = Direction.HigherIsBetter)),
    )
}

/** Tegner Activity Scale, German version (Wirth 2013), as in "Tegner-Activity-Scale_TAS_11-2026.pdf": one grade from 0 to 10. */
private val TEGNER: Assessment = run {
    val levels = listOf(
        10 to "Wettkampfsport – auf nationalem und internationalem Niveau: Fußball, Ski alpin (auf nationalem oder internationalem Niveau)",
        9 to "Wettkampfsport: Eishockey, Ringen, Turnen, Fußball, Ski alpin (auf regionalem Niveau)",
        8 to "Wettkampfsport: Snowboard, Badminton, Squash, Leichtathletik (Sprungdisziplinen)",
        7 to "Wettkampfsport: Tennis, Leichtathletik, (Lauf- und Wurfdisziplinen), Geräteturnen, Handball, Basketball, " +
            "Orientierungslauf, Crosslauf\nFreizeitsport: Eishockey, Fußball, Ski alpin",
        6 to "Freizeitsport: Badminton, Tennis, Squash, Basketball, Handball, Volleyball, Orientierungslauf, Crosslauf, Snowboard, " +
            "Aerobic (high impact), Joggen (mindestens 5x die Woche)",
        5 to "Wettkampfsport: Radfahren, Skilanglauf, Eiskunstlauf\nFreizeitsport: Turnen, Gymnastik, In-line-skating, Klettern, " +
            "Bergsteigen, Skitouren, Schneeschuhlaufen, Joggen auf unebenem Boden (mindestens. 2x die Woche)\nArbeit: Schwere " +
            "körperliche Arbeit (Bauarbeit, Waldarbeit)",
        4 to "Freizeitsport: Skilanglauf, Radfahren, Tanzen, Aerobic (low impact), Bergwandern (abwärts), Joggen auf ebenem " +
            "Boden (mind. 2x die Woche)\nArbeit: Mittelschwere körperliche Arbeit (schwere Hausarbeit)",
        3 to "Wettkampf- und Freizeitsport: Schwimmen, Wandern, Walking, Kegeln, Bowling\nArbeit: Leichte körperliche Arbeit\n" +
            "Gehen: Querfeldein ist möglich",
        2 to "Arbeit: Vorwiegend sitzende Tätigkeit\nGehen: Auf unebenem Boden möglich",
        1 to "Arbeit: Sitzende Tätigkeit\nGehen: Nur auf ebenem Boden möglich",
        0 to "Arbeit: Arbeitsunfähig oder berentet aufgrund von Knieproblemen\nGehen: Nur eingeschränkt möglich",
    )
    val level = ChoiceItem("aktivitaetsgrad", "Aktivitätsgrad", levels.map { (n, text) -> Option("$n", "$n – $text", n) })
    Assessment(
        id = "tegner",
        name = "Tegner Activity Scale",
        abbreviation = "TAS",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "TAS, Stand 11/2026",
        hint = "Validierte deutsche Version (Wirth 2013).\n\nMit den nachfolgenden Fragen möchten wir untersuchen, wie gut Ihre " +
            "alltags- und sportartspezifische Funktion ist. Bitte lesen Sie die nachfolgende Aufstellung durch. Beurteilen Sie " +
            "Ihren Aktivitätsstatus und kreuzen Sie die betreffende Spalte an.\n\n" +
            "Ein hohe Wertung spricht für einen sehr aktiven Lebensstil. Die Skala eignet sich zur Verlaufskontrolle, " +
            "insbesondere um den prä- und postoperativen Status oder die Wirksamkeit von anderen Behandlungsmöglichkeiten zu " +
            "vergleichen. Die minimal klinisch relevante Veränderung beträgt 1,4 Punkte (Wirth et al. 2013).",
        sections = listOf(Section(items = listOf(level))),
        scores = listOf(Score("wertung", "Wertung", listOf(level.id), unit = "", direction = Direction.HigherIsBetter)),
    )
}

/** Victorian Institute of Sport Assessment – Patella, German version (Lohrer et al. 2011), as in "…_VISA-P-G-Score_11-2026.pdf". */
private val VISA_P_G: Assessment = run {
    fun pain(id: String, text: String) = ScaleItem(id, text, 0, 10, "Starker heftiger Schmerz", "Kein Schmerz")
    val sitting = ChoiceItem(
        "1", "1. Wie viele Minuten können Sie schmerzfrei sitzen?",
        (1..10).map { Option("$it", "${it * 10} Minuten", it) },
    )
    val scales = listOf(
        pain("2", "2. Haben Sie Schmerzen, wenn Sie mit normaler Geschwindigkeit die Treppe heruntersteigen?"),
        pain("3", "3. Haben Sie Knieschmerzen, wenn Sie das unbelastete Knie aktiv voll durchstrecken?"),
        pain("4", "4. Haben Sie Schmerzen, wenn Sie einen Ausfallschritt mit vollem Körpergewicht nach vorne machen?"),
        ScaleItem("5", "5. Haben Sie Probleme, wenn Sie in der tiefen Hocke sitzen?", 0, 10, "Unmöglich", "Kein Problem"),
        pain("6", "6. Haben Sie Schmerzen, während oder unmittelbar nachdem Sie 10 Mal auf einem Bein gehüpft sind?"),
    )
    val sport = ChoiceItem(
        "7", "7. Betreiben Sie zurzeit Sport oder sind Sie anderweitig körperlich aktiv?",
        scored(
            "Nein, gar nicht; wegen der Patellasehnenprobleme" to 0,
            "Veränderte Trainings- und Wettkampffähigkeit wegen der Patellasehnenprobleme" to 4,
            "Volle Trainings- und Wettkampffähigkeit, aber auf niedrigerem Niveau als vor Beginn der Symptome" to 7,
            "Gleiche oder bessere Wettkampffähigkeit als vor Beginn der Symptome" to 10,
        ),
    )
    // Which of 8a, 8b and 8c to answer; it makes no points itself.
    val which = ChoiceItem(
        "8", "8. Bitte beantworten Sie entweder A oder B oder C dieser Frage.",
        listOf(
            Option("a", "Wenn Sie während der Sportausübung keine Schmerzen beim Sport haben, dann beantworten Sie bitte nur die Frage 8a."),
            Option("b", "Wenn Sie während der Sportausübung Schmerzen haben, die Sie aber nicht zum Abbrechen der Aktivität zwingen, dann beantworten Sie bitte nur die Frage 8b."),
            Option("c", "Wenn Sie während der Sportausübung Schmerzen haben, die Sie zum Abbrechen der Aktivität zwingen, dann beantworten Sie bitte nur die Frage 8c."),
        ),
    )
    fun minutes(vararg points: Int) = listOf("0 min.", "0-5 min.", "5-10 min.", "11-15 min.", ">15 min.").zip(points.toList()).mapIndexed { i, (label, p) -> Option("$i", label, p) }
    val training = listOf(
        ChoiceItem("8a", "8a. Wenn Sie während der Sportausübung keine Schmerzen haben, wie lange können Sie trainieren?", minutes(0, 7, 14, 21, 30), showIf = Condition("8", setOf("a"))),
        ChoiceItem(
            "8b", "8b. Wenn Sie während der Sportausübung Schmerzen haben, die Sie aber nicht zum Abbrechen der Aktivität zwingen, wie lange können Sie trainieren?",
            minutes(0, 4, 10, 14, 20), showIf = Condition("8", setOf("b")),
        ),
        ChoiceItem(
            "8c", "8c. Wenn Sie während der Sportausübung Schmerzen haben, die Sie zum Abbrechen der Aktivität zwingen, wie lange können Sie bis zu dem Zeitpunkt noch trainieren?",
            minutes(0, 2, 5, 7, 10), showIf = Condition("8", setOf("c")),
        ),
    )
    val counted = listOf(sitting) + scales + sport + training
    Assessment(
        id = "visa-p-g",
        name = "Victorian Institute of Sport Assessment – Patella",
        abbreviation = "VISA-P-G",
        categories = listOf(AssessmentCategory.LowerExtremity),
        source = "VISA-P-G Score, Stand 11/2026",
        hint = "Validierte deutsche Version (Lohrer et al. 2011).\n\nSehr geehrter Patient, der folgende Fragebogen dient der " +
            "Erfassung der Beschwerden, die durch Ihre Patellasehne verursacht werden. Bitte beantworten Sie die Fragen in Bezug " +
            "zu Ihrem problematischen Knie. Kreuzen Sie bei jeder Frage das Kästchen, welches ihr momentanes Befinden am " +
            "deutlichsten wiedergibt, an.\n\n" +
            "Aus den einzelnen Antworten zugewiesenen Werten wird die Summe gebildet. Dabei sind maximal 100 Punkte möglich. " +
            "Dies würde einem vollkommen gesunden Patienten entsprechen. Der Score ist nicht zur Diagnose einer Tendinopathie " +
            "des Lig. patellae geeignet. Vielmehr ist es ein Instrument zur Feststellung des Schweregrades und der " +
            "Verlaufskontrolle bzw. Quantifizierung des Behandlungsergebnisses. Der MDC – Wert („minimal detectable change“ " +
            "oder minimal messbare Veränderung) beträgt 12,6 Punkte. Der MCID – Wert („minimum clinically important difference“ " +
            "oder minimal klinisch relevante Veränderung) für diesen Fragebogen beträgt >13 Punkte. Das heißt, eine klinisch " +
            "bedeutsame Veränderung erfährt der Patient ab einer Änderung von über 13 Punkten in diesem Score.",
        sections = listOf(Section(items = listOf(sitting) + scales + sport + which + training)),
        scores = listOf(
            // Questions 1 to 7 and the one of 8a, 8b and 8c shown.
            Score("score", "Score", ids(counted), direction = Direction.HigherIsBetter, minAnswered = counted.size - 2),
        ),
    )
}
