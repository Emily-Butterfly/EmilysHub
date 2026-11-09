package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/** The forms of DIGOTOR's subcategory "Wirbelsäule" (#67), each as in its PDF of the date in its source. */
internal val SPINE: List<Assessment> by lazy { listOf(CSIQ, HDI, LSIQ, MICHELI, NDI, ODI, RMDQ, VOMS) }

/** Cervical Spine Instability-Skala, as in "Cervical-Spine-Instability-Skala_CSIQ_11-2026.pdf". */
private val CSIQ: Assessment = run {
    val items = yesNoItems(
        "Ich kann keine langanhaltenden, statischen Positionen tolerieren.",
        "Es fällt mir schwer meinen Kopf zu halten.",
        "Passive Hilfen, wie z. B. eine Halskrause oder eine Orthese lindern meine Beschwerden.",
        "Ich muss mich häufig selbst „Einrenken“.",
        "Ich fühle, dass mein Nacken instabil ist.",
        "Ich habe regelmäßige Nackenschmerzepisoden.",
        "Mein Schmerz verstärkt sich manchmal bei schnellen, kleinen, unerwarteten Bewegungen.",
        "Mein Kopf fühlt sich schwer an.",
        "Mein Nacken blockiert bei Bewegung.",
        "Im Liegen habe ich weniger Beschwerden.",
        "Mein Nacken macht Geräusche („Knacken“).",
        "Ich habe in der Vergangenheit eine Nackenverletzung gehabt.",
        "Kleine Nackenbewegungen verursachen Beschwerden.",
        "Meine Muskeln fühlen sich steif und verspannt an.",
        "Ich habe Angst oder Unsicherheitsgefühle bei Nackenbewegungen.",
        "„Einrenken“ des Nackens lindert kurzfristig meine Symptome.",
    )
    Assessment(
        id = "csiq",
        name = "Cervical Spine Instability-Skala",
        abbreviation = "CSIQ",
        categories = listOf(AssessmentCategory.Spine),
        source = "CSIQ, Stand 11/2026",
        hint = "Nicht validierte deutsche Version.\n\nSehr geehrter Patient, der folgende Fragebogen dient der Erfassung von " +
            "Beschwerden und Problemen, die durch Ihren Nacken verursacht werden. Bitte identifizieren Sie alle genannten " +
            "Aktivitäten und Haltungen, die Ihre Nackenbeschwerden beeinflussen. Markieren Sie bitte alle zutreffenden " +
            "Faktoren mit „Ja“, nichtzutreffende Faktoren kennzeichnen Sie bitte mit „Nein“.\n\n" +
            "Je höher das Ergebnis, desto größer ist das subjektiv empfundene Instabilitätsgefühl (Cook et al. 2005). Der " +
            "Fragebogen wird kritisch diskutiert und dient nicht der Diagnose von einzelnen Krankheitsbildern. Er kann aber " +
            "für die Auswahl von Interventionen (aktive Stabilisation) verwendet werden.",
        sections = listOf(Section(items = items)),
        scores = listOf(ItemScore("gesamt", "Gesamt", items.map(Item::id), direction = Direction.LowerIsBetter)),
    )
}

/** The Headache Disability Index, German, as in "Headache-Disabilitiy-Index_HDI_deutsch_11-2026.pdf". */
private val HDI: Assessment = run {
    val answers = listOf(Option("ja", "ja", 4), Option("manchmal", "manchmal", 2), Option("nein", "nein", 0))
    // Each with its subscale as the original marks it: E emotional, F functional.
    val statements = listOf(
        'E' to "Aufgrund meiner Kopfschmerzen fühle ich mich beeinträchtigt.",
        'F' to "Aufgrund meiner Kopfschmerzen fühle ich mich eingeschränkt bei meinen alltäglichen Aktivitäten.",
        'E' to "Keiner versteht, wie sehr sich die Kopfschmerzen auf mein Leben auswirken.",
        'F' to "Ich habe aufgrund meiner Kopfschmerzen meine Freizeitaktivitäten (z.B. Sport, Hobbys) eingeschränkt.",
        'E' to "Meine Kopfschmerzen ärgern mich.",
        'E' to "Manchmal habe ich das Gefühl, dass ich wegen meiner Kopfschmerzen die Beherrschung verlieren könnte.",
        'F' to "Durch meine Kopfschmerzen habe ich weniger soziale Kontakte.",
        'E' to "Mein Partner oder meine Familie und Freunde haben keine Vorstellung, was ich wegen meiner Kopfschmerzen durchmache.",
        'E' to "Meine Kopfschmerzen sind so schlimm, dass ich das Gefühl habe, ich könnte verrückt werden.",
        'E' to "Meine Lebenseinstellung ist durch meine Kopfschmerzen beeinflusst.",
        'E' to "Ich habe Angst auszugehen, wenn ich Kopfschmerzen bekomme.",
        'E' to "Meine Kopfschmerzen bringen mich zur Verzweiflung.",
        'F' to "Ich mach mir Sorgen, dass meine Kopfschmerzen mir im Beruf oder zu Hause Nachteile bringen könnten.",
        'E' to "Meine Kopfschmerzen beeinträchtigen die Beziehung zu meiner Familie oder zu Freunden.",
        'F' to "Wenn ich Kopfschmerzen habe, ziehe ich mich zurück.",
        'F' to "Ich glaube, dass meine Kopfschmerzen es mir erschweren, gesteckte Ziele zu erreichen.",
        'F' to "Ich kann wegen meiner Kopfschmerzen nicht klar denken.",
        'F' to "Meine Kopfschmerzen führen zu Verspannungen (z.B. Muskelverspannungen).",
        'F' to "Ich habe an sozialen Aktivitäten aufgrund meiner Kopfschmerzen keine Freude.",
        'E' to "Ich fühle mich wegen meiner Kopfschmerzen leicht reizbar.",
        'F' to "Wegen meiner Kopfschmerzen vermeide ich Reisen.",
        'E' to "Meine Kopfschmerzen bringen mich durcheinander.",
        'E' to "Meine Kopfschmerzen frustrieren mich.",
        'F' to "Wegen meiner Kopfschmerzen fällt mir das Lesen schwer.",
        'F' to "Es fällt mir schwer, mich von meinen Kopfschmerzen abzulenken.",
    )
    val items = statements.mapIndexed { i, (scale, text) -> ChoiceItem("${i + 1}", "$scale ${i + 1}. $text", answers) }
    val of = { scale: Char -> statements.indices.filter { statements[it].first == scale }.map { "${it + 1}" } }
    Assessment(
        id = "hdi",
        name = "Headache Disability Index",
        abbreviation = "HDI",
        categories = listOf(AssessmentCategory.Spine),
        source = "HDI deutsch, Stand 11/2026",
        hint = "Bitte kreuzen Sie Zutreffendes an: ja (4 Punkte), manchmal (2 Punkte), nein (0 Punkte). " +
            "E kennzeichnet die emotionalen, F die funktionellen Aussagen.\n\n" +
            "Eine Veränderung des Gesamtscores um mindestens 29 Punkte (95 % Konfidenzintervall) zwischen zwei " +
            "Messzeitpunkten ist erforderlich, um diese Veränderung sicher als Effekt der Behandlung zu werten und nicht als " +
            "zufällige Schwankung.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            ItemScore(
                id = "gesamt",
                name = "Gesamtpunktzahl",
                items = items.map(Item::id),
                direction = Direction.LowerIsBetter,
                // Below 10 the original names nothing; 70 lies between its 68 and 72, and counts as severe (08.11.2026).
                bands = listOf(
                    Band.upTo(8, null),
                    Band.upTo(28, "Leichte Beeinträchtigung"),
                    Band.upTo(48, "Moderate Beeinträchtigung"),
                    Band.upTo(70, "Schwere Beeinträchtigung"),
                    Band.upTo(100, "Vollständige Beeinträchtigung"),
                ),
            ),
            // The original marks them but scores only the total; asked for as subscales on 08.11.2026.
            ItemScore("emotional", "Emotional (E)", of('E'), direction = Direction.LowerIsBetter),
            ItemScore("funktional", "Funktional (F)", of('F'), direction = Direction.LowerIsBetter),
        ),
    )
}

