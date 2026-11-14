package de.emilyshub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.stats.StreakRule

/**
 * "Ein Tag zählt für den Gesamt-Streak" (SPEC.md §8; #109): from a number of things done, 1 to 30, or from a share,
 * 10 % to 100 % in tens, as [rule] has it, with what it means under it. Changing it reckons the streaks anew ([onChange]).
 */
@Composable
internal fun StreakRuleSetting(rule: StreakRule, onChange: (StreakRule) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            stringResource(R.string.streak_rule_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            KindRow(R.string.streak_rule_count, selected = rule is StreakRule.Count) { onChange(StreakRule.DEFAULT) }
            KindRow(R.string.streak_rule_share, selected = rule is StreakRule.Share) { onChange(StreakRule.Share(StreakRule.SHARE_DEFAULT)) }
        }
        when (rule) {
            is StreakRule.Count -> {
                val counts = StreakRule.COUNTS
                Stepper(
                    value = pluralStringResource(R.plurals.streak_rule_count_value, rule.count, rule.count),
                    onLess = { onChange(StreakRule.Count(rule.count - 1)) }.takeIf { rule.count > counts.first },
                    onMore = { onChange(StreakRule.Count(rule.count + 1)) }.takeIf { rule.count < counts.last },
                )
                Hint(stringResource(R.string.streak_rule_count_hint))
            }
            is StreakRule.Share -> {
                val shares = StreakRule.SHARES
                val at = shares.indexOf(rule.percent)
                Stepper(
                    value = stringResource(R.string.streak_rule_share_value, rule.percent),
                    onLess = { onChange(StreakRule.Share(shares[at - 1])) }.takeIf { at > 0 },
                    onMore = { onChange(StreakRule.Share(shares[at + 1])) }.takeIf { at < shares.lastIndex },
                )
                Hint(stringResource(R.string.streak_rule_share_hint))
            }
        }
    }
}

/** One of the two kinds of rule, to choose. */
@Composable
private fun KindRow(label: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

/** The [value] chosen, with a step down ([onLess]) and up ([onMore]) where there is one. */
@Composable
private fun Stepper(value: String, onLess: (() -> Unit)?, onMore: (() -> Unit)?) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val less = stringResource(R.string.streak_rule_less)
        IconButton(onClick = { onLess?.invoke() }, enabled = onLess != null, modifier = Modifier.semantics { contentDescription = less }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        val more = stringResource(R.string.streak_rule_more)
        IconButton(onClick = { onMore?.invoke() }, enabled = onMore != null, modifier = Modifier.semantics { contentDescription = more }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
