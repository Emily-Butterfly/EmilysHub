package de.emilyshub.tracking

/**
 * A primary source behind what Tracking says (SPEC.md §17, "Quellen der Schwellen"), by the [code] the spec and the
 * screen cite it with. Each slice adds those its rules rest on.
 */
enum class Source(val code: String, val citation: String) {
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
    Hrs2015(
        "HRS-2015",
        "Sheldon RS et al.: 2015 Heart Rhythm Society Expert Consensus Statement on the Diagnosis and Treatment of " +
            "Postural Tachycardia Syndrome, Inappropriate Sinus Tachycardia, and Vasovagal Syncope. " +
            "Heart Rhythm 2015; 12: e41–e63. doi:10.1016/j.hrthm.2015.03.029",
    ),
}

/** How soon the [WarningSigns] call for help. */
enum class Urgency {
    /** At once, by calling 112. */
    Emergency,

    /** Soon, by a doctor. */
    Soon,
}

/** Signs that call for help with this [urgency], whatever Tracking has recorded, after these [sources]. */
data class WarningSigns(val urgency: Urgency, val signs: List<String>, val sources: List<Source>)

/**
 * The warning signs under "Akute Beschwerden?" at the top of Tracking's start page (SPEC.md §17, "Hinweise"): what calls
 * for 112 at once, then what calls for a doctor soon. Not a diagnosis; they hold for everyone, whatever was recorded.
 */
val ACUTE_WARNING_SIGNS: List<WarningSigns> = listOf(
    WarningSigns(
        Urgency.Emergency,
        listOf(
            "plötzliche oder rasch zunehmende Atemnot",
            "Brustschmerz",
            "Ohnmacht oder Beinahe-Ohnmacht",
            "Bluthusten",
            "Ohnmacht bei Belastung oder im Liegen",
            "Herzrasen vor einer Ohnmacht",
            "plötzlicher Sehverlust",
            "Lähmung, Sprach- oder Gefühlsstörung",
            "Krampfanfall",
        ),
        listOf(Source.EscPulmonaryEmbolism2019, Source.EscSyncope2018),
    ),
    WarningSigns(
        Urgency.Soon,
        listOf(
            "einseitige Beinschwellung oder -schmerz",
            "fortschreitende Atemnot",
            "neue anhaltende Kopfschmerzen oder Sehstörungen",
            "Gelbfärbung von Haut oder Augen",
            "Farbveränderungen oder Wunden an Fingern oder Zehen",
            "Hautzeichnung, die bleibt",
            "Ruhepuls dauerhaft über 100/min",
        ),
        listOf(Source.Hrs2015),
    ),
)