/** Lumbar Spine Instability Questionnaire, as in "Lumbar-Spine-Instability-Questionaire_LSIQ_11-2026.pdf". */
private val LSIQ: Assessment = run {
    val items = yesNoItems(
        "Ich fühle, dass mein Rücken nachgibt.",
        "Ich fühle, dass ich meinen Rücken regelmäßig „knacken lassen muss“, um den Schmerz zu reduzieren.",
        "Mein Schmerz tritt häufig über den Tag verteilt auf.",
        "In der Vergangenheit blockierte mein Rücken bei Drehbewegungen der Wirbelsäule.",
        "Beim Aufstehen oder Hinsetzen von einem Stuhl habe ich Schmerzen.",
        "Ich habe starke Schmerzen, wenn ich mich nicht auf die korrekte Weise vom Liegen in den Sitz bewege.",
        "Mein Schmerz verstärkt sich manchmal bei schnellen, kleinen, unerwarteten Bewegungen.",
        "Beim Sitzen ohne eine Rückenlehne habe ich Beschwerden. Ich fühle mich besser mit einer unterstützenden Lehne.",
        "Ich kann keine langanhaltenden, statischen Positionen tolerieren.",
        "Es hat den Anschein, dass meine Beschwerden in der letzten Zeit schlechter werden.",
        "Ich habe die Beschwerden schon sehr lange.",
        "Manchmal habe ich eine Beschwerdelinderung, wenn ich ein Korsett oder eine Orthese benutze.",
        "Meine (Rücken-) Muskeln verspannen sich häufig.",
        "Aufgrund des Schmerzes habe ich manchmal Angst mich zu bewegen.",
        "Ich habe in der Vergangenheit eine Rückenverletzung gehabt.",
    )
    Assessment(
        id = "lsiq",
        name = "Lumbar Spine Instability Questionnaire",
        abbreviation = "LSIQ",
        categories = listOf(AssessmentCategory.Spine),
        source = "LSIQ, Stand 11/2026",
        hint = "Nicht validierte deutsche Version.\n\nSehr geehrter Patient, sehr geehrte Patientin, der folgende Fragebogen " +
            "dient der Erfassung von Beschwerden und Problemen, die durch Ihren Rücken verursacht werden. Bitte identifizieren " +
            "Sie alle genannten Aktivitäten und Haltungen, die Ihre Rückenbeschwerden beeinflussen. Markieren Sie bitte alle " +
            "zutreffenden Faktoren mit „Ja“, nichtzutreffende Faktoren kennzeichnen Sie bitte mit „Nein“.\n\n" +
            "Je höher das Ergebnis, desto größer ist das subjektiv empfundene Instabilitätsgefühl (Cook et al. 2006). Der " +
            "Fragebogen wird kritisch diskutiert und dient nicht der Diagnose von einzelnen Krankheitsbildern. Dennoch kann " +
            "er für die Auswahl von Interventionen (aktive Stabilisation) verwendet werden (Saragiotto et al. 2018).",
        sections = listOf(Section(items = items)),
        scores = listOf(
            ItemScore(
                id = "gesamt",
                name = "Gesamt",
                items = items.map(Item::id),
                direction = Direction.LowerIsBetter,
                bands = listOf(
                    Band.upTo(8, null),
                    Band.upTo(15, "typisch für Patienten, die besser auf motorische Kontrollübungen, bzw. koordinatives Training ansprechen"),
                ),
            ),
        ),
    )
}

