package de.emilyshub.ui.fields

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.wishes.GoalChoice
import de.emilyshub.wishes.Wishes

/**
 * The goal something is part of (SPEC.md §6), [none] for none: tapped, it offers each goal of the tree, indented below
 * its own, but [excluding] one and those below it, so that no goal can come to hang under itself.
 */
@Composable
fun GoalField(
    wishes: Wishes,
    selected: Long?,
    @StringRes label: Int,
    @StringRes none: Int,
    onChange: (Long?) -> Unit,
    excluding: Long? = null,
) {
    val choices by remember(wishes, excluding) { wishes.goalChoices(excluding) }.collectAsState(initial = null)
    var choosing by rememberSaveable { mutableStateOf(false) }
    val list = choices ?: return
    // One deleted meanwhile is chosen no more.
    val chosen = list.find { it.id == selected }
    LabelledValue(
        label = stringResource(label),
        value = chosen?.title ?: stringResource(none),
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.goal_choose), role = Role.Button) { choosing = true },
    )

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.goal_choose)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    val choose = { id: Long? ->
                        choosing = false
                        onChange(id)
                    }
                    ChoiceRow(stringResource(none), depth = 0, chosen = chosen == null, onClick = { choose(null) })
                    list.forEach { choice -> ChoiceRow(choice, chosen = choice == chosen, onClick = { choose(choice.id) }) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ChoiceRow(choice: GoalChoice, chosen: Boolean, onClick: () -> Unit) =
    ChoiceRow(choice.title, choice.depth, chosen, onClick)

/** One to choose, indented by its [depth], the deepest ones no further than four steps in, so that the title still fits. */
@Composable
private fun ChoiceRow(title: String, depth: Int, chosen: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .padding(start = (depth.coerceAtMost(MAX_INDENT) * INDENT).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = chosen, onClick = null)
        Text(title, modifier = Modifier.padding(start = 8.dp))
    }
}

private const val INDENT = 16
private const val MAX_INDENT = 4
