package org.umn.lensspent.data.remote

/**
 * Why the optional AI feature cannot run a request right now.
 *
 * Each value maps to one sentence the chat shows, so "AI is unavailable" is never a dead end: the
 * user is told whether the build has no backend, whether they switched the feature off, or whether
 * they have not agreed to the disclosure yet.
 */
enum class AiUnavailableReason {
    /** This build was compiled without an AI proxy URL, so there is nowhere safe to send anything. */
    NotConfigured,

    /** The user turned AI network access off in Settings. */
    DisabledByUser,

    /** The user has not yet accepted the in-app disclosure. */
    ConsentRequired
}

/**
 * Whether one AI request may leave the phone.
 *
 * Three independent conditions, all of which must hold:
 *  1. a secure backend was configured at build time ([configured]),
 *  2. the user has not switched AI networking off ([enabled]),
 *  3. the user explicitly agreed to the disclosure ([consentGranted]).
 *
 * Defaults are the privacy-preserving ones: nothing is configured, nothing is consented, so a fresh
 * install cannot transmit spending by accident.
 */
data class AiAccess(
    val configured: Boolean = false,
    val enabled: Boolean = false,
    val consentGranted: Boolean = false
) {

    /** True only when every gate above is open. */
    val isAllowed: Boolean get() = configured && enabled && consentGranted

    /** The first gate that is closed, or null when [isAllowed]. */
    val unavailableReason: AiUnavailableReason?
        get() = when {
            !configured -> AiUnavailableReason.NotConfigured
            !enabled -> AiUnavailableReason.DisabledByUser
            !consentGranted -> AiUnavailableReason.ConsentRequired
            else -> null
        }
}

/**
 * The text the chat shows when a request is refused locally.
 *
 * Written once, next to the gate it describes, so the wording cannot drift from the rule that
 * produced it. These constants are asserted by tests.
 */
object AiAvailabilityMessages {

    const val NotConfigured: String =
        "AI Insights is off in this build. It needs a secure backend configured by the app " +
            "developer, and no API key is ever stored in the app itself."

    const val DisabledByUser: String =
        "AI Insights is turned off in Settings. Nothing about your spending is being sent " +
            "anywhere, and the rest of the app keeps working offline."

    const val ConsentRequired: String =
        "Before AI Insights can answer, you need to review and accept what it sends."

    fun forReason(reason: AiUnavailableReason): String = when (reason) {
        AiUnavailableReason.NotConfigured -> NotConfigured
        AiUnavailableReason.DisabledByUser -> DisabledByUser
        AiUnavailableReason.ConsentRequired -> ConsentRequired
    }
}