/** Micheli Functional Scale, as in "Micheli-Functional-Scale_09-2026.pdf". */
private val MICHELI: Assessment = run {
    // Six answers from 0 to 5, as the original scores Teil A; "Schmerz, aber kein Einfluss" as 1 (08.11.2026).
    val a1 = ChoiceItem(
        "a1", "1. In welchem Ausmaß beeinflusst der Schmerz Ihre sportliche Aktivität?",
        numbered(
            "Ich habe keinen Schmerz.",
            "Ich habe Schmerz, aber er hat keinen Einfluss.",
            "Der Schmerz hat nur einen geringen/minimalen Einfluss.",
            "Der Schmerz hat einen moderaten Einfluss.",
            "Der Schmerz hat einen starken Einfluss.",
            "Ich kann bedingt durch den Schmerz keinen Sport ausüben.",
        ),
    )
    val b1 = ChoiceItem(
        "b1", "1. In welchem Ausmaß ist Ihr Schmerz mit Streckbewegungen der Wirbelsäule oder Aktivitäten in aufrechter Haltung verbunden?",
        numbered(
            "Ich kann mit maximaler Geschwindigkeit rennen und ohne Einschränkung die Wirbelsäule strecken.",
            "Ich kann mit maximaler Geschwindigkeit rennen aber die Streckung der Wirbelsäule schmerzt etwas.",
            "Rennen und Streckbewegungen der Wirbelsäule verursachen Schmerz.",
            "Ich kann die Wirbelsäule nicht strecken.",
            "Ich kann weder rennen noch die Wirbelsäule strecken.",
        ),
    )
    val b2 = ChoiceItem(
        "b2", "2. In welchem Ausmaß ist Ihr Schmerz mit Beugebewegungen der Wirbelsäule oder Aktivitäten in gebeugter Haltung verbunden?",
        numbered(
            "Ich kann ohne Einschränkung sitzen und die Wirbelsäule beugen.",
            "Ich kann sitzen aber die Beugung der Wirbelsäule schmerzt etwas.",
            "Sitzen und beugen der Wirbelsäule verursacht etwas Schmerz.",
            "Ich kann weder Sitzen noch belastete Beugebewegungen der Wirbelsäule ausführen.",
        ),
    )
    val b3 = ChoiceItem(
        "b3", "3. In welchem Ausmaß ist Ihr Schmerz mit Sprungbelastungen verbunden?",
        numbered(
            "Ich kann ohne Schmerz springen.",
            "Springen verursacht etwas Schmerz.",
            "Springen verursacht starke Schmerzen.",
            "Ich kann bedingt durch meinen Schmerz nicht springen.",
        ),
    )
    // The line of the original in whole centimetres (08.11.2026).
    val c1 = ScaleItem(
        "c1",
        "1. Das linke Ende der Linie repräsentiert KEINEN Schmerz. Das rechte Ende der Linie repräsentiert einen starken " +
            "Schmerz, der es ihnen nicht ermöglicht zu arbeiten oder zur Schule zu gehen. Bitte ordnen Sie Ihre momentane " +
            "Schmerzerfahrung ein!",
        0, 10, "kein Schmerz", "maximaler Schmerz",
    )
    Assessment(
        id = "micheli",
        name = "Micheli Functional Scale",
        abbreviation = "Micheli",
        categories = listOf(AssessmentCategory.Spine, AssessmentCategory.PainPsychosocial),
        source = "Micheli Functional Scale, Stand 09/2026",
        hint = "Nicht validierte deutsche Version.\n\nDieser Fragebogen untersucht Ihren Rückenschmerz und die daraus " +
            "resultierenden funktionellen Einschränkungen. Bitte kreuzen Sie für jede Frage EINE für Sie passende Antwort an.",
        sections = listOf(
            Section("Teil A: Symptome", items = listOf(a1)),
            Section("Teil B: Alltagsaktivitäten", items = listOf(b1, b2, b3)),
            Section("Teil C: Schmerzskala", items = listOf(c1)),
        ),
        // Of 25 points, times 4: the percent of the most possible.
        scores = listOf(ItemScore("gesamt", "Gesamtscore", listOf("a1", "b1", "b2", "b3", "c1"), rule = ScoreRule.Percent, unit = "%", direction = Direction.LowerIsBetter)),
    )
}

