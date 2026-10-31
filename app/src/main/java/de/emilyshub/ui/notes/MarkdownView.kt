package de.emilyshub.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.emilyshub.notes.Block
import de.emilyshub.notes.Inline
import de.emilyshub.notes.ListItem

/**
 * The [blocks] of a note's Markdown, one below the other, as text only (SPEC.md §7, §9): nothing in them can be tapped,
 * and a link shows only its words. A task shows whether it is ticked off, which is changed in the text, by its "[x]".
 */
fun LazyListScope.markdownBlocks(blocks: List<Block>) {
    items(blocks) { block ->
        // A block written right to left, as in Arabic, puts its bullet, box or quote bar on the right.
        val direction = if (startsRightToLeft(block)) LayoutDirection.Rtl else LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides direction) { MarkdownBlock(block) }
    }
}

/** Whether the first letter of [block] with a direction of its own, as Arabic or Hebrew letters have, goes right to left. */
internal fun startsRightToLeft(block: Block): Boolean {
    val text = when (block) {
        is Block.Heading -> plainText(block.text)
        is Block.Paragraph -> plainText(block.text)
        is Block.Quote -> plainText(block.text)
        is Block.Bullets -> block.items.joinToString(" ") { plainText(it.text) }
        is Block.Numbered -> block.items.joinToString(" ") { plainText(it.text) }
        // Code reads left to right whatever it holds.
        is Block.Code, Block.Rule -> ""
    }
    return text.firstNotNullOfOrNull { char ->
        when (Character.getDirectionality(char)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> false
            else -> null
        }
    } ?: false
}

private fun plainText(parts: List<Inline>): String = parts.joinToString("") { part ->
    when (part) {
        is Inline.Text -> part.text
        is Inline.Code -> part.text
        is Inline.Bold -> plainText(part.content)
        is Inline.Italic -> plainText(part.content)
        is Inline.Link -> plainText(part.content)
    }
}

@Composable
private fun MarkdownBlock(block: Block) {
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is Block.Heading -> Text(
            text = annotated(block.text),
            style = headingStyle(block.level),
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { heading() },
        )
        is Block.Paragraph -> Text(annotated(block.text), style = body)
        is Block.Bullets -> Column { block.items.forEach { ListLine(marker = BULLET, it) } }
        is Block.Numbered -> Column {
            block.items.forEachIndexed { index, item -> ListLine(marker = "${block.start + index}.", item) }
        }
        is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = annotated(block.text),
                style = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is Block.Code -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // As written, line by line: a long line scrolls sideways.
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
        Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
    }
}

/** An item of a list after its [marker], or, a task, after whether it is ticked off, read out as such. */
@Composable
private fun ListLine(marker: String, item: ListItem) {
    val checked = item.checked
    Row(
        modifier = if (checked == null) {
            Modifier
        } else {
            Modifier.semantics(mergeDescendants = true) { toggleableState = ToggleableState(checked) }
        },
        verticalAlignment = if (checked == null) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (checked == null) {
            Text(marker, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
        } else {
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        }
        Text(annotated(item.text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = with(MaterialTheme.typography) {
    when (level) {
        1 -> headlineSmall
        2 -> titleLarge
        3 -> titleMedium
        else -> titleSmall
    }
}

@Composable
private fun annotated(parts: List<Inline>): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString { appendAll(parts, codeBackground) }
}

private fun AnnotatedString.Builder.appendAll(parts: List<Inline>, codeBackground: Color) {
    parts.forEach { part ->
        when (part) {
            is Inline.Text -> append(part.text)
            is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendAll(part.content, codeBackground) }
            is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendAll(part.content, codeBackground) }
            is Inline.Code ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(part.text) }
            // Its words only, looking like the text around them, as there is nothing to open.
            is Inline.Link -> appendAll(part.content, codeBackground)
        }
    }
}

private const val BULLET = "•"
