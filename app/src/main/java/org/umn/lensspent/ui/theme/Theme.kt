package org.umn.lensspent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import android.os.Build

/**
 * Whether the app is currently drawing its dark scheme.
 *
 * Category colours are data, not tokens, but they still have to be lifted for a dark surface. Reading
 * the *system* setting for that was wrong whenever the user forced Light or Dark in Settings: the app
 * would paint a light page and brighten its category tints as if it were night, or the reverse. This
 * local answers the question the screen actually asks - which scheme is on screen - so every category
 * badge follows the same decision as the page behind it.
 */
val LocalLensDarkTheme = staticCompositionLocalOf { false }

/**
 * The app's theme: LensSpent's own colours, type scale and shapes, applied once here.
 *
 * All three systems are supplied. Passing only `colorScheme` leaves `MaterialTheme.shapes` and
 * `MaterialTheme.typography` at Material's defaults, so every screen that asked the theme for a shape
 * silently got different corners from its neighbour.
 *
 * `dynamicColor` is off by default, and off for a reason. Material You replaces the whole colour scheme
 * with the wallpaper's, which would erase the palette the app is branded with and leave the olive
 * "budget" colour meaning nothing. It stays a parameter so a developer can preview the app under the
 * device palette, not so it can be turned on in a build.
 *
 * [darkTheme] is a boolean rather than "read the system" because the user can force Light or Dark in
 * Settings; the decision is made once in `MainActivity` and handed in, so the theme never has to guess.
 */
@Composable
fun LensSpentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> LensSpentDarkColors
        else -> LensSpentLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LensSpentTypography,
        shapes = LensSpentShapes,
        content = {
            CompositionLocalProvider(
                LocalLensDarkTheme provides darkTheme,
                content = content
            )
        }
    )
}
