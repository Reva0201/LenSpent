package org.umn.lensspent.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * LensSpent's palette: warm paper, olive ink, and one ochre accent.
 *
 * The previous palette was a blue-led finance palette: deep ocean ink, teal for money left, cool
 * blue-grey neutrals. It read clearly, but it also read like every other banking app, and the blue
 * gradient card made the dashboard's most important number look like a marketing panel rather than a
 * ledger total.
 *
 * This palette is drawn from the material the app is about: paper receipts, a pencil ledger, and the
 * green ink a shopkeeper uses to mark a total. The page is warm off-white rather than blue-white, the
 * ink is a dark olive rather than navy, and the one saturated colour is a deep olive used for actions
 * and budget progress. Ochre is the supporting accent and is kept to small marks: a category icon, a
 * selected state, a single figure that needs to be noticed.
 *
 * The rules are the same as before, because they are what keep this a system rather than a bag of hex
 * codes. Every role is named for what it means in this app, each role exists in both schemes, and the
 * raw colours are private so a screen cannot reach past the scheme. What a screen may use is
 * `MaterialTheme.colorScheme`.
 */

// Paper and ink, light scheme.
private val Paper = Color(0xFFF7F5EF)
private val PaperRaised = Color(0xFFFFFFFF)
private val PaperSunk = Color(0xFFEAE7DD)
private val PaperRaisedHigh = Color(0xFFE2DFD3)
private val PaperHighest = Color(0xFFDAD6C8)
private val Ink = Color(0xFF202820)
private val InkMuted = Color(0xFF515B50)
private val Rule = Color(0xFFD5D5C7)
private val RuleStrong = Color(0xFF727A69)

// Olive: actions, budget progress, the app's one strong colour.
private val Olive800 = Color(0xFF425832)
private val Olive900 = Color(0xFF2C3D20)
private val Olive100 = Color(0xFFDCE9CF)
private val Olive50 = Color(0xFFEDF3E5)

// Ochre: a supporting accent, never body text or a large fill.
private val Ochre800 = Color(0xFF866019)
private val Ochre100 = Color(0xFFF5E3BB)

// Warm charcoal for the dark scheme. Not blue-black: against a phone's own bezel a blue-black page
// reads as a hole, and the warm cast keeps receipts and paper imagery feeling like the same app.
private val Night = Color(0xFF141813)
private val NightRaised = Color(0xFF20271D)
private val NightSunk = Color(0xFF2A3226)
private val NightHigh = Color(0xFF323A2D)
private val NightHighest = Color(0xFF3B4435)
private val NightInk = Color(0xFFEDF1E8)
private val NightInkMuted = Color(0xFFBFC7B8)
private val NightRule = Color(0xFF4B5545)

private val Red600 = Color(0xFFB3261E)
private val Red300 = Color(0xFFFFB4AB)
private val Red900 = Color(0xFF410E0B)
private val Red950 = Color(0xFF690005)

internal val LensSpentLightColors = lightColorScheme(
    primary = Olive800,
    onPrimary = Color.White,
    primaryContainer = Olive100,
    onPrimaryContainer = Color(0xFF17210F),
    inversePrimary = Color(0xFFB9D39D),
    secondary = Color(0xFF5C6652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E8D8),
    onSecondaryContainer = Color(0xFF1A2115),
    tertiary = Ochre800,
    onTertiary = Color.White,
    tertiaryContainer = Ochre100,
    onTertiaryContainer = Color(0xFF2C1F00),
    error = Red600,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Red900,
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E6DC),
    onSurfaceVariant = InkMuted,
    surfaceTint = Olive800,
    inverseSurface = Color(0xFF263022),
    inverseOnSurface = Color(0xFFF1F4EA),
    outline = RuleStrong,
    outlineVariant = Rule,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE4E1D7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0EEE6),
    surfaceContainer = PaperSunk,
    surfaceContainerHigh = PaperRaisedHigh,
    surfaceContainerHighest = PaperHighest
)

internal val LensSpentDarkColors = darkColorScheme(
    primary = Color(0xFFB9D39D),
    onPrimary = Color(0xFF233313),
    primaryContainer = Color(0xFF35482A),
    onPrimaryContainer = Color(0xFFD7EDC0),
    inversePrimary = Olive800,
    secondary = Color(0xFFC4CCB9),
    onSecondary = Color(0xFF2E3628),
    secondaryContainer = Color(0xFF444D3C),
    onSecondaryContainer = Color(0xFFE0E8D8),
    tertiary = Color(0xFFE6C06A),
    onTertiary = Color(0xFF442F00),
    tertiaryContainer = Color(0xFF624700),
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = Red300,
    onError = Red950,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Night,
    onBackground = NightInk,
    surface = NightRaised,
    onSurface = NightInk,
    surfaceVariant = NightSunk,
    onSurfaceVariant = NightInkMuted,
    surfaceTint = Color(0xFFB9D39D),
    inverseSurface = NightInk,
    inverseOnSurface = Color(0xFF263022),
    outline = Color(0xFF8E9885),
    outlineVariant = NightRule,
    scrim = Color.Black,
    surfaceBright = NightHigh,
    surfaceDim = Color(0xFF0E120E),
    surfaceContainerLowest = Color(0xFF0F130F),
    surfaceContainerLow = Color(0xFF1A2018),
    surfaceContainer = NightSunk,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest
)