/** Neck Disability Index, validated German version, as in "Neck-Disability-Index_09-2026.pdf". */
private val NDI: Assessment = run {
    val sections = listOf(
        "Schmerzintensität" to numbered(
            "Momentan habe ich keine Schmerzen.",
            "Ich habe im Moment sehr geringe Schmerzen.",
            "Ich habe im Moment mäßige Schmerzen.",
            "Ich habe im Moment ziemlich starke Schmerzen.",
            "Ich habe im Moment sehr starke Schmerzen.",
            "Ich habe im Moment die stärksten Schmerzen, die ich mir vorstellen kann.",
        ),
        "Persönliche Körperpflege (z.B. Waschen, Anziehen)" to numbered(
            "Ich kann meine Körperpflege erledigen, ohne dass dies zusätzliche Schmerzen verursacht.",
            "Ich kann meine Körperpflege erledigen, aber es verursacht mir zusätzliche Schmerzen.",
            "Das Erledigen der Körperpflege ist schmerzhaft und ich bin dabei langsam und vorsichtig.",
            "Ich brauche etwas Hilfe, aber ich kann den größten Teil meiner Körperpflege selbst besorgen.",
            "Ich brauche täglich Hilfe bei den meisten Verrichtungen meiner Körperpflege.",
            "Ich ziehe mich nicht an, wasche mich nur mit Mühe und bleibe im Bett.",
        ),
        "Heben" to numbered(
            "Ich kann schwere Gegenstände ohne zusätzliche Schmerzen heben.",
            "Ich kann schwere Gegenstände heben, aber dies verursacht zusätzliche Schmerzen.",
            "Meine Schmerzen hindern mich daran, schwere Gegenstände vom Boden aufzuheben. Aber ich kann schwere Gegenstände " +
                "heben, wenn sie günstig positioniert sind (z.B. auf dem Tisch).",
            "Meine Schmerzen hindern mich daran, schwere Gegenstände vom Boden aufzuheben. Aber ich kann mittelschwere " +
                "Gegenstände heben, wenn sie günstig positioniert sind.",
            "Ich kann nur sehr leichte Gegenstände heben.",
            "Ich kann überhaupt nichts heben oder tragen.",
        ),
        "Lesen" to numbered(
            "Ich kann lesen soviel ich will, ohne Nackenschmerzen zu bekommen.",
            "Ich kann lesen soviel ich will, aber ich bekomme davon leichte Nackenschmerzen.",
            "Ich kann lesen soviel ich will, aber ich bekomme davon mäßige Nackenschmerzen.",
            "Ich kann wegen mäßiger Nackenschmerzen nicht soviel lesen, wie ich will.",
            "Ich kann wegen starker Nackenschmerzen kaum lesen.",
            "Ich kann überhaupt nicht mehr lesen.",
        ),
        "Kopfschmerzen" to numbered(
            "Ich habe überhaupt keine Kopfschmerzen.",
            "Ich habe leichte Kopfschmerzen, die unregelmäßig auftreten.",
            "Ich habe mäßige Kopfschmerzen, die unregelmäßig auftreten.",
            "Ich habe mäßige Kopfschmerzen, die regelmäßig auftreten.",
            "Ich habe starke Kopfschmerzen, die regelmäßig auftreten.",
            "Ich habe die meiste Zeit Kopfschmerzen.",
        ),
        "Konzentration" to numbered(
            "Ich kann mich, wenn ich will, ohne Schwierigkeiten voll konzentrieren.",
            "Ich kann mich, wenn ich will, mit leichten Schwierigkeiten voll konzentrieren.",
            "Ich habe ziemliche Schwierigkeiten, mich zu konzentrieren, wenn ich es will.",
            "Ich habe große Schwierigkeiten, mich zu konzentrieren, wenn ich es will.",
            "Ich habe sehr große Schwierigkeiten, mich zu konzentrieren, wenn ich es will.",
            "Ich kann mich überhaupt nicht konzentrieren.",
        ),
        "Arbeit" to numbered(
            "Ich kann soviel Arbeit erledigen, wie ich möchte.",
            "Ich kann nur meine übliche Arbeit erledigen, aber nicht mehr.",
            "Ich kann den größten Teil meiner üblichen Arbeit verrichten, aber nicht mehr.",
            "Ich kann meine übliche Arbeit nicht erledigen.",
            "Ich kann kaum eine Arbeit erledigen.",
            "Ich kann überhaupt keine Arbeit erledigen.",
        ),
        "Auto fahren" to numbered(
            "Ich kann Auto fahren, ohne Nackenschmerzen zu bekommen.",
            "Ich kann Auto fahren, so lange ich will, mit leichten Nackenschmerzen.",
            "Ich kann Auto fahren, so lange ich will, mit mäßigen Nackenschmerzen.",
            "Ich kann wegen mäßiger Nackenschmerzen nicht Auto fahren, so lange ich will.",
            "Ich kann wegen starker Nackenschmerzen kaum Auto fahren.",
            "Ich kann überhaupt nicht Auto fahren.",
        ),
        "Schlafen" to numbered(
            "Ich habe keine Schlafprobleme.",
            "Mein Schlaf ist kaum gestört (weniger als 1 Stunde schlaflos).",
            "Mein Schlaf ist leicht gestört (1-2 Stunden schlaflos).",
            "Mein Schlaf ist mäßig gestört (2-3 Stunden schlaflos).",
            "Mein Schlaf ist stark gestört (3-5 Stunden schlaflos).",
            "Mein Schlaf ist komplett gestört (5-7 Stunden schlaflos).",
        ),
        "Freizeitaktivität (FA) - Erholung" to numbered(
            "Ich kann alle meine Freizeitaktivitäten ohne Nackenschmerzen ausüben.",
            "Ich kann, wenn auch mit einigen Nackenschmerzen, alle meine Freizeitaktivitäten ausüben.",
            "Wegen Nackenschmerzen kann ich die meisten, aber nicht alle meiner täglichen Freizeitaktivitäten ausüben.",
            "Ich kann wegen meiner Nackenschmerzen nur einige meiner Freizeitaktivitäten ausüben.",
            "Ich kann wegen meiner Nackenschmerzen kaum irgendwelche Freizeitaktivitäten ausüben.",
            "Ich kann überhaupt keine Freizeitaktivitäten ausüben.",
        ),
    )
    val items = sections.mapIndexed { i, (title, options) -> ChoiceItem("${i + 1}", "${i + 1}. $title", options) }
    Assessment(
        id = "ndi",
        name = "Neck Disability Index",
        abbreviation = "NDI",
        categories = listOf(AssessmentCategory.Spine),
        source = "Neck Disability Index, Stand 09/2026",
        hint = "Validierte deutsche Version (Cramer 2014).\n\nDer folgende Fragebogen dient der Erfassung von Beschwerden und " +
            "Problemen bei alltäglichen Aktivitäten, die durch Ihre Halswirbelsäule verursacht werden. Bitte beantworten Sie " +
            "alle Fragen gemäß Ihrem aktuellen Zustand. Sollten Sie momentan keine Beschwerden haben, dann bewerten Sie die " +
            "Fragen entsprechend Ihrem Zustand in der vergangenen Woche. Sollten zwei Aussagen auf Ihren aktuellen Zustand zu " +
            "treffen, kreuzen Sie bitte nur die Aussage an, die am ehesten zu trifft.\n\n" +
            "Die minimal messbare Veränderung beträgt 5 Punkte oder 10 % der Gesamtpunktzahl (Vernon et al. 1991). Die " +
            "minimal klinisch relevante Veränderung wird in Studien mit unterschiedlichen Werten angegeben und liegt zwischen " +
            "3,5-9,5 (Schellingerhout et al. 2012). Nach Jorritsma et al. (2012) sollte die Veränderung 8,4 Punkte für ein " +
            "klinisch relevantes Ergebnis betragen.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            ItemScore(
                id = "score",
                name = "Score im NDI",
                items = items.map(Item::id),
                rule = ScoreRule.Percent,
                unit = "%",
                direction = Direction.LowerIsBetter,
                bands = listOf(
                    Band.upTo(8, "Patienten mit einer geringen Symptomatik"),
                    Band.upTo(40, null),
                    Band.upTo(100, "Patienten mit einer sehr schweren Symptomatik, der auch eine Chronifizierung begünstigt"),
                ),
                // One section left out lowers the most possible; with two the result is "–" (08.11.2026).
                minAnswered = items.size - 1,
            ),
        ),
    )
}

