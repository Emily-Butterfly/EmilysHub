package de.emilyshub.warum

import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * One of the eight fonts a Grund's text is written in (SPEC.md §18), all brought along in the app under the SIL Open
 * Font License but [Standard], the app's own; kept by [stored].
 */
enum class ReasonFont(val stored: String) {
    Standard("standard"),
    Serif("serif"),
    Round("round"),

    /** Handwriting, loose. */
    Hand("hand"),

    /** Handwriting, calligraphic. */
    Script("script"),

    /** Bold, as on a poster. */
    Poster("poster"),

    /** Narrow and elegant. */
    Elegant("elegant"),
    Typewriter("typewriter"),
    ;

    companion object {
        /** The font kept as [stored]; [Standard] for one the app does not know. */
        fun of(stored: String?) = entries.find { it.stored == stored } ?: Standard
    }
}

/** How large a Grund's text is (SPEC.md §18); kept by [stored]. */
enum class ReasonSize(val stored: String) {
    Small("small"),
    Medium("medium"),
    Large("large"),
    ;

    companion object {
        /** The size kept as [stored]; [Medium] for one the app does not know. */
        fun of(stored: String?) = entries.find { it.stored == stored } ?: Medium
    }
}

/** The seven hues of the palette (SPEC.md §18), each in four steps from soft to strong. */
enum class Hue { Red, Orange, Yellow, Green, Turquoise, Blue, Purple }

/**
 * A colour of a Grund's background or text (SPEC.md §18): one of the palette, the same in the light and the dark
 * design, as [argb]; a [hue] in its [step] from 1 (soft) to 4 (strong), or a neutral without; or [Standard], which follows
 * the design of the app. Kept by [stored].
 */
enum class ReasonColor(val stored: String, val argb: Int?, val hue: Hue? = null, val step: Int = 0) {
    Standard("standard", null),

    Red1("red-1", 0xFFFFCDD2, Hue.Red, 1), Red2("red-2", 0xFFE57373, Hue.Red, 2),
    Red3("red-3", 0xFFF44336, Hue.Red, 3), Red4("red-4", 0xFFB71C1C, Hue.Red, 4),
    Orange1("orange-1", 0xFFFFE0B2, Hue.Orange, 1), Orange2("orange-2", 0xFFFFB74D, Hue.Orange, 2),
    Orange3("orange-3", 0xFFFF9800, Hue.Orange, 3), Orange4("orange-4", 0xFFE65100, Hue.Orange, 4),
    Yellow1("yellow-1", 0xFFFFF9C4, Hue.Yellow, 1), Yellow2("yellow-2", 0xFFFFF176, Hue.Yellow, 2),
    Yellow3("yellow-3", 0xFFFFEB3B, Hue.Yellow, 3), Yellow4("yellow-4", 0xFFF9A825, Hue.Yellow, 4),
    Green1("green-1", 0xFFC8E6C9, Hue.Green, 1), Green2("green-2", 0xFF81C784, Hue.Green, 2),
    Green3("green-3", 0xFF4CAF50, Hue.Green, 3), Green4("green-4", 0xFF1B5E20, Hue.Green, 4),
    Turquoise1("turquoise-1", 0xFFB2DFDB, Hue.Turquoise, 1), Turquoise2("turquoise-2", 0xFF4DB6AC, Hue.Turquoise, 2),
    Turquoise3("turquoise-3", 0xFF009688, Hue.Turquoise, 3), Turquoise4("turquoise-4", 0xFF004D40, Hue.Turquoise, 4),
    Blue1("blue-1", 0xFFBBDEFB, Hue.Blue, 1), Blue2("blue-2", 0xFF64B5F6, Hue.Blue, 2),
    Blue3("blue-3", 0xFF2196F3, Hue.Blue, 3), Blue4("blue-4", 0xFF0D47A1, Hue.Blue, 4),
    Purple1("purple-1", 0xFFE1BEE7, Hue.Purple, 1), Purple2("purple-2", 0xFFBA68C8, Hue.Purple, 2),
    Purple3("purple-3", 0xFF9C27B0, Hue.Purple, 3), Purple4("purple-4", 0xFF4A148C, Hue.Purple, 4),

    White("white", 0xFFFFFFFF), Cream("cream", 0xFFFFF8E1), Sand("sand", 0xFFE6D5B8),
    LightGrey("light-grey", 0xFFCFCFCF), Grey("grey", 0xFF8E8E8E), DarkGrey("dark-grey", 0xFF424242),
    Black("black", 0xFF000000),
    ;

    constructor(stored: String, argb: Long, hue: Hue? = null, step: Int = 0) : this(stored, argb.toInt(), hue, step)

    companion object {
        /** The colour kept as [stored]; [Standard] for one the app does not know. */
        fun of(stored: String?) = entries.find { it.stored == stored } ?: Standard
    }
}

/** What "Standard" is in the design the app shows: the colour of a Grund's [text] and its [background], as ARGB. */
data class StandardColors(val text: Int, val background: Int)

/**
 * How a Grund looks (SPEC.md §18): its [font] in its [size], its [text] on its [background]; with a photo, those of the
 * strip below it. Serializable, so that the editor keeps it.
 */
data class ReasonLook(
    val font: ReasonFont = ReasonFont.Standard,
    val size: ReasonSize = ReasonSize.Medium,
    val background: ReasonColor = ReasonColor.Standard,
    val text: ReasonColor = ReasonColor.Standard,
) : Serializable {

    /** Its background as it shows in a design whose own colours are [standard]. */
    fun backgroundIn(standard: StandardColors): Int = background.argb ?: standard.background

    /** Its text's colour as it shows in a design whose own colours are [standard]. */
    fun textIn(standard: StandardColors): Int = text.argb ?: standard.text

    /** Whether its text is hard to read on its background in a design whose own colours are [standard]. */
    fun hardToReadIn(standard: StandardColors): Boolean = Contrast.hardToRead(textIn(standard), backgroundIn(standard))

    companion object {
        /** The look kept as these values; what of them the app does not know counts as Standard. */
        fun of(font: String?, size: String?, background: String?, text: String?) =
            ReasonLook(ReasonFont.of(font), ReasonSize.of(size), ReasonColor.of(background), ReasonColor.of(text))
    }
}

/** The contrast of two colours as WCAG 2 has it, from 1 to 21. */
object Contrast {
    /** What large text needs at least to be read well (WCAG 2, AA for large text). */
    const val READABLE = 3.0

    /** The contrast of [a] and [b], ARGB, whichever is the lighter. */
    fun ratio(a: Int, b: Int): Double {
        val (la, lb) = luminance(a) to luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Whether [text] is hard to read on [background]: less than [READABLE]. */
    fun hardToRead(text: Int, background: Int) = ratio(text, background) < READABLE

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
