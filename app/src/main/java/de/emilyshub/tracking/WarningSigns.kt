package de.emilyshub.tracking

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
