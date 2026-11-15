package de.emilyshub.ui.doku

import androidx.compose.runtime.Composable
import de.emilyshub.R
import de.emilyshub.doku.Doku
import de.emilyshub.ui.island.IslandMenu
import de.emilyshub.ui.island.IslandMenuItem

/**
 * The Doku's menu in the top bar, only while it is open (SPEC.md §14): its settings live here, not in the app's. For
 * the tests, the forms, the appointment template, the archive, "Doku-Passwort ändern" and the fingerprint on or off.
 */
@Composable
fun DokuMenu(doku: Doku, onOpen: (DokuPage) -> Unit) {
    IslandMenu(doku, DOKU_TEXTS, before = MENU_PAGES.map { (label, page) -> IslandMenuItem(label) { onOpen(page) } })
}

/** The pages the menu opens, by their label: the tests, the forms, the appointment template and the archive. */
private val MENU_PAGES = listOf(
    R.string.doku_menu_tests to DokuPage.Tests,
    R.string.doku_menu_assessments to DokuPage.Assessments,
    R.string.doku_menu_template to DokuPage.Template,
    R.string.doku_menu_archive to DokuPage.Archive,
)
