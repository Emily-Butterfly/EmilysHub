package de.emilyshub.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.ledger.LedgerMonth
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.OpeningBalance
import de.emilyshub.ui.monthAndYear
import de.emilyshub.ui.monthName

/**
 * The opening balance of a month (SPEC.md §13, #50) with where it comes from: carried over from the month before, or
 * set by hand. Tapping it changes it, unless the month is closed ([onEdit] null).
 */
@Composable
internal fun OpeningRow(opening: OpeningBalance, onEdit: (() -> Unit)?) {
    val label = stringResource(R.string.ledger_opening_edit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onEdit == null) Modifier else Modifier.clickable(onClickLabel = label, onClick = onEdit))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ledger_opening), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (opening.carried) {
                    stringResource(R.string.ledger_opening_carried, opening.month.minusMonths(1).monthName())
                } else {
                    stringResource(R.string.ledger_opening_by_hand)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money.format(opening.cents), style = MaterialTheme.typography.titleMedium)
        if (onEdit != null) {
            Icon(Icons.Filled.Edit, contentDescription = label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** What a month with an opening balance ends with. */
@Composable
internal fun ClosingRow(cents: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.ledger_closing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(Money.format(cents), style = MaterialTheme.typography.titleMedium)
    }
}

/** An open month without an opening balance: the end balance of the month before carried over, or one set by hand. */
@Composable
internal fun OpeningOffers(onCarry: () -> Unit, onSet: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        OutlinedButton(onClick = onCarry) { Text(stringResource(R.string.ledger_opening_carry)) }
        OutlinedButton(onClick = onSet) { Text(stringResource(R.string.ledger_opening_set)) }
    }
}

/**
 * Sets the opening balance to an amount typed, zero or below too ([onSave]); one there already ([current]) can also be
 * removed ([onRemove]).
 */
@Composable
internal fun OpeningDialog(current: Long?, onSave: (Long) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf(current?.let(Money::typed).orEmpty()) }
    val cents = Money.parseSigned(typed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_opening)) },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.ledger_opening_amount)) },
                placeholder = { Text(stringResource(R.string.ledger_opening_amount_hint)) },
                isError = typed.isNotBlank() && cents == null,
                supportingText = if (typed.isNotBlank() && cents == null) {
                    { Text(stringResource(R.string.ledger_opening_amount_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            // Unchanged, one carried over stays carried over.
            TextButton(
                onClick = { if (cents == current) onDismiss() else cents?.let(onSave) },
                enabled = cents != null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.ledger_opening_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Asks before carrying over the end balance of the month before while that is still open, as it may yet change. */
@Composable
internal fun CarryDialog(shown: LedgerMonth, onCarry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_carry_title)) },
        text = {
            Text(
                stringResource(
                    R.string.ledger_carry_open_text,
                    shown.previous.month.monthAndYear(),
                    Money.format(shown.previous.endBalance),
                ),
            )
        },
        confirmButton = { TextButton(onClick = onCarry) { Text(stringResource(R.string.ledger_carry_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Asks before closing a month (SPEC.md §13): only closing it, or closing it and carrying its end balance over into the
 * next month, saying which one replaces, if that has one; not into a closed one, which it says.
 */
@Composable
internal fun CloseDialog(shown: LedgerMonth, onClose: (carryOver: Boolean) -> Unit, onDismiss: () -> Unit) {
    val next = shown.next
    val end = Money.format(shown.endBalance)
    val nextName = next.month.monthAndYear()
    val carrying = when {
        next.closed -> stringResource(R.string.ledger_close_next_closed, nextName)
        next.opening != null ->
            stringResource(R.string.ledger_close_carry_replaces, end, nextName, Money.format(next.opening.cents))
        else -> stringResource(R.string.ledger_close_carry_text, end, nextName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_close_title, shown.month.monthAndYear())) },
        text = { Text(stringResource(R.string.ledger_close_text) + " " + carrying) },
        // Three, one above the other: their names are too long to stand side by side.
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (!next.closed) {
                    TextButton(onClick = { onClose(true) }) { Text(stringResource(R.string.ledger_close_carry)) }
                }
                TextButton(onClick = { onClose(false) }) { Text(stringResource(R.string.ledger_close_only)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
