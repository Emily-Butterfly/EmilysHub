package de.emilyshub.tracking

/**
 * A primary source behind what Tracking says (SPEC.md §17, "Quellen der Schwellen"), by the [code] the spec and the
 * screen cite it with. Each slice adds those its rules rest on.
 */
enum class Source(val code: String, val citation: String) {
    Funcap(
        "FUNCAP",
        "Entwicklung und Prüfung von FUNCAP, J Clin Med 2024; 13: 3486. doi:10.3390/jcm13123486; " +
            "deutsche Fassung und Lizenz (CC BY 4.0): funcap.no",
    ),
    CmajPots2022(
        "CMAJ-POTS-2022",
        "Raj SR, Fedorowski A, Sheldon RS: Diagnosis and management of postural orthostatic tachycardia syndrome. " +
            "CMAJ 2022; 194: E378–E385. doi:10.1503/cmaj.211373",
    ),
    DegamFatigue(
        "DEGAM-MUEDIGKEIT",
        "DEGAM: S3-Leitlinie Müdigkeit, AWMF-Register 053-002, Stand 12/2022",
    ),
    DiazBuschmann2014(
        "DIAZ-BUSCHMANN-2014",
        "Díaz-Buschmann I et al.: Programming exercise intensity in patients on beta-blocker treatment. " +
            "Eur J Prev Cardiol 2014. doi:10.1177/2047487313500214",
    ),
    DgnSyncope2020(
        "DGN-SYNK-2020",
        "Diehl RR et al.: Synkopen, S1-Leitlinie der DGN, 2020, AWMF-Register 030/072",
    ),
    EscPulmonaryEmbolism2019(
        "ESC-LE-2019",
        "Konstantinides SV et al.: 2019 ESC Guidelines for the diagnosis and management of acute pulmonary embolism. " +
            "Eur Heart J 2020; 41: 543–603. doi:10.1093/eurheartj/ehz405",
    ),
    EscSyncope2018(
        "ESC-SYNK-2018",
        "Brignole M et al.: 2018 ESC Guidelines for the diagnosis and management of syncope. " +
            "Eur Heart J 2018; 39: 1883–1948. doi:10.1093/eurheartj/ehy037",
    ),
    GarminHeartRate(
        "GARMIN-HF",
        "Gillinov et al.: Variable accuracy of wearable heart rate monitors during aerobic exercise. Med Sci Sports Exerc " +
            "2017 (PubMed 28709155); Merrigan et al. 2023, doi:10.1080/1091367X.2022.2161820 (Brustgurt gegen EKG); " +
            "Casanova-Lizón et al. 2026, doi:10.3390/s26206549 (Ruhe)",
    ),
    GarminHrv(
        "GARMIN-HRV",
        "Dial et al. 2025, doi:10.14814/phy2.70527 (Nacht-RMSSD gegen EKG); Miller et al. 2022, doi:10.3390/s22166317",
    ),
    GarminSleep(
        "GARMIN-SCHLAF",
        "Schyvens et al. 2025, doi:10.1093/sleepadvances/zpaf021 (gegen Polysomnographie)",
    ),
    GarminSteps(
        "GARMIN-SCHRITTE",
        "Evenson KR, Spade CL: Review of validity and reliability of Garmin activity trackers. J Meas Phys Behav 2020. " +
            "doi:10.1123/jmpb.2019-0035",
    ),
    Iom2015(
        "IOM-2015",
        "Institute of Medicine: Beyond Myalgic Encephalomyelitis/Chronic Fatigue Syndrome: Redefining an Illness. " +
            "National Academies Press 2015. doi:10.17226/19012",
    ),
    Keller2024(
        "KELLER-2024",
        "Keller et al.: Zwei-Tages-Spiroergometrie bei ME/CFS. J Transl Med 2024. doi:10.1186/s12967-024-05410-5",
    ),
    Lee2020(
        "LEE-2020",
        "Lee J et al.: Hemodynamics during the 10-minute NASA Lean Test: evidence of circulatory decompensation in a " +
            "subset of ME/CFS patients. J Transl Med 2020; 18: 314. doi:10.1186/s12967-020-02481-y",
    ),
    NiceNg206(
        "NICE-NG206",
        "NICE: Myalgic encephalomyelitis (or encephalopathy)/chronic fatigue syndrome: diagnosis and management, NG206, 2021",
    ),
    NihPots2021(
        "NIH-POTS-2021",
        "Vernino S et al.: Postural orthostatic tachycardia syndrome (POTS): State of the science and clinical care from " +
            "a 2019 National Institutes of Health Expert Consensus Meeting – Part 1. Auton Neurosci 2021; 235: 102828. " +
            "doi:10.1016/j.autneu.2021.102828",
    ),
    Hrs2015(
        "HRS-2015",
        "Sheldon RS et al.: 2015 Heart Rhythm Society Expert Consensus Statement on the Diagnosis and Treatment of " +
            "Postural Tachycardia Syndrome, Inappropriate Sinus Tachycardia, and Vasovagal Syncope. " +
            "Heart Rhythm 2015; 12: e41–e63. doi:10.1016/j.hrthm.2015.03.029",
    ),
    Sajjan2015(
        "SAJJAN-2015",
        "Sajjan VV et al.: Livedo reticularis: A review of the literature. Indian Dermatol Online J 2015; 6: 315–321. " +
            "doi:10.4103/2229-5178.164493",
    ),
    Workwell15(
        "WORKWELL-15",
        "Faustregel der Workwell Foundation (Ruhepuls + 15); nur eine Studie belegt, laut Erhebung in Work 2023, " +
            "doi:10.3233/WOR-220512 – Primärdokument offen",
    ),
}
