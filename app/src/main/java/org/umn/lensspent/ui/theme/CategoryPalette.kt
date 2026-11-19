package org.umn.lensspent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The nine category colours.
 *
 * These started as Material swatches (#4CAF50, #FF7043, #AB47BC) and were far too loud: a category
 * colour appears on every row, every tile and every breakdown bar, so they ended up outshouting the
 * rest of the palette. Each hue is now pulled down to roughly the same chroma and lightness, and
 * spread around the wheel so groceries, dining and transport don't look alike at a glance.
 *
 * No red in the set. Red means error here, and a category that looks like a warning reads as one.
 *
 * The value is stored on the category row as a hex string, so this is a seed and not a binding: a
 * category the user adds keeps whatever colour it was given.
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
