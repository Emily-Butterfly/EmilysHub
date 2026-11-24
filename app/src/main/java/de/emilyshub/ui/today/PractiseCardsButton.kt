package de.emilyshub.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.languages.Flashcards
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

/**
 * "Vokabeln üben" atop Heute (SPEC.md §1, §15; #158): there only while a language not archived has something open in
 * today's workload, as it changes, also at midnight. With one such language, a tap practises it ([onPractise]); with
 * more, a small menu offers them, the most open first, by name if alike, each with how much is open: "Arabisch (18)".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun PractiseCardsButton(flashcards: Flashcards, day: Day, onPractise: (languageId: Long) -> Unit) {
    val languages by remember(flashcards, day) { day.dates().flatMapLatest { flashcards.workloads.open(it) } }
        .collectAsState(initial = emptyList())
    if (languages.isEmpty()) return
    var choosing by rememberSaveable { mutableStateOf(false) }
    val single = languages.singleOrNull()
    // Down to one meanwhile, the menu closes, and does not come again by itself should another open later.
    if (single != null && choosing) SideEffect { choosing = false }
    Box {
        IconButton(onClick = { if (single != null) onPractise(single.id) else choosing = true }) {
            Icon(painterResource(R.drawable.ic_translate), contentDescription = stringResource(R.string.today_practise))
        }
        DropdownMenu(expanded = choosing && single == null, onDismissRequest = { choosing = false }) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.today_practise_language, language.name, language.open)) },
                    onClick = {
                        choosing = false
                        onPractise(language.id)
                    },
                )
            }
        }
    }
}
