package de.emilyshub.ui.warum

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.emilyshub.R
import de.emilyshub.warum.Hue
import de.emilyshub.warum.ReasonColor
import de.emilyshub.warum.ReasonFont
import de.emilyshub.warum.ReasonLook
import de.emilyshub.warum.ReasonSize
import de.emilyshub.warum.StandardColors

/** The font family [font] is written in: the app's own for Standard, else the one brought along for it. */
internal fun familyOf(font: ReasonFont): FontFamily = when (font) {
    ReasonFont.Standard -> FontFamily.Default
    ReasonFont.Serif -> FontFamily(Font(R.font.warum_serif))
    ReasonFont.Round -> FontFamily(Font(R.font.warum_round))
    ReasonFont.Hand -> FontFamily(Font(R.font.warum_hand))
    ReasonFont.Script -> FontFamily(Font(R.font.warum_script))
    ReasonFont.Poster -> FontFamily(Font(R.font.warum_poster))
    ReasonFont.Elegant -> FontFamily(Font(R.font.warum_elegant))
    ReasonFont.Typewriter -> FontFamily(Font(R.font.warum_typewriter))
}

/** Where a Grund is shown, which sets how large its sizes are. */
internal enum class ReasonPlace(val small: TextUnit, val medium: TextUnit, val large: TextUnit) {
    /** On the board, and in the editor's preview. */
    Board(14.sp, 18.sp, 24.sp),

    /** Opened large. */
    Large(24.sp, 32.sp, 44.sp),

    /** As the strip below a photo opened large. */
    Strip(16.sp, 20.sp, 26.sp),
}

/**
 * The text style of [look] at [place], in the colour of its text: its font, as large as its size is there. Characters
 * the font lacks, as Arabic ones, Android shows in a fitting font of the phone's own.
 */
@Composable
internal fun styleOf(look: ReasonLook, place: ReasonPlace): TextStyle {
    val size = when (look.size) {
        ReasonSize.Small -> place.small
        ReasonSize.Medium -> place.medium
        ReasonSize.Large -> place.large
    }
    return MaterialTheme.typography.bodyLarge.copy(
        fontFamily = familyOf(look.font),
        fontSize = size,
        lineHeight = size * LINE_HEIGHT,
        color = Color(look.textIn(standardColors())),
    )
}

/** The background of [look] in the design the app shows. */
@Composable
internal fun backgroundOf(look: ReasonLook): Color = Color(look.backgroundIn(standardColors()))

/** What "Standard" is in the design the app shows: its text on the tiles' colour. */
@Composable
internal fun standardColors() = StandardColors(
    text = MaterialTheme.colorScheme.onSurface.toArgb(),
    background = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
)

private const val LINE_HEIGHT = 1.3f

@StringRes
internal fun nameOf(font: ReasonFont): Int = when (font) {
    ReasonFont.Standard -> R.string.font_standard
    ReasonFont.Serif -> R.string.font_serif
    ReasonFont.Round -> R.string.font_round
    ReasonFont.Hand -> R.string.font_hand
    ReasonFont.Script -> R.string.font_script
    ReasonFont.Poster -> R.string.font_poster
    ReasonFont.Elegant -> R.string.font_elegant
    ReasonFont.Typewriter -> R.string.font_typewriter
}

@StringRes
internal fun nameOf(size: ReasonSize): Int = when (size) {
    ReasonSize.Small -> R.string.size_small
    ReasonSize.Medium -> R.string.size_medium
    ReasonSize.Large -> R.string.size_large
}

/** A colour's name as TalkBack says it: "Blau, kräftig", "Sand", "Standard". */
@Composable
internal fun nameOf(color: ReasonColor): String {
    val hue = color.hue
    if (hue != null) {
        val step = stringResource(listOf(R.string.step_1, R.string.step_2, R.string.step_3, R.string.step_4)[color.step - 1])
        return stringResource(R.string.color_hue_step, stringResource(hueName(hue)), step)
    }
    return stringResource(
        when (color) {
            ReasonColor.White -> R.string.color_white
            ReasonColor.Cream -> R.string.color_cream
            ReasonColor.Sand -> R.string.color_sand
            ReasonColor.LightGrey -> R.string.color_light_grey
            ReasonColor.Grey -> R.string.color_grey
            ReasonColor.DarkGrey -> R.string.color_dark_grey
            ReasonColor.Black -> R.string.color_black
            else -> R.string.color_standard
        },
    )
}

@StringRes
private fun hueName(hue: Hue): Int = when (hue) {
    Hue.Red -> R.string.hue_red
    Hue.Orange -> R.string.hue_orange
    Hue.Yellow -> R.string.hue_yellow
    Hue.Green -> R.string.hue_green
    Hue.Turquoise -> R.string.hue_turquoise
    Hue.Blue -> R.string.hue_blue
    Hue.Purple -> R.string.hue_purple
}

/** The eight fonts to choose from, each named in itself. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FontChoice(selected: ReasonFont, onChange: (ReasonFont) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReasonFont.entries.forEach { font ->
            FilterChip(
                selected = font == selected,
                onClick = { onChange(font) },
                label = { Text(stringResource(nameOf(font)), fontFamily = familyOf(font)) },
            )
        }
    }
}

/** Small, medium or large. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SizeChoice(selected: ReasonSize, onChange: (ReasonSize) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReasonSize.entries.forEach { size ->
            FilterChip(selected = size == selected, onClick = { onChange(size) }, label = { Text(stringResource(nameOf(size))) })
        }
    }
}

/**
 * The palette for what [label] names, the background or the text: "Standard" first, then the hues from soft to strong,
 * then the neutrals; each a round swatch, the chosen one ticked, named to TalkBack as "<label>: <colour>".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorChoice(label: String, selected: ReasonColor, standard: Color, onChange: (ReasonColor) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReasonColor.entries.forEach { color ->
                val shown = color.argb?.let(::Color) ?: standard
                val name = stringResource(R.string.reason_color_choice, label, nameOf(color))
                val ticked = color == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(SWATCH.dp)
                        .clip(CircleShape)
                        .background(shown)
                        .border(
                            if (ticked) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            CircleShape,
                        )
                        .selectable(selected = ticked, role = Role.RadioButton, onClick = { onChange(color) })
                        .semantics { contentDescription = name },
                ) {
                    if (color == ReasonColor.Standard) {
                        Text("A", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else if (ticked) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = if (shown.luminance() > 0.5f) Color.Black else Color.White)
                    }
                }
            }
        }
    }
}

private const val SWATCH = 36
