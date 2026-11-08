package de.emilyshub.doku.assessments

/**
 * DIGOTOR's Anamnesebogen Version 8, as in "Anamnesebogen_Version-8_07-2026.pdf": all questions of both pages, without
 * a result. Question 1 asks by body regions in place of a drawing; name, age, date and signature are the patient's and
 * the appointment's already.
 */
internal val ANAMNESIS_V8: Assessment = run {
    val no = Option("nein", "nein", exclusive = true)
    fun options(vararg labels: Pair<String, String>) = labels.map { (id, label) -> Option(id, label) }
    val reliefOrStrain = options(
        "aktivitaet" to "Aktivität", "ruhe" to "Ruhe", "liegen" to "Liegen", "sitzen" to "Sitzen", "aufstehen" to "Aufstehen",
        "buecken" to "Bücken", "hocke" to "in die Hocke gehen", "stehen" to "Stehen", "gehen" to "Gehen", "laufen" to "Laufen",
        "treppe" to "Treppe hoch-/runtergehen", "anziehen" to "An-/Ausziehen", "tragen" to "Tragen", "ueberkopf" to "Überkopfbewegung",
        "greifen" to "Greifen", "arbeit" to "Arbeit", "hobby" to "Hobby", "sport" to "Sport", "morgens" to "morgens",
        "abends" to "abends", "nachts" to "nachts",
    )
    val pain = "kein Schmerz" to "maximaler Schmerz"
    val stress = "kein Stress" to "maximaler Stress"
    fun scale(id: String, text: String, ends: Pair<String, String>) = ScaleItem(id, text, 0, 10, ends.first, ends.second)

    Assessment(
        id = "anamnese-v8",
        name = "Anamnesebogen Version 8",
        abbreviation = "Anamnese",
        categories = listOf(AssessmentCategory.Anamnesis),
        source = "Anamnesebogen Version 8, Stand 07/2026",
        hint = "Sehr geehrte/-r Patient/-in, es ist unser Ziel, Sie optimal zu behandeln. Dazu benötigen wir vorab ein paar " +
            "Informationen zu Ihrer Person und zu Ihren Beschwerden. Die Fragen beziehen sich dabei auf Ihr Hauptproblem. " +
            "Alle Informationen unterliegen der Schweigepflicht und werden nicht weitergegeben. Vielen Dank!",
        sections = listOf(
            Section(items = listOf(TextItem("beruf", "Beruf"), TextItem("sport", "Sport/Hobby"))),
            Section(
                items = listOf(
                    BodyRegionsItem("1", "1.) Wo haben Sie Ihre Beschwerden?"),
                    YesNoItem("2", "2.) Haben Sie aktuell Schmerzen?"),
                    scale("3", "3.) Wie stark sind Ihre Schmerzen?", pain),
                    ChecklistItem(
                        "4", "4.) Haben Sie: Ruheschmerzen, Nachtschmerzen, Dauerschmerzen?",
                        options("ruhe" to "Ruheschmerzen", "nacht" to "Nachtschmerzen", "dauer" to "Dauerschmerzen") + no,
                    ),
                    YesNoItem("5", "5.) Ist Ihre Beweglichkeit verändert?"),
                    YesNoItem("6", "6.) Ist Ihre Kraft vermindert?"),
                    ChecklistItem(
                        "7", "7.) Weisen Ihre Beschwerden eines der folgenden Merkmale auf?",
                        options(
                            "kribbeln" to "Kribbeln", "piksen" to "Piksen", "brennen" to "Brennen", "juckreiz" to "Juckreiz",
                            "taubheit" to "Taubheitsgefühl", "kaelte" to "Gefühl einer schmerzhaften Kälte", "schlaege" to "elektrische Schläge",
                        ) + no,
                    ),
                    TextItem("8", "8.) Haben Sie noch weitere Beschwerden?"),
                    TextItem("9", "9.) Seit wann haben Sie Ihre Beschwerden?"),
                    ChoiceItem("10", "10.) Haben Sie Ihre Beschwerden:", options("permanent" to "permanent", "unterbrochen" to "mit Unterbrechungen")),
                    ChoiceItem(
                        "11", "11.) Sind Ihre Beschwerden:",
                        options("besser" to "besser geworden", "gleich" to "gleichbleibend", "schlechter" to "schlechter geworden", "wechselnd" to "wechselnd"),
                    ),
                    ChecklistItem("12a", "12.) a) Was verbessert Ihre Beschwerden?", reliefOrStrain, otherLabel = "Sonstiges"),
                    ChecklistItem("12b", "12.) b) Was verschlechtert Ihre Beschwerden?", reliefOrStrain, otherLabel = "Sonstiges"),
                ),
            ),
            Section(
                hint = "Zur Beantwortung der Fragen 13-20 denken Sie bitte nur an die vergangenen zwei Wochen:",
                items = listOf(
                    scale("13", "13.) Wie stark waren Ihre Schmerzen im Durchschnitt?", pain),
                    YesNoItem("14", "14.) Sind Sie oft unsicher, wie Sie mit Ihrem Schmerzzustand umgehen sollen?"),
                    YesNoItem("15", "15.) Haben Sie sich in den vergangenen zwei Wochen stark durch Ihre Schmerzen gestört gefühlt?"),
                    YesNoItem("16", "16.) Konnten Sie aufgrund Ihrer Schmerzen nur kurze Wege gehen?"),
                    YesNoItem("17", "17.) Hatten Sie störende Gelenk- oder Muskelschmerzen an mehr als einem Körperteil?"),
                    YesNoItem("18", "18.) Denken Sie, dass Ihr Zustand lange anhalten wird?"),
                    YesNoItem("19", "19.) Haben Sie andere bedeutsame Gesundheitsprobleme?"),
                    YesNoItem("20", "20.) Haben Sie sich aufgrund Ihrer Schmerzen/Beschwerden niedergeschlagen oder depressiv gefühlt?"),
                ),
            ),
            Section(
                items = listOf(
                    YesNoItem("21", "21.) Denken Sie, dass es für jemanden in Ihrem Zustand schädlich ist, körperlich aktiv zu sein?"),
                    YesNoItem("22", "22.) Haben Sie Ihre derzeitige Schmerzproblematik seit 6 Monaten oder länger?"),
                    ChecklistItem(
                        "23", "23.) Hatten Sie in den letzten Wochen:",
                        options(
                            "kopfschmerzen" to "Kopfschmerzen", "gang" to "Gang- oder Gleichgewichtsstörungen", "schwindel" to "Schwindel",
                            "uebelkeit" to "Übelkeit", "ohnmacht" to "Ohnmachtsanfällen", "benommenheit" to "Benommenheit",
                            "sprechen" to "Sprech-/Schluckbeschwerden", "sehen" to "Sehprobleme/Doppelbilder",
                        ) + no,
                    ),
                    ChecklistItem(
                        "24", "24.) Hatten Sie in den letzten Woche:",
                        options(
                            "verdauung" to "Verdauungsbeschwerden/Sodbrennen", "blase" to "Blasenschwäche", "steifheit" to "morgendliche Steifheit",
                            "atem" to "Kurzatmigkeit", "muedigkeit" to "vermehrte Müdigkeit", "herzrasen" to "Herzrasen",
                            "brust" to "belastungsabhängige Brustschmerzen", "schweiss" to "Schweißausbrüche",
                        ) + no,
                    ),
                    ChecklistItem(
                        "25", "25.) Hatten Sie in den letzten Wochen:",
                        options(
                            "fieber" to "Fieber/Schüttelfrost", "nachtschweiss" to "Nachtschweiß", "gewicht" to "Gewichtsverlust",
                            "infekt" to "Infektanfälligkeit",
                        ) + no,
                    ),
                    YesNoItem("26", "26.) Hatten Sie jemals einen Tumor oder eine Krebserkrankung?"),
                    TextItem("26-welche", "Welche/n?", showIf = Condition.ifYes("26")),
                    TextItem("26-wann", "Wann?", showIf = Condition.ifYes("26")),
                    TextItem("26-nachuntersuchung", "Letzte Nachuntersuchung?", showIf = Condition.ifYes("26")),
                    ChecklistItem(
                        "27", "27.) Leiden Sie unter:",
                        options(
                            "asthma" to "Asthma", "diabetes" to "Diabetes", "osteoporose" to "Osteoporose", "epilepsie" to "Epilepsie",
                            "depression" to "Depression", "angst" to "Angststörung",
                        ) + no,
                        otherLabel = "andere Erkrankung",
                    ),
                    ChecklistItem(
                        "28", "28.) Stehen folgende Ereignisse in Zusammenhang mit Ihren aktuellen Problemen:",
                        options("unfall" to "Unfall/Sturz/Verletzung", "operation" to "Operation") + no,
                        otherLabel = "anderes Ereignis",
                    ),
                    ChecklistItem(
                        "29", "29.) Befinden Sie sich in den Wechseljahren? Besteht die Wahrscheinlichkeit einer Schwangerschaft?",
                        options("wechseljahre" to "Wechseljahre", "schwangerschaft" to "Wahrscheinlichkeit einer Schwangerschaft") + no,
                    ),
                    ChecklistItem(
                        "30", "30.) Nehmen Sie momentan Medikamente wie:",
                        options(
                            "kortison" to "Kortison", "blutverduenner" to "Blutverdünner", "schmerzmittel" to "Schmerzmittel",
                            "hormone" to "Hormone", "antidepressiva" to "Antidepressiva",
                        ) + no,
                        otherLabel = "andere Medikamente",
                    ),
                    YesNoItem("31", "31.) Rauchen Sie?"),
                    YesNoItem("32", "32.) Würden Sie Ihre Ernährung als gut bzw. ausgewogen bezeichnen?"),
                    YesNoItem(
                        "33",
                        "33.) Bewegen Sie sich pro Woche 150 Minuten bei niederer Intensität (Herzfrequenz erhöht, Reden ist noch " +
                            "möglich) oder 75 Minuten bei höherer Intensität (Herzfrequenz erhöht, Reden wird unmöglich)?",
                    ),
                    YesNoItem("34", "34.) Machen Sie 2 x pro Woche Krafttraining?"),
                    YesNoItem("35", "35.) Haben Sie Schwierigkeiten einzuschlafen oder durchzuschlafen?"),
                    scale("36", "36.) Wie gestresst fühlen Sie sich bei Ihrer Arbeit?", stress),
                    scale("37", "37.) Wie gestresst fühlen Sie sich im Alltag?", stress),
                    ChecklistItem(
                        "38", "38.) Welche Maßnahmen zur Diagnostik oder Therapie wurden bisher durchgeführt:",
                        options(
                            "roentgen" to "Röntgen", "ct" to "Computertomographie", "mrt" to "MRT/Kernspintomographie", "spritze" to "Spritze",
                            "massage" to "Massage", "physiotherapie" to "Physiotherapie", "training" to "Training",
                        ),
                        otherLabel = "Anderes",
                    ),
                    TextItem("39", "39.) Was sind Ihre Erwartungen und Ziele für die Therapie?"),
                ),
            ),
            Section(items = listOf(TextItem("therapeut", "Anmerkungen Therapeut"))),
        ),
    )
}
