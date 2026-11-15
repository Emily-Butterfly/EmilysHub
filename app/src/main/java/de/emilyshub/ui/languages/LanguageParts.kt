package de.emilyshub.ui.languages

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.languages.Periods
import de.emilyshub.languages.Tally
import de.emilyshub.ledger.Money
import de.emilyshub.ui.fields.LengthHint

/** How long, in hours and minutes: "45 Min.", "2 Std.", "1 Std. 30 Min.". */
@Composable
internal fun durationText(minutes: Long): String = when {
    minutes < 60 -> stringResource(R.string.languages_minutes, minutes.toInt())
    minutes % 60 == 0L -> stringResource(R.string.languages_hours, (minutes / 60).toInt())
    else -> stringResource(R.string.languages_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
}

/** What [tally] comes to: "1 Std. 30 Min. · 2 Sessions", with what it cost where prices were given. */
@Composable
internal fun tallyText(tally: Tally): String {
    val duration = durationText(tally.minutes)
    val count = pluralStringResource(R.plurals.languages_lessons, tally.count, tally.count)
    return tally.costCents?.let { stringResource(R.string.languages_tally_cost, duration, count, Money.format(it)) }
        ?: stringResource(R.string.languages_tally, duration, count)
}

/** The month, the year and all, a line each: its name and what it comes to. */
@Composable
internal fun PeriodLines(periods: Periods) {
    Column {
        listOf(
            R.string.languages_this_month to periods.month,
            R.string.languages_this_year to periods.year,
            R.string.languages_total to periods.total,
        ).forEach { (label, tally) ->
            Text(
                "${stringResource(label)}: ${tallyText(tally)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * A name typed for something new or renamed: [initial] to start with, [problemOf] what keeps a name from being given,
 * told at once once something is typed; [onConfirm] with the name only without one.
 */
@Composable
internal fun NameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: String,
    max: Int,
    problemOf: @Composable (String) -> String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val problem = problemOf(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.language_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = problem != null && (tried || name.isNotEmpty()),
                supportingText = {
                    if (problem != null && (tried || name.isNotEmpty())) {
                        Text(problem)
                    } else {
                        LengthHint(InputText.lengthOf(InputText.singleLine(name)), max)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    tried = true
                    if (problem == null) onConfirm(name)
                },
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
