package de.emilyshub.ui.doku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.doku.ClinicalTest
import de.emilyshub.doku.Doku
import de.emilyshub.doku.TestCatalog
import kotlinx.coroutines.launch

/**
 * The Doku's tests (SPEC.md §14), by name, those archived below under "Archiviert", each with "Wiederherstellen";
 * "+ Test" adds one. A test tapped, or added, opens in its editor ([onOpen], with none for a new one).
 */
@Composable
internal fun TestsPage(doku: Doku, tests: TestCatalog, contentPadding: PaddingValues, onOpen: (Long?) -> Unit) {
    val list = rememberDokuLoad(doku) { tests.list() } ?: return
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp)) {
                Text(
                    stringResource(R.string.doku_tests_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(onClick = { onOpen(null) }) { Text(stringResource(R.string.doku_tests_add)) }
            }
        }
        if (list.active.isEmpty() && list.archived.isEmpty()) item { EmptyLine(stringResource(R.string.doku_tests_empty)) }
        items(list.active, key = { it.id }) { test -> TestLine(test, onClick = { onOpen(test.id) }) }
        if (list.archived.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.doku_tests_archived),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
            }
            items(list.archived, key = { it.id }) { test ->
                ListItem(
                    headlineContent = { Text(test.name) },
                    trailingContent = {
                        TextButton(onClick = { scope.launch { tests.restore(test.id) } }) { Text(stringResource(R.string.doku_test_restore)) }
                    },
                    modifier = Modifier.clickable { onOpen(test.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** A test in use, by its name, "mit Summe" if it is asked to form one. */
@Composable
private fun TestLine(test: ClinicalTest, onClick: () -> Unit) {
    PatientLine(name = test.name, detail = stringResource(R.string.doku_test_with_sum).takeIf { test.hasSum }, onClick = onClick)
}
