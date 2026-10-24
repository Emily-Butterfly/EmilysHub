package de.emilyshub.routines

/** How much a routine or a to-do matters (SPEC.md §2, §5): shown with it in its list and on Heute. */
enum class Priority(val stored: String) {
    Low("low"),
    Normal("normal"),
    High("high"),
}
