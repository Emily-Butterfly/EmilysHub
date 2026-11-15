package de.emilyshub.tracking

/**
 * A primary source behind what Tracking says (SPEC.md §17, "Quellen der Schwellen"), by the [code] the spec and the
 * screen cite it with. Each slice adds those its rules rest on.
 */
enum class Source(val code: String, val citation: String) {
    CmajPots2022(
        "CMAJ-POTS-2022",
        "Raj SR, Fedorowski A, Sheldon RS: Diagnosis and management of postural orthostatic tachycardia syndrome. " +
            "CMAJ 2022; 194: E378–E385. doi:10.1503/cmaj.211373",
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
    Lee2020(
        "LEE-2020",
        "Lee J et al.: Hemodynamics during the 10-minute NASA Lean Test: evidence of circulatory decompensation in a " +
            "subset of ME/CFS patients. J Transl Med 2020; 18: 314. doi:10.1186/s12967-020-02481-y",
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
}
