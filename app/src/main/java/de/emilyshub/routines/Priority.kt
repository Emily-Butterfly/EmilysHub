package de.emilyshub.routines

/** How much a routine matters (SPEC.md §2): shown with it in the Routinen list and on Heute. */
enum class Priority(val stored: String) {
    Low("low"),
    Normal("normal"),
    High("high"),
}
