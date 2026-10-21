package org.umn.lensspent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The nine category colours, as one designed set.
 *
 * These are not the Material swatches the taxonomy first shipped with. Those were bright and unevenly
 * weighted - `#4CAF50` and `#FF7043` sit at very different perceived lightness - which made the
 * transaction list, the category tiles and the breakdown bars the loudest thing on a page whose palette
 * is otherwise muted ink and teal. A category colour appears on every row, so it has to belong to the
 * brand rather than sit on top of it.
 *
 * What unifies them: every hue is desaturated to roughly the same chroma, every one sits near 45%
 * lightness, and they are spaced around the wheel so neighbouring categories in the list - groceries,
 * dining, transport - never look like the same colour at a glance. Red is deliberately absent from the
 * main set: it is the error colour, and a category that looks like a warning is a category the user
 * reads as something going wrong.
 *
 * Stored on the category row as a hex string, like any user-defined category can be, so this list is a
 * seed rather than a hard binding: a category the user adds keeps whatever colour it is given.
 */
object CategoryPalette {
    val Groceries = Color(0xFF4E8C61)
    val Dining = Color(0xFFC2704F)
    val Transport = Color(0xFF4A7FA8)
    val Shopping = Color(0xFF8A5E9E)
    val Bills = Color(0xFFC08A3E)
    val Health = Color(0xFFB65C5C)
    val Entertainment = Color(0xFF3E8C8C)
    val Education = Color(0xFF6B6BA8)
    val Other = Color(0xFF6E7F8C)

    /**
     * The same colour adjusted for the scheme it is drawn on.
     *
     * A tint that reads as a considered mid-tone on white paper reads as a dark smudge on near-black, so
     * in dark mode each colour is lifted toward white by a fixed amount. Doing it here rather than at
     * each call site means a badge, a tile and a bar of the same category cannot end up three different
     * shades of it.
     */
    fun forScheme(color: Color, dark: Boolean): Color =
        if (dark) {
            lerp(color, Color.White, 0.28f)
        } else {
            color
        }

    /** Straight-line mix, in the sRGB space the hex values are written in. */
    private fun lerp(from: Color, to: Color, fraction: Float): Color = Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
        alpha = 1f
    )
}