/** Oswestry Disability Index, German version (Mannion et al. 2006), as in "Oswestry-Disability-Index_ODI_11-2026.pdf". */
private val ODI: Assessment = run {
    val sections = listOf(
        "Schmerzstärke" to numbered(
            "Ich habe momentan keine Schmerzen.",
            "Die Schmerzen sind momentan sehr schwach.",
            "Die Schmerzen sind momentan mäßig.",
            "Die Schmerzen sind momentan ziemlich stark.",
            "Die Schmerzen sind momentan sehr stark.",
            "Die Schmerzen sind momentan so schlimm wie nur vorstellbar.",
        ),
        "Körperpflege (Waschen, Anziehen, etc.)" to numbered(
            "Ich kann meine Körperpflege normal durchführen, ohne dass die Schmerzen dadurch stärker werden.",
            "Ich kann meine Körperpflege normal durchführen, aber es ist schmerzhaft.",
            "Meine Körperpflege normal durchzuführen ist schmerzhaft und ich bin langsam und vorsichtig.",
            "Ich brauche bei der Körperpflege etwas Hilfe, bewältige das meiste aber selbst.",
            "Ich brauche täglich Hilfe bei den meisten Aspekten der Körperpflege.",
            "Ich kann mich nicht selbst anziehen, wasche mich mit Mühe und bleibe im Bett.",
        ),
        "Heben" to numbered(
            "Ich kann schwere Gegenstände heben, ohne dass die Schmerzen dadurch stärker werden.",
            "Ich kann schwere Gegenstände heben, aber die Schmerzen werden dadurch stärker.",
            "Schmerzen hindern mich daran, schwere Gegenstände vom Boden zu heben, aber es geht, wenn sie geeignet stehen (z.B. auf einem Tisch).",
            "Schmerzen hindern mich daran, schwere Gegenstände zu heben, aber ich kann leichte bis mittelschwere Gegenstände heben, wenn sie geeignet stehen.",
            "Ich kann nur sehr leichte Gegenstände heben.",
            "Ich kann überhaupt nichts heben oder tragen.",
        ),
        "Gehen" to numbered(
            "Schmerzen hindern mich nicht daran, so weit zu gehen, wie ich möchte.",
            "Schmerzen hindern mich daran, mehr als 1-2 km zu gehen.",
            "Schmerzen hindern mich daran, mehr als 0,5 km zu gehen.",
            "Schmerzen hindern mich daran, mehr als 100 m zu gehen.",
            "Ich kann nur mit einem Stock oder Krücken gehen.",
            "Ich bin die meiste Zeit im Bett und muss mich zur Toilette schleppen.",
        ),
        "Sitzen" to numbered(
            "Ich kann auf jedem Stuhl so lange sitzen, wie ich möchte.",
            "Ich kann auf meinem Lieblingsstuhl so lange sitzen, wie ich möchte.",
            "Schmerzen hindern mich daran, länger als 1 Stunde zu sitzen.",
            "Schmerzen hindern mich daran, länger als eine halbe Stunde zu sitzen.",
            "Schmerzen hindern mich daran, länger als 10 Minuten zu sitzen.",
            "Schmerzen hindern mich daran, überhaupt zu sitzen.",
        ),
        "Stehen" to numbered(
            "Ich kann so lange stehen, wie ich möchte, ohne dass die Schmerzen dadurch stärker werden.",
            "Ich kann so lange stehen, wie ich möchte, aber die Schmerzen werden dadurch stärker.",
            "Schmerzen hindern mich daran, länger als 1 Stunde zu stehen.",
            "Schmerzen hindern mich daran, länger als eine halbe Stunde zu stehen.",
            "Schmerzen hindern mich daran, länger als 10 Minuten zu stehen.",
            "Schmerzen hindern mich daran, überhaupt zu stehen.",
        ),
        "Schlafen" to numbered(
            "Mein Schlaf ist nie durch Schmerzen gestört.",
            "Mein Schlaf ist gelegentlich durch Schmerzen gestört.",
            "Ich schlafe auf Grund von Schmerzen weniger als 6 Stunden.",
            "Ich schlafe auf Grund von Schmerzen weniger als 4 Stunden.",
            "Ich schlafe auf Grund von Schmerzen weniger als 2 Stunden.",
            "Schmerzen hindern mich daran, überhaupt zu schlafen.",
        ),
        "Sexualleben" to numbered(
            "Mein Sexualleben ist normal und die Schmerzen werden dadurch nicht stärker.",
            "Mein Sexualleben ist normal, aber die Schmerzen werden dadurch stärker.",
            "Mein Sexualleben ist nahezu normal, aber sehr schmerzhaft.",
            "Mein Sexualleben ist durch Schmerzen stark eingeschränkt.",
            "Ich habe auf Grund von Schmerzen fast kein Sexualleben.",
            "Schmerzen verhindern jegliches Sexualleben.",
        ),
        "Sozialleben" to numbered(
            "Mein Sozialleben ist normal und die Schmerzen werden durch nicht stärker.",
            "Mein Sozialleben ist normal, aber die Schmerzen werden dadurch stärker.",
            "Schmerzen haben keinen wesentlichen Einfluss auf mein Sozialleben, außer dass sie meine eher aktiven Interessen, z.B. Sport, einschränken.",
            "Schmerzen schränken mein Sozialleben ein und ich gehe nicht mehr so oft aus.",
            "Schmerzen schränken mein Sozialleben auf mein Zuhause ein.",
            "Ich habe auf Grund von Schmerzen kein Sozialleben.",
        ),
        "Reisen" to numbered(
            "Ich kann überallhin reisen und die Schmerzen werden dadurch nicht stärker.",
            "Ich kann überallhin reisen, aber die Schmerzen werden dadurch stärker.",
            "Trotz starker Schmerzen kann ich länger als 2 Stunden unterwegs sein.",
            "Ich kann auf Grund von Schmerzen höchstens 1 Stunde unterwegs sein.",
            "Ich kann auf Grund von Schmerzen nur kurze notwendige Fahrten unter 30 Minuten machen.",
            "Schmerzen hindern mich daran, Fahrten zu machen, außer zur medizinischen Behandlung.",
        ),
    )
    val items = sections.mapIndexed { i, (title, options) -> ChoiceItem("${i + 1}", title, options) }
    Assessment(
        id = "odi",
        name = "Oswestry Disability Index",
        abbreviation = "ODI",
        categories = listOf(AssessmentCategory.Spine),
        source = "ODI, Stand 11/2026",
        hint = "Deutsche Version (Mannion et al. 2006).\n\nBitte füllen Sie diesen Fragebogen aus. Er soll uns darüber " +
            "informieren, wie Ihre Rücken- (oder Bein-) Probleme Ihre Fähigkeit beeinflussen, den Alltag zu bewältigen. Wir " +
            "bitten Sie, jeden Abschnitt zu beantworten. Kreuzen Sie in jedem Abschnitt nur die Aussage an, die sie heute am " +
            "besten beschreibt.\n\nMDC (95 %): 9 Punkte (Mannion 2006), 13,47 Punkte (Monticone 2012), 11,75 (Johnsen 2013). " +
            "MCID: 5-17 Punkte, 30-50 % Reduktion (Schwind 2013); 12,8 Punkte (Park 2016, Johnsen 2013).",
        sections = listOf(Section(items = items)),
        scores = listOf(
            ItemScore(
                id = "gesamt",
                name = "Gesamtwert",
                items = items.map(Item::id),
                rule = ScoreRule.Percent,
                unit = "%",
                direction = Direction.LowerIsBetter,
                // Fairbank (2000); a value between two of its whole bounds, as from a section left out, counts to the upper one.
                bands = listOf(
                    Band.upTo(20, "minimale Funktionseinschränkung"),
                    Band.upTo(40, "moderate Funktionseinschränkung"),
                    Band.upTo(60, "starke Funktionseinschränkung"),
                    Band.upTo(80, "sehr starke Funktionseinschränkung"),
                    Band.upTo(100, "pflegebedürftig (bettlägerig) oder psychosozial extrem überlagert"),
                ),
                // One section left out lowers the most possible; with two the result is "–" (08.11.2026).
                minAnswered = items.size - 1,
            ),
        ),
    )
}

