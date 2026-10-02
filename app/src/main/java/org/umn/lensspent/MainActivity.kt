package org.umn.lensspent

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.umn.lensspent.data.local.ThemePreference
import org.umn.lensspent.data.local.UserPreferences
import org.umn.lensspent.ui.LensSpentNavHost
import org.umn.lensspent.ui.theme.LensSpentTheme

/**
 * The root of the app: it reads the stored settings, themes the window with them, and decides whether
 * this launch starts at onboarding or at the dashboard.
 *
 * The whole navigation graph hangs off here, but the decisions do not: they are made once, against the
 * stored values, and handed down to [LensSpentNavHost].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The platform defaults, for the frames before the first composition: the stored theme has not
        // been read yet, so anything more specific here would be a guess - and a guess is the flash the
        // effect in setContent exists to prevent.
        enableEdgeToEdge()

        val preferences = (application as LensSpentApplication).container.userPreferencesRepository

        setContent {
            // Null until the first read lands. The snapshot flow is collected rather than the individual
            // keys because a key can only answer with its default, and a default is indistinguishable
            // from a stored default - which is exactly the question this screen has to answer.
            val stored by preferences.preferences.collectAsStateWithLifecycle(initialValue = null)

            val darkTheme = themeIsDark(
                preference = stored?.themePreference ?: ThemePreference.Default,
                systemInDarkTheme = isSystemInDarkTheme()
            )



            LensSpentTheme(
                darkTheme = darkTheme,
                dynamicColor = false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val snapshot = stored ?: UserPreferences()
                    LensSpentNavHost(
                        hasCompletedOnboarding = snapshot.hasCompletedOnboarding,
                        username = snapshot.username
                    )
                }
            }
        }
    }
}

/**
 * Turns the stored preference into the boolean the theme wants.
 *
 * The phone's own setting is passed in rather than read here, which keeps this function pure: the one
 * input that does not come from DataStore arrives as an argument, so the mapping can be checked
 * without a device.
 */
internal fun themeIsDark(preference: ThemePreference, systemInDarkTheme: Boolean): Boolean =
    when (preference) {
        ThemePreference.SYSTEM -> systemInDarkTheme
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

/**
 * A transparent system bar whose icons contrast with [darkTheme].
 *
 * `SystemBarStyle.auto` decides icon colour from its `detectDarkMode` argument, and the default for that
 * asks the *resources* - which is the phone's own night mode. That is the wrong question here: once the
 * user picks Light or Dark in Settings, the app draws a theme the phone knows nothing about, so an app
 * forced dark on a phone left in light mode kept black icons over the dark background it had just
 * painted behind them. Answering with the theme actually on screen is the whole of the fix, and it is
 * why the caller has to reapply this whenever the theme changes rather than leave it to `onCreate`.
 *
 * Transparent rather than tinted: the app draws its own background edge to edge, so a scrim would only
 * be a second, slightly different shade of it sitting behind the bars.
 */
private fun systemBarStyle(darkTheme: Boolean): SystemBarStyle =
    SystemBarStyle.auto(
        lightScrim = Color.TRANSPARENT,
        darkScrim = Color.TRANSPARENT,
        detectDarkMode = { darkTheme }
    )