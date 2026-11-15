package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.BaseField
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingBaseData
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.ui.island.KeepIslandDraft

/**
 * The base data (SPEC.md §17, "Grunddaten"): a field in Markdown for each of [BaseField], with the questions to answer
 * there below it. Saved as the page closes and before Tracking locks.
 */
@Composable
internal fun BaseDataPage(tracking: Tracking, modules: TrackingModules, contentPadding: PaddingValues) {
    var loaded by remember { mutableStateOf<Map<BaseField, String>?>(null) }
    LaunchedEffect(Unit) { loaded = modules.baseData.all() }
    val original = loaded ?: return
    // Not saveable, as all that is typed in Tracking: nothing of it goes into the saved state.
    val texts = remember { mutableStateMapOf<BaseField, String>().apply { putAll(original) } }
    KeepIslandDraft(tracking, TrackingPage.BaseData, modules.writes) {
        val changed = BaseField.entries.filter { texts[it] != original[it] }.associateWith { texts[it].orEmpty() }
        if (changed.isEmpty()) null else ({ database -> changed.forEach { (field, text) -> modules.baseData.write(database, field, text) } })
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BaseField.entries.forEach { field ->
            val (label, questions) = field.texts()
            OutlinedTextField(
                value = texts[field].orEmpty(),
                onValueChange = { typed -> if (typed.length <= TrackingBaseData.TEXT_MAX) texts[field] = typed },
                label = { Text(stringResource(label)) },
                // Below the field, not as its placeholder, which shows only while it has the focus.
                supportingText = { Text(stringResource(questions)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** [this] field's label and the questions to answer there. */
private fun BaseField.texts(): Pair<Int, Int> = when (this) {
    BaseField.Course -> R.string.tracking_base_course to R.string.tracking_base_course_hint
    BaseField.Family -> R.string.tracking_base_family to R.string.tracking_base_family_hint
    BaseField.Sleep -> R.string.tracking_base_sleep to R.string.tracking_base_sleep_hint
    BaseField.Nutrition -> R.string.tracking_base_nutrition to R.string.tracking_base_nutrition_hint
    BaseField.Other -> R.string.tracking_base_other to R.string.tracking_base_other_hint
}
