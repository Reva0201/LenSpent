package org.umn.lensspent.data.remote

import java.net.URI
import org.umn.lensspent.BuildConfig

/**
 * The single place that answers "where may AI traffic go, if anywhere".
 *
 * The app **never** carries a third-party provider credential. An APK is not a secret store: a key
 * compiled into `BuildConfig`, a resource or a Gradle property can be read out of the artifact with
 * `strings`, and rotating it would require shipping a new build to every user. Instead this build may
 * be pointed at a proxy or backend owned by the app developer, which holds the provider key, applies
 * its own rate limits and can authenticate the app. When no such backend is configured the feature is
 * unavailable and no request is ever made.
 *
 * The URL is not a secret, so it is safe to keep in [BuildConfig]. It is still validated: only an
 * `https:` URL with a host is accepted, because an expense summary must never travel in clear text.
 */
object AiBackendConfig {

    /**
     * The configured proxy base URL, normalised to end in `/`, or an empty string when the build has
     * no backend. Retrofit requires the trailing slash to resolve relative endpoint paths.
     */
    val baseUrl: String = normalise(BuildConfig.AI_PROXY_BASE_URL)

    /** True only for a usable https backend URL. */
    val isConfigured: Boolean get() = baseUrl.isNotEmpty()

    /**
     * Validates and normalises [raw]. Exposed for tests; the production value is [baseUrl].
     *
     * Returns an empty string for anything that is not an absolute https URL with a host, which keeps
     * a typo in the build configuration from becoming an unintended plain-text destination.
     */
    fun normalise(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return ""
        if (!uri.scheme.equals("https", ignoreCase = true)) return ""
        if (uri.host.isNullOrBlank()) return ""

        return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
    }
}