/** Roland and Morris Disability Questionnaire, German version, as in "Roland-and-Morris-Disability-Questionnaire_RMDQ_11-2026.pdf". */
private val RMDQ: Assessment = run {
    val statements = listOf(
        "Wegen meiner Rückenschmerzen bleibe ich den größten Teil des Tages zu Hause.",
        "Ich wechsle häufig meine Körperhaltung, um meinen Rücken zu entlasten.",
        "Ich gehe wegen meiner Rückenschmerzen langsamer als sonst.",
        "Wegen meiner Rückenschmerzen erledige ich keine der Arbeiten, die ich sonst im Haushalt erledige.",
        "Wegen meiner Rückenschmerzen halte ich mich beim Treppensteigen am Geländer fest.",
        "Wegen meiner Rückenschmerzen lege ich mich häufiger als sonst zum Ausruhen hin.",
        "Wegen meiner Rückenschmerzen muss ich mich an etwas abstützen, um aus einem Polstersessel hochzukommen.",
        "Wegen meiner Rückenschmerzen bitte ich andere Menschen, etwas für mich zu erledigen.",
        "Wegen meiner Rückenschmerzen brauche ich zum Ankleiden länger als sonst.",
        "Wegen meiner Rückenschmerzen achte ich darauf, nur kurze Zeit zu stehen.",
        "Wegen meiner Rückenschmerzen achte ich darauf, mich so wenig wie möglich zu bücken oder niederzuknien.",
        "Wegen meiner Rückenschmerzen fällt es mir schwer, von einem Stuhl aufzustehen.",
        "Ich leide den größten Teil des Tages/der Nacht unter Rückenschmerzen.",
        "Meine Rückenschmerzen erschweren mir das Umdrehen im Bett.",
        "Wegen meiner Rückenschmerzen ist mein Appetit nicht besonders gut.",
        "Wegen meiner Rückenschmerzen habe ich Probleme beim Anziehen von Socken (oder Strümpfen/Strumpfhosen).",
        "Wegen meiner Rückenschmerzen gehe ich nur kurze Strecken.",
        "Wegen meiner Rückenschmerzen schlafe ich weniger als sonst.",
        "Wegen meiner Rückenschmerzen brauche ich beim Ankleiden Hilfe.",
        "Wegen meiner Rückenschmerzen verbringe ich den größten Teil des Tages sitzend.",
        "Wegen meiner Rückenschmerzen versuche ich, schwere Arbeiten im Haushalt zu vermeiden.",
        "Wegen meiner Rückenschmerzen bin ich reizbarer und übellauniger als sonst.",
        "Wegen meiner Rückenschmerzen gehe ich Treppen langsamer hinauf als sonst.",
        "Wegen meiner Rückenschmerzen verbringe ich den größten Teil des Tages im Bett.",
    )
    val list = ChecklistItem(
        "saetze",
        "Beschreibung der Tätigkeit",
        statements.mapIndexed { i, text -> Option("${i + 1}", "${i + 1}. $text", 1) },
        scored = true,
    )
    Assessment(
        id = "rmdq",
        name = "Roland and Morris Disability Questionnaire",
        abbreviation = "RMDQ",
        categories = listOf(AssessmentCategory.Spine),
        source = "RMDQ, Stand 11/2026",
        hint = "Deutsche Version.\n\nSehr geehrter Patient, wenn Sie Rückenschmerzen haben, sind alltägliche Dinge oft schwer " +
            "zu erledigen. Diese Liste enthält Sätze, die Personen verwendet haben, um ihre Rückenschmerzen zu beschreiben. " +
            "Wenn Sie die Liste durchlesen, fällt Ihnen auf, dass der eine oder andere Satz Ihre Beschwerden von heute am " +
            "besten beschreibt. Denken Sie bitte nur an die Beschwerden von heute. Trifft ein Satz für Ihre Beschwerden von " +
            "heute zu, kreuzen Sie diesen Satz an. Falls ein Satz die Beschwerden nicht beschreibt, kreuzen Sie diesen Satz " +
            "nicht an und gehen Sie zum nächsten Satz.\n\n" +
            "Die minimale klinisch relevante Veränderung wird von diversen Autoren unterschiedlich angegeben und beträgt " +
            "zwischen 2-8 Punkten. Es wird daher vorgeschlagen diese Grenze abhängig vom Behinderungsgrad zu bestimmen: Bei " +
            "Patienten mit geringer Behinderung (<9): 1-2. Bei Patienten mit großer Behinderung (>16): 8. Bei nicht genauer " +
            "bestimmten Patienten: 5. Bei Werten unter 4 bzw. über 20 sind nur geringe Veränderungen zu erwarten und der " +
            "Fragebogen ist entweder nicht aussagekräftig bzw. die minimal klinisch relevante Veränderung übersteigt den " +
            "Ausgangswert.",
        sections = listOf(Section(items = listOf(list))),
        scores = listOf(ItemScore("score", "Score", listOf(list.id), direction = Direction.LowerIsBetter)),
    )
}

