package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/** Patient Health Questionnaire 9, as in DIGOTOR's "Patient-Health-Questionnaire-9_PHQ-9_10-2026.pdf". */
internal val PHQ_9: Assessment = run {
    val answers = listOf(
        Option("0", "Überhaupt nicht", 0),
        Option("1", "An einzelnen Tagen", 1),
        Option("2", "An mehr als der Hälfte der Tage", 2),
        Option("3", "Beinahe jeden Tag", 3),
    )
    val questions = listOf(
        "Wenig Interesse oder Freude an Ihren Tätigkeiten",
        "Niedergeschlagenheit, Schwermut oder Hoffnungslosigkeit",
        "Schwierigkeiten ein- oder durchzuschlafen oder vermehrter Schlaf",
        "Müdigkeit oder Gefühl, keine Energie zu haben",
        "Verminderter Appetit oder übermäßiges Bedürfnis zu essen",
        "Schlechte Meinung von sich selbst; Gefühl, ein Versager zu sein oder die Familie enttäuscht zu haben",
        "Schwierigkeiten, sich auf etwas zu konzentrieren, z.B. beim Zeitunglesen oder Fernsehen",
        "Waren Ihre Bewegungen oder Ihre Sprache so verlangsamt, dass es auch anderen auffallen würde? Oder waren Sie im " +
            "Gegenteil „zappelig“ oder ruhelos und hatten dadurch einen stärkeren Bewegungsdrang als sonst?",
        "Gedanken, dass Sie lieber tot wären oder sich Leid zufügen möchten",
    )
    val items = questions.mapIndexed { i, text -> ChoiceItem("${i + 1}", "${i + 1}. $text", answers) }
    Assessment(
        id = "phq-9",
        name = "Patient Health Questionnaire 9",
        abbreviation = "PHQ-9",
        categories = listOf(AssessmentCategory.JawCmd, AssessmentCategory.PainPsychosocial),
        source = "PHQ-9, Stand 10/2026",
        hint = "Wie oft fühlten Sie sich im Verlauf der letzten 2 Wochen durch die folgenden Beschwerden beeinträchtigt?\n\n" +
            "Diagnosestellung: Da sich der Fragebogen ausschließlich auf Selbstauskünfte des Patienten bezieht, müssen die " +
            "endgültigen Diagnosen durch den behandelnden Arzt überprüft werden. Dabei sollte berücksichtigt werden, wie gut " +
            "der Patient den Fragebogen verstanden hat. Zusätzlich sind weitere anamnestische Informationen des Patienten, " +
            "seiner Familienangehörigen bzw. Informationen aus anderen Quellen heranzuziehen.",
        sections = listOf(Section(items = items)),
        scores = listOf(
            ItemScore(
                id = "summe",
                name = "Summenwert",
                items = items.map(Item::id),
                direction = Direction.LowerIsBetter,
                bands = listOf(
                    Band.upTo(4, "gesund"),
                    Band.upTo(9, "unauffällig"),
                    Band.upTo(14, "„leichtgradige“ Depression"),
                    Band.upTo(19, "„mittelgradige“ Depression"),
                    Band.upTo(27, "„schwergradige“ Depression"),
                ),
            ),
        ),
    )
}
