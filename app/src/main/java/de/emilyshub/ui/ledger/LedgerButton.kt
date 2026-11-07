package de.emilyshub.ui.ledger

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.emilyshub.R

/**
 * The button that opens the household book from the notes (SPEC.md §1, §13), as wide as [modifier] makes it: it says
 * "Haushaltsbuch" in small type, or "€" where that does not fit, as with large type; to accessibility services it is
 * always "Haushaltsbuch öffnen".
 */
@Composable
fun LedgerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(R.string.ledger_open)
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.semantics { contentDescription = name },
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val label = stringResource(R.string.ledger_title)
            val style = MaterialTheme.typography.labelSmall
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val room = constraints.maxWidth
            val fits = remember(label, style, room, density) {
                measurer.measure(label, style, maxLines = 1, softWrap = false, density = density).size.width <= room
            }
            if (fits) {
                Text(label, style = style, maxLines = 1, softWrap = false)
            } else {
                Text(
                    stringResource(R.string.ledger_open_short),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * The small "€" atop Heute that opens the household book (SPEC.md §1, §13), as large as the icons beside it; to
 * accessibility services "Haushaltsbuch öffnen", as the notes' button.
 */
@Composable
fun LedgerIconButton(onClick: () -> Unit) {
    val name = stringResource(R.string.ledger_open)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = name }) {
        Text(stringResource(R.string.ledger_open_short), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
    }
}
