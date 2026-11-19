package org.umn.lensspent.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The app's motion scale: four durations and three curves, named once.
 *
 * Before this file every animation carried its own number - a 180ms tab colour, a 280ms pill, a
 * 600ms share bar with an 80ms delay, a 700ms counter, a 160/220ms screen fade. That is how an app
 * ends up feeling like several apps: the eye reads inconsistent easing as jitter even when each
 * individual animation is fine.
 *
 * The scale is deliberately short. This is a ledger, not a game: motion exists to show *where* a
 * change came from and to keep the eye oriented while content settles. Anything longer than about a
 * third of a second starts to feel like waiting, and anything that overshoots makes a number look
 * unstable.
 *
 * [rememberAnimationsEnabled] is the accessibility switch. When the system animator scale is zero,
 * callers pass `enabled = false` and every spec collapses to zero milliseconds rather than the
 * screen being frozen mid-animation.
 */
object Motion {

    /** Pressed states and colour swaps. Barely perceptible, which is the point. */
    const val Quick = 120

    /** Tabs, selection pills, the pager. Movement the eye should follow, not wait for. */
    const val Standard = 220

    /** Content settling into place: bars, progress, share meters. */
    const val Emphasized = 320

    /** A single large figure counting to its value. The one motion allowed to take its time. */
    const val Deliberate = 520

    /** Standard material curve: accelerates gently, decelerates firmly. */
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For something arriving on screen: starts moving immediately, then eases out. */
    val Decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** For something leaving: keeps moving as it fades, so it does not appear to stop first. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/**
 * Whether the system allows animation.
 *
 * Read once per composition rather than per frame: the setting changes a handful of times in a
 * device's life, and re-reading it inside a draw loop would be wasted work.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }

/**
 * A tween on the app's scale that respects the platform's "remove animations" setting.
 *
 * Generic over the animated type so the same call serves `Float`, `Dp` and `Color`; the default
 * easing is [Motion.StandardEasing] because that is correct for almost everything here.
 */
fun <T> motionTween(
    durationMillis: Int,
    enabled: Boolean = true,
    easing: Easing = Motion.StandardEasing
): TweenSpec<T> = tween(
    durationMillis = if (enabled) durationMillis else 0,
    easing = easing
)
