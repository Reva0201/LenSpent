package org.umn.lensspent.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * LensSpent's palette: deep ocean ink carrying the app, a single teal accent for what the user should
 * act on, and cool neutrals for everything that is merely information.
 *
 * Two rules keep this a system rather than a bag of hex codes. First, every role is named for what it
 * means in this app - `primary` is the ink of a headline and the fill of the main button, `tertiary` is
 * the one colour allowed to mean "money you have left". Second, each role exists in both schemes and
 * is picked in pairs, so nothing ever needs a hand-tuned alpha to stay readable in dark mode.
 *
 * The raw colours are private: a screen that reaches past the colour scheme is the drift this file
 * exists to prevent. What a screen may use is `MaterialTheme.colorScheme`, which is what makes a
 * theme switch a single line rather than an audit.
 */
private val Ink900 = Color(0xFF05121A)
private val Ink800 = Color(0xFF0B1418)
private val Ink700 = Color(0xFF0D5C8A)
private val Ink600 = Color(0xFF0A4A70)
private val Ink200 = Color(0xFF8CCBF0)
private val Ink100 = Color(0xFFCBE8FB)
private val Ink50 = Color(0xFFD3E8F7)

private val Teal700 = Color(0xFF0A8A7E)
private val Teal600 = Color(0xFF00776C)
private val Teal200 = Color(0xFF5FD8CB)
private val Teal50 = Color(0xFFC7F0E9)

private val Slate800 = Color(0xFF3E5B6E)
private val Slate300 = Color(0xFFB4C9D6)
private val Slate100 = Color(0xFFDBE6ED)
private val Slate900 = Color(0xFF0C1A22)

private val Paper = Color(0xFFF5F8FA)
private val PaperRaised = Color(0xFFFFFFFF)
private val PaperSunk = Color(0xFFE9F0F4)

private val Night = Color(0xFF080E12)
private val NightRaised = Color(0xFF0D151A)
private val NightSunk = Color(0xFF151F26)

private val Red600 = Color(0xFFB3261E)
private val Red300 = Color(0xFFFFB4AB)
private val Red900 = Color(0xFF410E0B)
private val Red950 = Color(0xFF690005)

/**
 * The brand gradient's own colours, with the colour its content must be drawn in.
 *
 * This exists because a gradient cannot be assembled out of `colorScheme` slots and still work in both
 * modes. In the light scheme `primary` and `tertiary` are deep inks, so a primary-to-tertiary gradient
 * with white text reads correctly - but in the dark scheme those same slots are *light* tints, and the
 * identical gradient turns into a pastel block with white text on it, which is unreadable. Naming the
 * gradient as its own role lets each scheme choose a pair that its own content colour contrasts with,
 * instead of every screen inventing one and half of them getting dark mode wrong.
 */
data class HeroColors(
    val start: Color,
    val end: Color,
    /** Text and icons drawn directly on the gradient. */
    val content: Color,
    /** Secondary text on the gradient: labels, units, supporting lines. */
    val contentMuted: Color,
    /** The one accent allowed on the hero: the ring that shows budget used. */
    val accent: Color
)

/**
 * A rich brand-blue card on the light page.
 *
 * Two earlier attempts are worth recording, because both failed the same way. The first swept brand
 * blue into brand teal, and the hue crossing produced a green that belongs to neither. The second went
 * so dark that on a white page it read as black - a hole rather than a coloured surface - and its
 * diagonal made the amount sit on a noticeably lighter patch than the label above it.
 *
 * So: one family, clearly blue, and a vertical sweep with a small delta, which keeps the amount and the
 * footer on the same tone and lets the lightening be read as depth at the bottom edge.
 */
internal val LightHeroColors = HeroColors(
    start = Color(0xFF11496F),
    end = Color(0xFF0B3352),
    content = Color.White,
    contentMuted = Color.White.copy(alpha = 0.76f),
    accent = Color(0xFF5FD8CB)
)

/**
 * The same family in dark mode, lifted just above the background: the hero still has to be a different
 * surface from the page, so it is lighter than `background`, not darker.
 */
internal val DarkHeroColors = HeroColors(
    start = Color(0xFF12314A),
    end = Color(0xFF0E2333),
    content = Color(0xFFE6F1F7),
    contentMuted = Color(0xFFE6F1F7).copy(alpha = 0.72f),
    accent = Color(0xFF5FD8CB)
)

internal val LensSpentLightColors = lightColorScheme(
    primary = Ink700,
    onPrimary = Color.White,
    primaryContainer = Ink50,
    onPrimaryContainer = Color(0xFF04202E),
    inversePrimary = Ink200,
    secondary = Slate800,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = Teal700,
    onTertiary = Color.White,
    tertiaryContainer = Teal50,
    onTertiaryContainer = Color(0xFF002E29),
    error = Red600,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Red900,
    background = Paper,
    onBackground = Ink800,
    surface = PaperRaised,
    onSurface = Ink800,
    surfaceVariant = Color(0xFFE2EAEF),
    onSurfaceVariant = Color(0xFF40525C),
    surfaceTint = Ink700,
    inverseSurface = Color(0xFF21313A),
    inverseOnSurface = Color(0xFFECF3F6),
    outline = Color(0xFF6E7F89),
    outlineVariant = Color(0xFFC6D2D9),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDCE5EA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5F8),
    surfaceContainer = PaperSunk,
    surfaceContainerHigh = Color(0xFFE2EAEF),
    surfaceContainerHighest = Color(0xFFD8E2E8)
)

/**
 * Dark scheme: near-black rather than grey, so a raised card reads as a lit surface and the teal accent
 * keeps its punch. Every `on*` role is a light tint of its container, which is what keeps long-form
 * text readable without per-screen overrides.
 */
internal val LensSpentDarkColors = darkColorScheme(
    primary = Ink200,
    onPrimary = Color(0xFF00344D),
    primaryContainer = Ink600,
    onPrimaryContainer = Ink100,
    inversePrimary = Ink700,
    secondary = Slate300,
    onSecondary = Color(0xFF1E333F),
    secondaryContainer = Color(0xFF334A57),
    onSecondaryContainer = Color(0xFFD0E3EF),
    tertiary = Teal200,
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005049),
    onTertiaryContainer = Color(0xFFB8F0E9),
    error = Red300,
    onError = Red950,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Night,
    onBackground = Color(0xFFE3EAEF),
    surface = NightRaised,
    onSurface = Color(0xFFE3EAEF),
    surfaceVariant = Color(0xFF24333C),
    onSurfaceVariant = Color(0xFFB9C7D0),
    surfaceTint = Ink200,
    inverseSurface = Color(0xFFE3EAEF),
    inverseOnSurface = Color(0xFF26343C),
    outline = Color(0xFF83949E),
    outlineVariant = Color(0xFF33434C),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A3A44),
    surfaceDim = Night,
    surfaceContainerLowest = Ink900,
    surfaceContainerLow = Color(0xFF111A20),
    surfaceContainer = NightSunk,
    surfaceContainerHigh = Color(0xFF1B262E),
    surfaceContainerHighest = Color(0xFF22303A)
)
