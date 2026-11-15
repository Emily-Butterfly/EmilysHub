package de.emilyshub.ui.languages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.languages.Language
import de.emilyshub.languages.Languages
import de.emilyshub.ui.ChoiceChip

/** The languages not archived, by name, as they change; empty until read. */
@Composable
fun rememberOfferedLanguages(languages: Languages): List<Language> {
    val all by remember(languages) { languages.languages() }.collectAsState(initial = emptyList())
    return all.map { it.language }.filter { !it.archived }
}

/**
 * "Erledigt durch Vokabeln" of a routine (SPEC.md §2, §15; #106): one of the languages not archived of [offered], or
 * "Nein" ([selected] none).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyField(offered: List<Language>, selected: Long?, onChange: (Long?) -> Unit) {
    if (offered.isEmpty() && selected == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.routine_vocabulary), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.routine_vocabulary_none), chosen = selected == null) { onChange(null) }
            offered.forEach { language -> ChoiceChip(language.name, chosen = selected == language.id) { onChange(language.id) } }
        }
        Text(stringResource(R.string.routine_vocabulary_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The Karten-Ziel of a goal (SPEC.md §6, §15; #106): one of the languages of [offered], or none ([language] none), and
 * how many of its cards are to be sure, as typed ([count]); [invalid] if that is no number from 1 to 100 000.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardGoalField(
    offered: List<Language>,
    language: Long?,
    count: String,
    invalid: Boolean,
    onLanguageChange: (Long?) -> Unit,
    onCountChange: (String) -> Unit,
) {
    if (offered.isEmpty() && language == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.wish_card_goal), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.wish_card_goal_none), chosen = language == null) { onLanguageChange(null) }
            offered.forEach { offer -> ChoiceChip(offer.name, chosen = language == offer.id) { onLanguageChange(offer.id) } }
        }
        if (language != null) {
            OutlinedTextField(
                value = count,
                onValueChange = onCountChange,
                label = { Text(stringResource(R.string.wish_card_goal_count)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = invalid,
                supportingText = if (invalid) ({ Text(stringResource(R.string.wish_card_goal_count_invalid)) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
