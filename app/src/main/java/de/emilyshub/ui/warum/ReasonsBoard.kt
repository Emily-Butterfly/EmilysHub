package de.emilyshub.ui.warum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.ui.MoreMenu
import de.emilyshub.ui.MoreMenuItem
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.warum.Reason
import de.emilyshub.warum.Reasons

/**
 * The board of Mein Warum (SPEC.md §18): the Gründe in two columns, each tile as high as what it holds, in the order the
 * user laid them. Looked at ([editing] false), it has no buttons; a reason tapped opens large ([onOpen]). Edited, a
 * reason tapped is changed ([onEdit]), "+" adds one ([onAdd]), a long press drags one to another place, and its menu
 * moves it by one, also for TalkBack, or deletes it after asking. Empty, it says what it is for, with the way to the first.
 */
@Composable
fun ReasonsBoard(
    reasons: Reasons,
    editing: Boolean,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val board by remember(reasons) { reasons.board() }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val saved = board ?: return
    if (saved.isEmpty()) {
        Empty(contentPadding, onAdd)
        return
    }
    // The order as dragged, until the board saved follows it.
    var dragged by remember { mutableStateOf<List<Reason>?>(null) }
    LaunchedEffect(saved) { dragged = null }
    val shown = dragged ?: saved
    val grid = rememberLazyStaggeredGridState()
    val drag = remember(grid) { BoardDrag(grid) }
    val current by rememberUpdatedState(shown)

    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = grid,
            contentPadding = (if (editing) contentPadding.plusFabSpace() else contentPadding).plus(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.id }) { reason ->
                val moving = drag.id == reason.id
                val dragging = if (editing) {
                    Modifier.pointerInput(reason.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { drag.start(reason.id) },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.by(amount, current)?.let { dragged = it }
                            },
                            onDragEnd = {
                                val index = current.indexOfFirst { it.id == reason.id }
                                drag.stop()
                                if (dragged != null) scope.launchNow { reasons.move(reason.id, index) }
                            },
                            onDragCancel = {
                                drag.stop()
                                dragged = null
                            },
                        )
                    }
                } else {
                    Modifier
                }
                ReasonTile(
                    reason = reason,
                    editing = editing,
                    onClick = { if (editing) onEdit(reason.id) else onOpen(reason.id) },
                    onForward = { scope.launchNow { reasons.forward(reason.id) } },
                    onBack = { scope.launchNow { reasons.back(reason.id) } },
                    onDelete = { deleting = reason.id },
                    modifier = Modifier
                        .then(if (moving) Modifier.zIndex(1f) else Modifier.animateItem())
                        .graphicsLayer {
                            if (moving) {
                                translationX = drag.offset.x
                                translationY = drag.offset.y
                            }
                        }
                        .then(dragging),
                )
            }
        }
        if (editing) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(contentPadding)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.warum_add))
            }
        }
    }

    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.warum_delete_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launchNow { reasons.delete(id) }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** An empty board: what it is for, and the way to its first reason. */
@Composable
private fun Empty(contentPadding: PaddingValues, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.warum_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAdd) { Text(stringResource(R.string.warum_add_first)) }
    }
}

/** A reason on the board, as high as its text; edited, with its menu in the corner. */
@Composable
private fun ReasonTile(
    reason: Reason,
    editing: Boolean,
    onClick: () -> Unit,
    onForward: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth()) {
            ReasonText(
                reason.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (editing) 40.dp else 16.dp, vertical = 20.dp),
            )
            if (editing) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.align(Alignment.TopEnd)) {
                    MoreMenu(nameOf(reason), expanded = open, onExpandedChange = { open = it }) { close ->
                        MoreMenuItem(stringResource(R.string.warum_change), close, onClick)
                        MoreMenuItem(stringResource(R.string.warum_forward), close, onForward)
                        MoreMenuItem(stringResource(R.string.warum_back), close, onBack)
                        MoreMenuItem(stringResource(R.string.delete), close, onDelete)
                    }
                }
            }
        }
    }
}

/** A reason's text as it stands, in the middle, right to left if it begins so (SPEC.md §18). */
@Composable
internal fun ReasonText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val direction = if (InputText.startsRightToLeft(text)) TextDirection.Rtl else TextDirection.Ltr
    Text(text, style = style.copy(textAlign = TextAlign.Center, textDirection = direction), modifier = modifier)
}

/** What a reason's menu is named by: its first line. */
private fun nameOf(reason: Reason) = reason.text.lineSequence().first()

/**
 * A reason dragged across the board by a long press: [id] while it is, [offset] from where the grid lays it out. Over
 * another reason, it takes that one's place in the order, and the offset follows so that it stays under the finger.
 */
private class BoardDrag(private val grid: LazyStaggeredGridState) {
    var id by mutableStateOf<Long?>(null)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun start(id: Long) {
        this.id = id
        offset = Offset.Zero
    }

    /** Moves it by [amount]; the order of [shown] it makes, if it changes. */
    fun by(amount: Offset, shown: List<Reason>): List<Reason>? {
        val moving = id ?: return null
        offset += amount
        val items = grid.layoutInfo.visibleItemsInfo
        val own = items.find { it.key == moving } ?: return null
        val center = own.offset.toOffset() + offset + Offset(own.size.width / 2f, own.size.height / 2f)
        val over = items.find { item ->
            item.key != moving &&
                center.x in item.offset.x.toFloat()..(item.offset.x + item.size.width).toFloat() &&
                center.y in item.offset.y.toFloat()..(item.offset.y + item.size.height).toFloat()
        } ?: return null
        val from = shown.indexOfFirst { it.id == moving }
        val to = shown.indexOfFirst { it.id == over.key }
        if (from < 0 || to < 0) return null
        // Laid out where the other was, it is moved back by as much, to stay where the finger is.
        offset += own.offset.toOffset() - over.offset.toOffset()
        return shown.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun stop() {
        id = null
        offset = Offset.Zero
    }

    private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())
}

/** These paddings with [extra] all round. */
private fun PaddingValues.plus(extra: Dp): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding() = this@plus.calculateTopPadding() + extra
    override fun calculateBottomPadding() = this@plus.calculateBottomPadding() + extra
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        this@plus.calculateLeftPadding(layoutDirection) + extra
    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        this@plus.calculateRightPadding(layoutDirection) + extra
}