/** Vestibular/Ocular-Motor Screening, as in "Vestibular-Ocular-Motor-Screening_VOMS_11-2026-1.pdf": an examination, without a result. */
private val VOMS: Assessment = run {
    val symptoms = listOf("kopf" to "Kopf-/Nackenschmerz", "schwindel" to "Schwindel", "uebelkeit" to "Übelkeit", "benommenheit" to "Benommenheit")
    fun test(id: String, title: String, hint: String, extra: List<Item> = emptyList()) = Section(
        title = title,
        hint = hint,
        items = symptoms.map { (symptom, label) -> ScaleItem("$id-$symptom", label, 0, 10) } + extra + TextItem("$id-anmerkungen", "Anmerkungen"),
    )
    val distance = (1..3).map { NumberItem("konvergenz-messung-$it", "Messung $it", unit = "cm", decimals = true) }
    Assessment(
        id = "voms",
        name = "Vestibular-Ocular-Motor Screening",
        abbreviation = "VOMS",
        categories = listOf(AssessmentCategory.Spine),
        source = "VOMS, Stand 11/2026",
        hint = "Das Vestibular/Ocular-Motor Screening ist ein kurzes, reliables und einfach durchzuführendes Assessment der " +
            "University of Pittsburgh Medical Center (UPMC). Es wurde entwickelt, um Defizite des vestibulookulären Systems bei " +
            "Athleten nach sportbezogener Gehirnerschütterung (Concussion) zu testen. Das VOMS stellt dabei eine Ergänzung zur " +
            "umfangreichen Testung nach Gehirnerschütterung dar. Auffälligkeiten bedürfen weiterer ärztlicher Abklärung. Der " +
            "Test kann als Basisuntersuchung vor der Saison genutzt werden, um direkt nach der nächsten Gehirnerschütterung " +
            "einen Vergleich ziehen zu können.\n\nInterpretation: Dieser Test wurde für Personen im Alter von 9–40 Jahren " +
            "entwickelt. Für Personen außerhalb dieser Altersspanne kann die Interpretation variieren. Abnormale Befunde und " +
            "Provokation von Symptomen bei einem der Tests deuten auf eine Dysfunktion hin.\n\nBenötigtes Material: Maßband, " +
            "Metronom (z.B. als App), Ziel: Durchführung mit einer Schriftgröße von ca. 14.\n\nNach jedem einzelnen Test werden " +
            "die Symptome (Skala 0–10) eingetragen.",
        sections = listOf(
            test(
                "vor", "Symptome vor Testung",
                "Symptome wie Kopf-/Nackenschmerz (Nackenschmerzen werden in der Originalversion nicht erwähnt), Schwindel, Übelkeit und Benommenheit.",
            ),
            test(
                "smooth", "1. Smooth Pursuits (langsame Blickfolge)",
                "Dieser Test wird zuerst horizontal und dann vertikal durchgeführt. Ausgangsstellung: Der Patient und der " +
                    "Untersucher sitzen sich gegenüber. Der Untersucher hält das Ziel (z.B. Stift) ca. 90cm vom Patient entfernt. " +
                    "Das Ziel bleibt mit den Augen fokussiert. Aktion: Das Ziel wird von der Mitte aus ca. 45cm nach rechts und " +
                    "links und nach oben und unten bewegt. Der Patient soll dem Ziel mit den Augen folgen, ohne den Kopf zu bewegen. " +
                    "Geschwindigkeit: ca. 2 Sekunden von der Mitte aus. Beide Richtungen werden 2x ausgeführt.",
            ),
            test(
                "sakkaden-h", "2. Sakkaden (schnelle Blickfolge) – horizontal",
                "Dieser Test wird zuerst horizontal und dann vertikal durchgeführt. " +
                    "Ausgangsstellung: Der Patient und der Untersucher sitzen sich gegenüber. Der Untersucher hält 2 Ziele (z.B. " +
                    "Stifte) ca. 90cm vom Patient entfernt. Zur Mitte haben diese einen Abstand von ca. 45cm. Aktion: Der Patient " +
                    "schaut nur mit seinen Augen so schnell wie möglich von Ziel zu Ziel, ohne den Kopf zu bewegen. Eine " +
                    "Wiederholung gilt vom Startpunkt aus (rechts-links-rechts, bzw. oben-unten-oben). Der Patient führt 10 " +
                    "Wiederholungen durch.",
            ),
            test("sakkaden-v", "2. Sakkaden (schnelle Blickfolge) – vertikal", "Dieser Test wird zuerst horizontal und dann vertikal durchgeführt."),
            test(
                "konvergenz", "3. Konvergenz (Doppelbild bei Nähe)",
                "Ausgangsstellung: Der Patient sitzt (falls notwendig mit Korrekturlinsen). Der Therapeut sitzt, die " +
                    "Augenbewegung beobachtend. Der Patient hält das Ziel (z.B ein Stift) in Armlänge vor seinen Augen. Aktion: " +
                    "Der Patient führt langsam das Ziel zu seiner Nase. Der Patient stoppt, sobald er anfängt, das Ziel als " +
                    "Doppelbild zu sehen. Der Therapeut stoppt, sobald er eine Deviation eines Auges nach außen feststellt. " +
                    "Unschärfe beim Nahsehen wird ignoriert. Der Abstand zwischen Ziel und Nase beim Stopp wird in Zentimetern " +
                    "gemessen und notiert. Der Test wird 3x wiederholt. Abweichung: ≥ 6cm von der Nasenspitze entfernt.",
                extra = distance,
            ),
            test(
                "vor-h", "4. Vestibulookulärer Reflex (Blickstabilisierung bei Kopfbewegung) – horizontal",
                "Dieser Test wird zuerst horizontal und dann vertikal durchgeführt. " +
                    "Ausgangsstellung: Der Patient und der Untersucher sitzen sich gegenüber. Der Untersucher hält das Ziel ca. " +
                    "90cm vom Patient entfernt. Das Ziel (z.B. Stift) wird mit den Augen fokussiert. Aktion: Der Patient bewegt " +
                    "den Kopf aus der Mitte um 20° zuerst nach rechts und links (horizontal) und dann nach oben und unten " +
                    "(vertikal), während er das Ziel weiterhin mit den Augen fokussiert. Bewegungsgeschwindigkeit: 180 Schläge " +
                    "pro Minute (Metronom). Eine Wiederholung gilt vom Startpunkt aus. Der Patient führt 10 Wiederholungen durch.",
            ),
            test("vor-v", "4. Vestibulookulärer Reflex (Blickstabilisierung bei Kopfbewegung) – vertikal", "Dieser Test wird zuerst horizontal und dann vertikal durchgeführt."),
            test(
                "vms", "5. Visual Motion Sensitivity",
                "Ausgangsstellung: Der Patient steht (Füße schulterbreit) mit Blick zu einem belebten Teil der Klinik. Der " +
                    "Untersucher steht zur Sicherheit schräg hinter dem Patienten, sodass dieser sich aber frei bewegen kann. Der " +
                    "Patient streckt die Arme aus und schaut auf seine Daumen. Die Arme werden auf Augenhöhe gehalten. Die Daumen " +
                    "werden mit den Augen fokussiert. Aktion: Der Patient rotiert den gesamten Oberkörper (als Einheit) um 80° von " +
                    "rechts nach links, während er die Daumen mit den Augen fokussiert. Es gibt 3 Positionen, die eingenommen " +
                    "werden (rechts-Mitte-links). Bewegungsgeschwindigkeit: 50 Schläge pro Minute (Metronom). Pro Schlag eine " +
                    "Bewegung. Der Patient führt 5 komplette Wiederholungen durch. Eine Wiederholung ist eine komplette Bewegung " +
                    "(Mitte-rechts-Mitte-links-Mitte).",
            ),
        ),
    )
}
