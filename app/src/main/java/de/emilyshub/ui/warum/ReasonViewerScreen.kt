package de.emilyshub.ui.warum

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.warum.Reason
import de.emilyshub.warum.Reasons

/**
 * The Gründe of Mein Warum opened large (SPEC.md §18), from the one tapped ([reasonId]): its text in large letters, in
 * the middle; swiping leads to the next in the board's order, and back to the one before. No buttons but the way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonViewerScreen(reasons: Reasons, reasonId: Long, onBack: () -> Unit) {
    val board by remember(reasons) { reasons.board() }.collectAsState(initial = null)
    val shown = board ?: return
    LaunchedEffect(shown.isEmpty()) { if (shown.isEmpty()) onBack() }
    if (shown.isEmpty()) return
    val start = remember { shown.indexOfFirst { it.id == reasonId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { shown.size }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        HorizontalPager(
            state = pager,
            key = { shown.getOrNull(it)?.id ?: it },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) { page ->
            val reason = shown.getOrNull(page) ?: return@HorizontalPager
            Large(reason, stringResource(R.string.warum_reason_of, page + 1, shown.size))
        }
    }
}

/** A reason as large as the screen lets it be, in its middle, its place among all ([place]) told to TalkBack. */
@Composable
private fun Large(reason: Reason, place: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .padding(24.dp)
                .semantics { stateDescription = place },
        ) {
            ReasonText(reason.text, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
